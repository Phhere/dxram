package de.uniduesseldorf.dxram.core.chunk.messages;

import java.nio.ByteBuffer;

import de.uniduesseldorf.dxram.core.data.Chunk;
import de.uniduesseldorf.dxram.core.data.DataStructure;
import de.uniduesseldorf.dxram.core.data.MessagesDataStructureImExporter;
import de.uniduesseldorf.dxram.core.util.ChunkLockOperation;
import de.uniduesseldorf.dxram.core.util.ChunkMessagesMetadataUtils;

import de.uniduesseldorf.menet.AbstractRequest;

/**
 * Request for updating a Chunk on a remote node
 * @author Florian Klein 09.03.2012
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 11.12.15
 */
public class PutRequest extends AbstractRequest {

	// DataStructures used when sending the put request.
	// These are also used by the response to directly write the
	// receiving data to the structures
	// Chunks are created and used when receiving a put request
	private DataStructure[] m_dataStructures = null;

	/**
	 * Creates an instance of PutRequest.
	 * This constructor is used when receiving this message.
	 */
	public PutRequest() {
		super();
	}

	/**
	 * Creates an instance of PutRequest
	 * @param p_destination
	 *            the destination
	 * @param p_dataStructure
	 *            Data structure with the data to put.
	 * @param p_releaseLock
	 *            if true a potential lock will be released
	 */
	public PutRequest(final short p_destination, final ChunkLockOperation m_unlockOperation, final DataStructure... p_dataStructures) {
		super(p_destination, ChunkMessages.TYPE, ChunkMessages.SUBTYPE_PUT_REQUEST);

		m_dataStructures = p_dataStructures;
		
		byte tmpCode = getStatusCode();
		switch (m_unlockOperation)
		{
			case NO_LOCK_OPERATION:
				break;
			case READ_LOCK:
				ChunkMessagesMetadataUtils.setReadLockFlag(tmpCode, true);
			case WRITE_LOCK:
				ChunkMessagesMetadataUtils.setWriteLockFlag(tmpCode, true);
			default:
				assert 1 == 2;
		}
		
		ChunkMessagesMetadataUtils.setNumberOfItemsToSend(tmpCode, p_dataStructures.length);
		setStatusCode(tmpCode);
	}

	/**
	 * Get the DataStructures to put when this message is received.
	 * @return the Chunk to put
	 */
	public final DataStructure[] getDataStructures() {
		return m_dataStructures;
	}

	public ChunkLockOperation getUnlockOperation() {
		if (ChunkMessagesMetadataUtils.isLockAcquireFlagSet(getStatusCode())) {
			if (ChunkMessagesMetadataUtils.isReadLockFlagSet(getStatusCode())) {
				return ChunkLockOperation.READ_LOCK;
			} else {
				return ChunkLockOperation.WRITE_LOCK;
			}
		} else {
			return ChunkLockOperation.NO_LOCK_OPERATION;
		}
	}

	// Methods
	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		ChunkMessagesMetadataUtils.setNumberOfItemsInMessageBuffer(getStatusCode(), p_buffer, m_dataStructures.length);
		
		MessagesDataStructureImExporter exporter = new MessagesDataStructureImExporter(p_buffer);
		for (DataStructure dataStructure : m_dataStructures) {
			int size = dataStructure.sizeofObject();
			
			p_buffer.putLong(dataStructure.getID());
			exporter.setPayloadSize(size);
			p_buffer.putInt(size);
			exporter.exportObject(dataStructure);
		}
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		MessagesDataStructureImExporter importer = new MessagesDataStructureImExporter(p_buffer);
		int numChunks = ChunkMessagesMetadataUtils.getSizeOfAdditionalLengthField(getStatusCode());
		
		m_dataStructures = new Chunk[numChunks];
		
		for (int i = 0; i < m_dataStructures.length; i++) {
			long id = p_buffer.getLong();
			int size = p_buffer.getInt();
			
			importer.setPayloadSize(size);
			m_dataStructures[i] = new Chunk(id, size);
			importer.importObject(m_dataStructures[i]);
		}
	}

	@Override
	protected final int getPayloadLengthForWrite() {	
		int size = ChunkMessagesMetadataUtils.getSizeOfAdditionalLengthField(getStatusCode());
		
		size += m_dataStructures.length * Long.BYTES;
		size += m_dataStructures.length * Integer.BYTES;
		
		for (DataStructure dataStructure : m_dataStructures) {
			size += dataStructure.sizeofObject();
		}
		
		return size;
	}

}