package de.hhu.bsinfo.net.ib;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.net.MessageHandlers;
import de.hhu.bsinfo.net.NetworkDestinationUnreachableException;
import de.hhu.bsinfo.net.NodeMap;
import de.hhu.bsinfo.net.core.AbstractConnection;
import de.hhu.bsinfo.net.core.AbstractConnectionManager;
import de.hhu.bsinfo.net.core.AbstractExporterPool;
import de.hhu.bsinfo.net.core.CoreConfig;
import de.hhu.bsinfo.net.core.DynamicExporterPool;
import de.hhu.bsinfo.net.core.MessageCreator;
import de.hhu.bsinfo.net.core.MessageDirectory;
import de.hhu.bsinfo.net.core.NetworkException;
import de.hhu.bsinfo.net.core.NetworkRuntimeException;
import de.hhu.bsinfo.net.core.RequestMap;
import de.hhu.bsinfo.net.core.StaticExporterPool;
import de.hhu.bsinfo.utils.ByteBufferHelper;
import de.hhu.bsinfo.utils.NodeID;

/**
 * Connection manager for infiniband (note: this is the main class for the IB subsystem in the java space)
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 13.06.2017
 */
public class IBConnectionManager extends AbstractConnectionManager
        implements JNIIbdxnet.SendHandler, JNIIbdxnet.RecvHandler, JNIIbdxnet.DiscoveryHandler, JNIIbdxnet.ConnectionHandler {
    private static final Logger LOGGER = LogManager.getFormatterLogger(IBConnectionManager.class.getSimpleName());

    private final CoreConfig m_coreConfig;
    private final IBConfig m_config;
    private final NodeMap m_nodeMap;

    private final MessageDirectory m_messageDirectory;
    private final RequestMap m_requestMap;
    private final MessageCreator m_messageCreator;
    private final MessageHandlers m_messageHandlers;

    private AbstractExporterPool m_exporterPool;

    private final IBWriteInterestManager m_writeInterestManager;

    private final boolean[] m_nodeConnected;

    //    struct NextWorkParameters
    //    {
    //        uint32_t m_posFrontRel;
    //        uint32_t m_posBackRel;
    //        uint32_t m_flowControlData;
    //        uint16_t m_nodeId;
    //    } __attribute__((packed));
    private final IBSendWorkParameterPool m_sendWorkParameterPool;

    /**
     * Constructor
     *
     * @param p_coreConfig
     *         Core configuration instance with core config values
     * @param p_config
     *         IB configuration instance with IB specific config values
     * @param p_nodeMap
     *         Node map instance
     * @param p_messageDirectory
     *         Message directory instance
     * @param p_requestMap
     *         Request map instance
     * @param p_messageCreator
     *         Message creator instance
     * @param p_messageHandlers
     *         Message handlers instance
     */
    public IBConnectionManager(final CoreConfig p_coreConfig, final IBConfig p_config, final NodeMap p_nodeMap, final MessageDirectory p_messageDirectory,
            final RequestMap p_requestMap, final MessageCreator p_messageCreator, final MessageHandlers p_messageHandlers) {
        super(p_config.getMaxConnections());

        m_coreConfig = p_coreConfig;
        m_config = p_config;
        m_nodeMap = p_nodeMap;

        m_messageDirectory = p_messageDirectory;
        m_requestMap = p_requestMap;
        m_messageCreator = p_messageCreator;
        m_messageHandlers = p_messageHandlers;

        if (p_coreConfig.getExporterPoolType()) {
            m_exporterPool = new StaticExporterPool();
        } else {
            m_exporterPool = new DynamicExporterPool();
        }

        m_writeInterestManager = new IBWriteInterestManager();

        m_nodeConnected = new boolean[NodeID.MAX_ID];

        m_sendWorkParameterPool = new IBSendWorkParameterPool(Long.BYTES + Integer.BYTES + Integer.BYTES + Short.BYTES);
    }

    /**
     * Initialize the infiniband subsystem. This calls to the underlying Ibdxnet subsystem and requires the respective library to be loaded
     */
    public void init() {
        // can't call this in the constructor because it relies on the implemented interfaces for callbacks
        if (!JNIIbdxnet
                .init(m_coreConfig.getOwnNodeId(), (int) m_config.getIncomingBufferSize().getBytes(), (int) m_config.getOugoingRingBufferSize().getBytes(),
                        m_config.getIncomingBufferPoolTotalSize().getBytes(), m_config.getMaxRecvReqs(), m_config.getMaxSendReqs(),
                        m_config.getFlowControlMaxRecvReqs(), m_config.getMaxConnections(), this, this, this, this, m_config.getEnableSignalHandler(),
                        m_config.getEnableDebugThread())) {
            // #if LOGGER >= DEBUG
            LOGGER.debug("Initializing ibnet failed, check ibnet logs");
            // #endif /* LOGGER >= DEBUG */

            throw new NetworkRuntimeException("Initializing ibnet failed");
        }

        // this is an ugly way of figuring out which nodes are available on startup. the ib subsystem needs that kind of information to
        // contact the nodes using an ethernet connection to exchange ib connection information
        // if you know a better/faster way of doing this here, be my guest and fix it
        for (int i = 0; i < NodeID.MAX_ID; i++) {
            if (i == (m_coreConfig.getOwnNodeId() & 0xFFFF)) {
                continue;
            }

            InetSocketAddress addr = m_nodeMap.getAddress((short) i);

            if (!"/255.255.255.255".equals(addr.getAddress().toString())) {
                byte[] bytes = addr.getAddress().getAddress();
                int val = (int) (((long) bytes[0] & 0xFF) << 24 | bytes[1] & 0xFF << 16 | bytes[2] & 0xFF << 8 | bytes[3] & 0xFF);
                JNIIbdxnet.addNode(val);
            }
        }
    }

    @Override
    public void close() {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Closing connection manager");
        // #endif /* LOGGER >= DEBUG */

        super.close();

        JNIIbdxnet.shutdown();
    }

    @Override
    protected AbstractConnection createConnection(final short p_destination, final AbstractConnection p_existingConnection) throws NetworkException {
        IBConnection connection;

        if (!m_nodeConnected[p_destination & 0xFFFF]) {
            throw new NetworkDestinationUnreachableException(p_destination);
        }

        m_connectionCreationLock.lock();
        if (m_openConnections == m_config.getMaxConnections()) {
            dismissRandomConnection();
        }

        connection = (IBConnection) m_connections[p_destination & 0xFFFF];

        if (connection == null) {
            connection = new IBConnection(m_coreConfig.getOwnNodeId(), p_destination, (int) m_config.getOugoingRingBufferSize().getBytes(),
                    (int) m_config.getFlowControlWindow().getBytes(), m_config.getFlowControlWindowThreshold(), m_messageDirectory, m_requestMap,
                    m_exporterPool, m_messageHandlers, m_writeInterestManager);
            m_openConnections++;
        }

        m_connectionCreationLock.unlock();

        connection.setPipeInConnected(true);
        connection.setPipeOutConnected(true);

        return connection;
    }

    @Override
    protected void closeConnection(final AbstractConnection p_connection, final boolean p_removeConnection) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Closing connection 0x%X", p_connection.getDestinationNodeID());
        // #endif /* LOGGER >= DEBUG */

        p_connection.setPipeInConnected(false);
        p_connection.setPipeOutConnected(false);

        m_connectionCreationLock.lock();
        AbstractConnection tmp = m_connections[p_connection.getDestinationNodeID() & 0xFFFF];
        if (p_connection.equals(tmp)) {
            p_connection.close(p_removeConnection);
            m_connections[p_connection.getDestinationNodeID() & 0xFFFF] = null;
            m_openConnections--;
        }
        m_connectionCreationLock.unlock();

        // Trigger failure handling for remote node over faulty connection
        m_listener.connectionLost(p_connection.getDestinationNodeID());
    }

    @Override
    public void nodeDiscovered(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node discovered 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */

        m_nodeConnected[p_nodeId & 0xFFFF] = true;
    }

    @Override
    public void nodeInvalidated(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node invalidated 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */

        m_nodeConnected[p_nodeId & 0xFFFF] = false;
    }

    @Override
    public void nodeConnected(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node connected 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */
    }

    @Override
    public void nodeDisconnected(final short p_nodeId) {
        // #if LOGGER >= DEBUG
        LOGGER.debug("Node disconnected 0x%X", p_nodeId);
        // #endif /* LOGGER >= DEBUG */

        closeConnection(m_connections[p_nodeId], true);
    }

    @Override
    public long getNextDataToSend(final short p_prevNodeIdWritten, final int p_prevDataWrittenLen) {
        // return interest of previous call
        if (p_prevNodeIdWritten != NodeID.INVALID_ID) {
            // #if LOGGER >= TRACE
            LOGGER.trace("getNextDataToSend, p_prevNodeIdWritten 0x%X, p_prevDataWrittenLen %d", p_prevNodeIdWritten, p_prevDataWrittenLen);
            // #endif /* LOGGER >= TRACE */

            m_writeInterestManager.finishedProcessingInterests(p_prevNodeIdWritten);

            // also notify that previous data has been processed (if connection is still available)
            try {
                IBConnection prevConnection = (IBConnection) getConnection(p_prevNodeIdWritten);
                prevConnection.getPipeOut().dataProcessed(p_prevDataWrittenLen);
            } catch (final NetworkException e) {
                // #if LOGGER >= ERROR
                LOGGER.trace("Getting connection 0x%X for previous data written failed", p_prevNodeIdWritten);
                // #endif /* LOGGER >= ERROR */
            }
        }

        // poll for next interest
        short nodeId = m_writeInterestManager.getNextInterests();

        // no data available
        if (nodeId == NodeID.INVALID_ID) {
            return 0;
        }

        // #if LOGGER >= TRACE
        LOGGER.trace("Next write interests on node 0x%X", nodeId);
        // #endif /* LOGGER >= TRACE */

        // prepare next work load
        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(nodeId);
        } catch (final NetworkException ignored) {
            m_writeInterestManager.nodeDisconnected(nodeId);
            return 0;
        }

        // assemble arguments for struct to pass back to jni
        ByteBuffer arguments = m_sendWorkParameterPool.getInstance();
        arguments.clear();

        long interests = m_writeInterestManager.consumeInterests(nodeId);

        // process data interests
        if ((int) interests > 0) {
            long pos = connection.getPipeOut().getNextBuffer();
            int relPosBackRel = (int) (pos >> 32 & 0x7FFFFFFF);
            int relPosFrontRel = (int) (pos & 0x7FFFFFFF);

            // relative position of data start in buffer
            arguments.putInt(relPosFrontRel);
            // relative position of data end in buffer
            arguments.putInt(relPosBackRel);

            // #if LOGGER >= TRACE
            LOGGER.trace("Next data write on node 0x%X, posFrontRelative %d, posBackRelative %d", nodeId, relPosFrontRel, relPosBackRel);
            // #endif /* LOGGER >= TRACE */

            // check if outgoing is empty or if we got the first part of a wrap around
            // if wrap around -> push back a new interest to not forget the wrap around
            if (!connection.getPipeOut().isOutgoingQueueEmpty()) {
                m_writeInterestManager.pushBackDataInterest(nodeId);
            }
        } else {
            // no data to write, fc only
            arguments.putInt(0);
            arguments.putInt(0);
        }

        // process flow control interests
        if (interests >> 32 > 0) {
            int fcData = connection.getPipeOut().getFlowControlToWrite();

            arguments.putInt(fcData);

            // #if LOGGER >= TRACE
            LOGGER.trace("Next flow control write on node 0x%X, fc data %d", nodeId, fcData);
            // #endif /* LOGGER >= TRACE */
        } else {
            // data only, no fc
            arguments.putInt(0);
        }

        // node id
        arguments.putShort(nodeId);

        return ByteBufferHelper.getDirectAddress(arguments);
    }

    @Override
    public void receivedBuffer(final short p_sourceNodeId, final long p_bufferHandle, final long p_addr, final int p_length) {
        // #if LOGGER >= TRACE
        LOGGER.trace("Received buffer (0x%X, %d) from 0x%X", p_addr, p_length, p_sourceNodeId);
        // #endif /* LOGGER >= TRACE */

        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(p_sourceNodeId);
        } catch (final NetworkException e) {
            // #if LOGGER >= ERROR
            LOGGER.error("Getting connection for recv buffer of node 0x%X failed", p_sourceNodeId, e);
            // #endif /* LOGGER >= ERROR */
            return;
        }

        // Avoid congestion by not allowing more than m_numberOfBuffers buffers to be cached for reading
        while (!m_messageCreator.pushJob(connection, null, p_bufferHandle, p_addr, p_length)) {
            // #if LOGGER == TRACE
            LOGGER.trace("Message creator: Job queue is full!");
            // #endif /* LOGGER == TRACE */

            Thread.yield();
        }
    }

    @Override
    public void receivedFlowControlData(final short p_sourceNodeId, final int p_bytes) {
        // #if LOGGER >= TRACE
        LOGGER.trace("Received flow control data (%d) from 0x%X", p_bytes, p_sourceNodeId);
        // #endif /* LOGGER >= TRACE */

        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(p_sourceNodeId);
        } catch (final NetworkException e) {
            // #if LOGGER >= ERROR
            LOGGER.error("Getting connection for recv flow control data of node 0x%X failed", p_sourceNodeId, e);
            // #endif /* LOGGER >= ERROR */
            return;
        }

        connection.getPipeIn().handleFlowControlData(p_bytes);
    }
}
