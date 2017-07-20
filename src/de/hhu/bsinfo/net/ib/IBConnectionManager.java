package de.hhu.bsinfo.net.ib;

import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.net.NetworkDestinationUnreachableException;
import de.hhu.bsinfo.net.NodeMap;
import de.hhu.bsinfo.net.core.AbstractConnection;
import de.hhu.bsinfo.net.core.AbstractConnectionManager;
import de.hhu.bsinfo.net.core.DataReceiver;
import de.hhu.bsinfo.net.core.MessageCreator;
import de.hhu.bsinfo.net.core.MessageDirectory;
import de.hhu.bsinfo.net.core.NetworkException;
import de.hhu.bsinfo.net.core.NetworkRuntimeException;
import de.hhu.bsinfo.net.core.RequestMap;
import de.hhu.bsinfo.utils.NodeID;

/**
 * Created by nothaas on 6/13/17.
 */
public class IBConnectionManager extends AbstractConnectionManager
        implements JNIIbdxnet.SendHandler, JNIIbdxnet.RecvHandler, JNIIbdxnet.DiscoveryHandler, JNIIbdxnet.ConnectionHandler {
    private static final Logger LOGGER = LogManager.getFormatterLogger(IBConnectionManager.class.getSimpleName());

    private final IBConnectionManagerConfig m_config;
    private final NodeMap m_nodeMap;

    private final MessageDirectory m_messageDirectory;
    private final RequestMap m_requestMap;
    private final MessageCreator m_messageCreator;
    private final DataReceiver m_dataReceiver;

    private final IBWriteInterestManager m_writeInterestManager;

    private final boolean[] m_nodeConnected;

    // struct NextWorkParameters
    // {
    //    uint64_t m_ptrBuffer;
    //    uint32_t m_len;
    //    uint32_t m_flowControlData;
    //    uint16_t m_nodeId;
    //} __attribute__((packed));
    private final IBSendWorkParameterPool m_sendWorkParameterPool;

    public IBConnectionManager(final IBConnectionManagerConfig p_config, final NodeMap p_nodeMap, final MessageDirectory p_messageDirectory,
            final RequestMap p_requestMap, final MessageCreator p_messageCreator, final DataReceiver p_dataReciever) {
        super(p_config);

        m_config = p_config;
        m_nodeMap = p_nodeMap;

        m_messageDirectory = p_messageDirectory;
        m_requestMap = p_requestMap;
        m_messageCreator = p_messageCreator;
        m_dataReceiver = p_dataReciever;

        m_writeInterestManager = new IBWriteInterestManager();

        m_nodeConnected = new boolean[NodeID.MAX_ID];

        m_sendWorkParameterPool = new IBSendWorkParameterPool(Long.BYTES + Integer.BYTES + Integer.BYTES + Short.BYTES);
    }

    public void init() {
        // can't call this in the constructor because it relies on the implemented interfaces for callbacks
        if (!JNIIbdxnet.init(m_config.getOwnNodeId(), m_config.getMaxRecvReqs(), m_config.getMaxSendReqs(), m_config.getBufferSize(),
                m_config.getFlowControlMaxRecvReqs(), m_config.getFlowControlMaxSendReqs(), m_config.getSendThreads(), m_config.getRecvThreads(),
                m_config.getMaxConnections(), this, this, this, this, m_config.getEnableSignalHandler(), m_config.getEnableDebugThread())) {
            // #if LOGGER >= DEBUG
            LOGGER.debug("Initializing ibnet failed, check ibnet logs");
            // #endif /* LOGGER >= DEBUG */

            throw new NetworkRuntimeException("Initializing ibnet failed");
        }

        // TODO ugly (temporary) workaround
        for (int i = 0; i < NodeID.MAX_ID; i++) {
            if (i == (m_config.getOwnNodeId() & 0xFFFF)) {
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
            connection = new IBConnection(m_config.getOwnNodeId(), p_destination, m_config.getBufferSize(), m_config.getFlowControlWindow(), m_messageDirectory,
                    m_requestMap, m_dataReceiver, m_writeInterestManager);
            m_connections[p_destination & 0xFFFF] = connection;
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

        // TODO correct spot?
        m_writeInterestManager.nodeDisconnected(p_nodeId);
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
    }

    @Override
    public long getNextDataToSend(final short p_prevNodeIdWritten, final int p_prevDataWrittenLen) {
        // return interest of previous call
        if (p_prevNodeIdWritten != NodeID.INVALID_ID) {

            m_writeInterestManager.finishedProcessingInterests(p_prevNodeIdWritten);

            // also notify that previous data has been processed (if connection is still available)
            try {
                IBConnection prevConnection = (IBConnection) getConnection(p_prevNodeIdWritten);
                prevConnection.getPipeOut().dataProcessed(p_prevDataWrittenLen);
            } catch (final NetworkException e) {
                // TODO ignore ?
            }
        }

        // poll for next interest
        short nodeId = m_writeInterestManager.getNextInterest();

        // no data available
        if (nodeId == NodeID.INVALID_ID) {
            return 0;
        }

        // #if LOGGER >= TRACE
        LOGGER.trace("Next write interest on node 0x%X", nodeId);
        // #endif /* LOGGER >= TRACE */

        // prepare next work load
        IBConnection connection;
        try {
            connection = (IBConnection) getConnection(nodeId);
        } catch (final NetworkException e) {
            // TODO ?

            m_writeInterestManager.nodeDisconnected(nodeId);
            return 0;
        }

        // return 0 if no data is available, otherwise valid (unsafe) memory address
        return connection.getPipeOut().getNextBuffer();
    }

    @Override
    public void receivedBuffer(final short p_sourceNodeId, final long p_addr, final int p_length) {
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
        while (!m_messageCreator.pushJob(connection, null, p_addr, p_length)) {
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
