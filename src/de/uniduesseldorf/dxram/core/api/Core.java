package de.uniduesseldorf.dxram.core.api;

import java.util.Arrays;

import org.apache.log4j.Logger;

import de.uniduesseldorf.dxram.core.CoreComponentFactory;
import de.uniduesseldorf.dxram.core.api.config.Configuration;
import de.uniduesseldorf.dxram.core.api.config.ConfigurationHelper;
import de.uniduesseldorf.dxram.core.api.config.NodesConfiguration;
import de.uniduesseldorf.dxram.core.api.config.NodesConfigurationHelper;
import de.uniduesseldorf.dxram.core.chunk.Chunk;
import de.uniduesseldorf.dxram.core.chunk.ChunkInterface;
import de.uniduesseldorf.dxram.core.events.IncomingChunkListener;
import de.uniduesseldorf.dxram.core.exceptions.ChunkException;
import de.uniduesseldorf.dxram.core.exceptions.ComponentCreationException;
import de.uniduesseldorf.dxram.core.exceptions.DXRAMException;
import de.uniduesseldorf.dxram.core.exceptions.ExceptionHandler;
import de.uniduesseldorf.dxram.core.exceptions.ExceptionHandler.ExceptionSource;
import de.uniduesseldorf.dxram.core.exceptions.LookupException;
import de.uniduesseldorf.dxram.core.exceptions.NetworkException;
import de.uniduesseldorf.dxram.core.exceptions.PrimaryLogException;
import de.uniduesseldorf.dxram.core.exceptions.RecoveryException;
import de.uniduesseldorf.dxram.core.log.LogInterface;
import de.uniduesseldorf.dxram.utils.Contract;
import de.uniduesseldorf.dxram.utils.StringConverter;

/**
 * API for DXRAM
 * @author Florian Klein 09.03.2012
 */
public final class Core {

	// Constants
	private static final Logger LOGGER = Logger.getLogger(Core.class);

	// Attributes
	private static ConfigurationHelper m_configurationHelper;
	private static NodesConfigurationHelper m_nodesConfigurationHelper;

	private static ChunkInterface m_chunk;
	private static LogInterface m_log;
	private static ExceptionHandler m_exceptionHandler;

	// Constructors
	/**
	 * Creates an instance of DXRAM
	 */
	private Core() {}

	// Getters
	/**
	 * Get the DXRAM configuration
	 * @return the configuration
	 */
	public static ConfigurationHelper getConfiguration() {
		return m_configurationHelper;
	}

	/**
	 * Get the parsed DXRAM nodes configuration
	 * @return the parsed nodes configuration
	 */
	public static NodesConfigurationHelper getNodesConfiguration() {
		return m_nodesConfigurationHelper;
	}

	// Setters
	/**
	 * Set the IncomingChunkListener for DXRAM
	 * @param p_listener
	 *            the IncomingChunkListener
	 */
	public static void setListener(final IncomingChunkListener p_listener) {
		m_chunk.setListener(p_listener);
	}

	/**
	 * Set the ExceptionHandler for DXRAM
	 * @param p_exceptionHandler
	 *            the ExceptionHandler
	 */
	public static void setExceptionHandler(final ExceptionHandler p_exceptionHandler) {
		m_exceptionHandler = p_exceptionHandler;
	}

	// Methods
	/**
	 * Initializes DXRAM<br>
	 * Should be called before any other method call of DXRAM
	 * @param p_configuration
	 *            the configuration to use
	 * @param p_nodesConfiguration
	 *            the nodes configuration to use
	 */
	public static void initialize(final Configuration p_configuration, final NodesConfiguration p_nodesConfiguration) {
		LOGGER.trace("Entering initialize with: p_configuration=" + p_configuration + ", p_nodesConfiguration="
				+ p_nodesConfiguration);

		try {
			p_configuration.makeImmutable();
			m_configurationHelper = new ConfigurationHelper(p_configuration);

			m_nodesConfigurationHelper = new NodesConfigurationHelper(p_nodesConfiguration);

			CoreComponentFactory.getNetworkInterface();
			m_chunk = CoreComponentFactory.getChunkInterface();
			if (!NodeID.isSuperpeer()) {
				m_log = CoreComponentFactory.getLogInterface();
			}

			// Register shutdown thread
			Runtime.getRuntime().addShutdownHook(new ShutdownThread());
		} catch (final Exception e) {
			LOGGER.fatal("FATAL::Could not instantiate chunk interface", e);

			handleException(e, ExceptionSource.DXRAM_INITIALIZE);
		}

		LOGGER.trace("Exiting initialize");
	}

	/**
	 * Closes DXRAM und frees unused ressources
	 */
	public static void close() {
		LOGGER.trace("Entering close");

		CoreComponentFactory.closeAll();

		LOGGER.trace("Exiting close");
	}

	/**
	 * Get the own NodeID
	 * @return the own NodeID
	 */
	public static short getNodeID() {
		return NodeID.getLocalNodeID();
	}

	/**
	 * Creates a new Chunk
	 * @param p_size
	 *            the size of the Chunk
	 * @return a new Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be created
	 */
	public static Chunk createNewChunk(final int p_size) throws DXRAMException {
		Chunk ret = null;

		LOGGER.trace("Entering createNewChunk");

		try {
			if (m_chunk != null) {
				ret = m_chunk.create(p_size);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Create new Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_CREATE_NEW_CHUNK)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting createNewChunk with: ret=" + ret);

		return ret;
	}

	/**
	 * Creates new Chunks
	 * @param p_sizes
	 *            the sizes of the Chunks
	 * @return new Chunks
	 * @throws DXRAMException
	 *             if the chunks could not be created
	 */
	public static Chunk[] createNewChunk(final int[] p_sizes) throws DXRAMException {
		Chunk[] ret = null;

		LOGGER.trace("Entering createNewChunk");

		try {
			if (m_chunk != null) {
				ret = m_chunk.create(p_sizes);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Create new Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_CREATE_NEW_CHUNK)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting createNewChunk with: ret=" + ret);

		return ret;
	}

	/**
	 * Creates a new Chunk with identifier
	 * @param p_size
	 *            the size of the Chunk
	 * @param p_name
	 *            the identifier of the Chunk
	 * @return a new Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be created
	 */
	public static Chunk createNewChunk(final int p_size, final String p_name) throws DXRAMException {
		Chunk ret = null;

		LOGGER.trace("Entering createNewChunk with identifier");

		try {
			if (m_chunk != null) {
				ret = m_chunk.create(p_size, StringConverter.convert(p_name));
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Create new Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_CREATE_NEW_CHUNK)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting createNewChunk with: ret=" + ret);

		return ret;
	}

	/**
	 * Creates new Chunks with identifier
	 * @param p_sizes
	 *            the sizes of the Chunks
	 * @param p_name
	 *            the identifier of the first Chunk
	 * @return new Chunks
	 * @throws DXRAMException
	 *             if the chunks could not be created
	 */
	public static Chunk[] createNewChunk(final int[] p_sizes, final String p_name) throws DXRAMException {
		Chunk[] ret = null;

		LOGGER.trace("Entering createNewChunk with identifier");

		try {
			if (m_chunk != null) {
				ret = m_chunk.create(p_sizes, StringConverter.convert(p_name));
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Create new Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_CREATE_NEW_CHUNK)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting createNewChunk with: ret=" + ret);

		return ret;
	}

	/**
	 * Gets the corresponding Chunk for the given ID
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @return the Chunk for the given ID
	 * @throws DXRAMException
	 *             if the chunk could not be get
	 */
	public static Chunk get(final long p_chunkID) throws DXRAMException {
		Chunk ret = null;

		LOGGER.trace("Entering get with: p_chunkID=" + p_chunkID);

		ChunkID.check(p_chunkID);

		try {
			if (m_chunk != null) {
				ret = m_chunk.get(p_chunkID);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Get Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_GET, p_chunkID)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting get with: ret=" + ret);

		return ret;
	}

	/**
	 * Gets the corresponding Chunks for the given IDs
	 * @param p_chunkIDs
	 *            the IDs of the corresponding Chunks
	 * @return the Chunks for the given IDs
	 * @throws DXRAMException
	 *             if the chunks could not be get
	 */
	public static Chunk[] get(final long[] p_chunkIDs) throws DXRAMException {
		Chunk[] ret = null;

		LOGGER.trace("Entering get with: p_chunkIDs=" + Arrays.toString(p_chunkIDs));

		Contract.checkNotNull(p_chunkIDs, "no IDs given");

		ChunkID.check(p_chunkIDs);

		try {
			if (m_chunk != null) {
				ret = m_chunk.get(p_chunkIDs);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Get Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_GET, p_chunkIDs)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting get with: ret=" + Arrays.toString(ret));

		return ret;
	}

	/**
	 * Gets the corresponding Chunk for the given identifier
	 * @param p_name
	 *            the identifier of the corresponding Chunk
	 * @return the Chunk for the given ID
	 * @throws DXRAMException
	 *             if the chunk could not be get
	 */
	public static Chunk get(final String p_name) throws DXRAMException {
		Chunk ret = null;
		int id;

		LOGGER.trace("Entering get with: p_name=" + p_name);

		id = StringConverter.convert(p_name);
		try {
			if (m_chunk != null) {
				ret = m_chunk.get(id);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Get Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_GET, id)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting get with: ret=" + ret);

		return ret;
	}

	/**
	 * Gets the corresponding ChunkID for the given identifier
	 * @param p_name
	 *            the identifier of the corresponding Chunk
	 * @return the ChunkID for the given ID
	 * @throws DXRAMException
	 *             if the chunk could not be get
	 */
	public static long getChunkID(final String p_name) throws DXRAMException {
		long ret = -1;
		int id;

		LOGGER.trace("Entering get with: p_name=" + p_name);

		id = StringConverter.convert(p_name);
		try {
			if (m_chunk != null) {
				ret = m_chunk.getChunkID(id);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Get Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_GET, id)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting get with: ret=" + ret);

		return ret;
	}

	/**
	 * Requests the corresponding Chunk for the given ID<br>
	 * An IncomingChunkEvent will be triggered on the arrival of the Chunk
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be get
	 */
	public static void getAsync(final long p_chunkID) throws DXRAMException {
		LOGGER.trace("Entering getAsync with: p_chunkID=" + p_chunkID);

		ChunkID.check(p_chunkID);

		try {
			if (m_chunk != null) {
				m_chunk.getAsync(p_chunkID);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Get Chunk asynchronously", e);

			if (!handleException(e, ExceptionSource.DXRAM_GET_ASYNC, p_chunkID)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting getAsync");
	}

	/**
	 * Updates the given Chunk
	 * @param p_chunk
	 *            the Chunk to be updated
	 * @throws DXRAMException
	 *             if the chunk could not be put
	 */
	public static void put(final Chunk p_chunk) throws DXRAMException {
		LOGGER.trace("Entering put with: p_chunk=" + p_chunk);

		Contract.checkNotNull(p_chunk, "no chunk given");

		try {
			if (m_chunk != null) {
				m_chunk.put(p_chunk);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Put Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_PUT, p_chunk)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting put");
	}

	/**
	 * Requests and locks the corresponding Chunk for the giving ID
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @return the Chunk for the given ID
	 * @throws DXRAMException
	 *             if the chunk could not be locked
	 */
	public static Chunk lock(final long p_chunkID) throws DXRAMException {
		Chunk ret = null;

		LOGGER.trace("Entering lock with: p_chunkID=" + p_chunkID);

		ChunkID.check(p_chunkID);

		try {
			if (m_chunk != null) {
				ret = m_chunk.lock(p_chunkID);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Lock Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_LOCK, p_chunkID)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting lock with: ret=" + ret);

		return ret;
	}

	/**
	 * Unlocks the corresponding Chunk for the giving ID
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be unlocked
	 */
	public static void unlock(final long p_chunkID) throws DXRAMException {
		LOGGER.trace("Entering unlock with: p_chunkID=" + p_chunkID);

		ChunkID.check(p_chunkID);

		try {
			if (m_chunk != null) {
				m_chunk.unlock(p_chunkID);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Lock Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_LOCK, p_chunkID)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting unlock");
	}

	/**
	 * Removes the corresponding Chunk for the giving ID
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be removed
	 */
	public static void remove(final long p_chunkID) throws DXRAMException {
		LOGGER.trace("Entering remove with: p_chunkID=" + p_chunkID);

		ChunkID.check(p_chunkID);

		try {
			if (m_chunk != null) {
				m_chunk.remove(p_chunkID);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Remove Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_REMOVE, p_chunkID)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting remove");
	}

	/**
	 * Migrates the corresponding Chunk for the giving ID to another Node
	 * @param p_chunkID
	 *            the ID of the corresponding Chunk
	 * @param p_target
	 *            the Node where to migrate the Chunk
	 * @throws DXRAMException
	 *             if the chunk could not be migrated
	 */
	public static void migrate(final long p_chunkID, final short p_target) throws DXRAMException {
		LOGGER.trace("Entering migrate with: p_chunkID=" + p_chunkID + ", p_target=" + p_target);

		ChunkID.check(p_chunkID);
		NodeID.check(p_target);

		try {
			if (m_chunk != null) {
				m_chunk.migrate(p_chunkID, p_target);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Migrate Chunk", e);

			if (!handleException(e, ExceptionSource.DXRAM_MIGRATE, p_chunkID, p_target)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting migrate");
	}

	/**
	 * Migrates the corresponding Chunks for the giving ID range to another Node
	 * @param p_startChunkID
	 *            the first ID
	 * @param p_endChunkID
	 *            the last ID
	 * @param p_target
	 *            the Node where to migrate the Chunk
	 * @throws DXRAMException
	 *             if the chunks could not be migrated
	 */
	public static void migrateRange(final long p_startChunkID, final long p_endChunkID, final short p_target)
			throws DXRAMException {
		LOGGER.trace("Entering migrateRange with: p_startChunkID=" + p_startChunkID + ", p_endChunkID="
				+ p_endChunkID + ", p_target=" + p_target);

		ChunkID.check(p_startChunkID);
		ChunkID.check(p_endChunkID);
		NodeID.check(p_target);

		try {
			if (m_chunk != null) {
				m_chunk.migrateRange(p_startChunkID, p_endChunkID, p_target);
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Migrate Range", e);

			if (!handleException(e, ExceptionSource.DXRAM_MIGRATE, p_startChunkID, p_target)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting migrateRange");
	}

	/**
	 * Recovers the local data from the log
	 * @throws DXRAMException
	 *             if the chunks could not be recovered
	 */
	public static void recoverFromLog() throws DXRAMException {
		LOGGER.trace("Entering recoverFromLog");

		try {
			if (m_chunk != null) {
				m_chunk.recoverFromLog();
			}
		} catch (final DXRAMException e) {
			LOGGER.error("ERR::Recover", e);

			if (!handleException(e, ExceptionSource.DXRAM_RECOVER_FROM_LOG)) {
				throw e;
			}
		}

		LOGGER.trace("Exiting recoverFromLog");
	}

	/**
	 * Handles an occured exception
	 * @param p_exception
	 *            the occured exception
	 * @param p_source
	 *            the source of the exception
	 * @param p_parameters
	 *            the parameters of the method in which the exception occured (optional)
	 * @return if true the exception will be thrown
	 */
	public static boolean handleException(final Exception p_exception, final ExceptionSource p_source,
			final Object... p_parameters) {
		boolean ret = false;

		if (m_exceptionHandler != null) {
			if (p_exception instanceof LookupException) {
				ret = m_exceptionHandler.handleException((LookupException)p_exception, p_source, p_parameters);
			} else if (p_exception instanceof ChunkException) {
				ret = m_exceptionHandler.handleException((ChunkException)p_exception, p_source, p_parameters);
			} else if (p_exception instanceof NetworkException) {
				ret = m_exceptionHandler.handleException((NetworkException)p_exception, p_source, p_parameters);
			} else if (p_exception instanceof PrimaryLogException) {
				ret = m_exceptionHandler.handleException((PrimaryLogException)p_exception, p_source, p_parameters);
			} else if (p_exception instanceof RecoveryException) {
				ret = m_exceptionHandler.handleException((RecoveryException)p_exception, p_source, p_parameters);
			} else if (p_exception instanceof ComponentCreationException) {
				ret = m_exceptionHandler.handleException((ComponentCreationException)p_exception, p_source,
						p_parameters);
			} else {
				ret = m_exceptionHandler.handleException(p_exception, p_source, p_parameters);
			}
		}

		return ret;
	}

	// Classe
	/**
	 * Shuts down DXRAM in case of the system exits
	 * @author Florian Klein 03.09.2013
	 */
	private static final class ShutdownThread extends Thread {

		// Constructors
		/**
		 * Creates an instance of ShutdownThread
		 */
		private ShutdownThread() {
			super(ShutdownThread.class.getSimpleName());
		}

		// Methods
		@Override
		public void run() {
			close();
		}

	}

}
