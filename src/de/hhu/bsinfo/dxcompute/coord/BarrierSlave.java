
package de.hhu.bsinfo.dxcompute.coord;

import de.hhu.bsinfo.dxcompute.coord.messages.BarrierSlaveSignOnRequest;
import de.hhu.bsinfo.dxcompute.coord.messages.BarrierSlaveSignOnResponse;
import de.hhu.bsinfo.dxcompute.coord.messages.CoordinatorMessages;
import de.hhu.bsinfo.dxcompute.coord.messages.MasterSyncBarrierReleaseMessage;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.net.NetworkErrorCodes;
import de.hhu.bsinfo.menet.AbstractMessage;
import de.hhu.bsinfo.menet.NetworkHandler.MessageReceiver;
import de.hhu.bsinfo.menet.NodeID;

/**
 * Counterpart for the SyncBarrierMaster, this is used on a slave node to sync
 * multiple slaves to a single master.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 18.02.16
 */
public class BarrierSlave implements MessageReceiver {

	private int m_barrierIdentifer = -1;

	private volatile boolean m_masterBarrierReleased;
	private volatile long m_barrierDataFromMaster;

	private NetworkComponent m_networkService;
	private LoggerComponent m_loggerService;

	/**
	 * Constructor
	 * @param p_barrierIdentifier
	 *            Token to identify this barrier (if using multiple barriers), which is used as a sync token.
	 */
	public BarrierSlave(final NetworkComponent p_network, final LoggerComponent p_logger) {
		m_networkService = p_network;
		m_loggerService = p_logger;

		m_networkService.registerMessageType(CoordinatorMessages.TYPE,
				CoordinatorMessages.SUBTYPE_BARRIER_SLAVE_SIGN_ON_REQUEST, BarrierSlaveSignOnRequest.class);
		m_networkService.registerMessageType(CoordinatorMessages.TYPE,
				CoordinatorMessages.SUBTYPE_BARRIER_SLAVE_SIGN_ON_RESPONSE, BarrierSlaveSignOnResponse.class);
		m_networkService.registerMessageType(CoordinatorMessages.TYPE,
				CoordinatorMessages.SUBTYPE_BARRIER_MASTER_RELEASE, MasterSyncBarrierReleaseMessage.class);
	}

	public boolean execute(final short p_masterNodeId, final int p_barrierIdentifier, final long p_data) {
		m_barrierIdentifer = p_barrierIdentifier;
		m_masterBarrierReleased = false;
		m_barrierDataFromMaster = -1;

		m_networkService.register(MasterSyncBarrierReleaseMessage.class, this);

		m_loggerService.debug(getClass(),
				"Sign on request to master " + NodeID.toHexString(p_masterNodeId) + " with identifier "
						+ m_barrierIdentifer + "...");

		// request for sign on and retry until we succeed
		do {
			BarrierSlaveSignOnRequest request =
					new BarrierSlaveSignOnRequest(p_masterNodeId, m_barrierIdentifer, p_data);
			NetworkErrorCodes err = m_networkService.sendSync(request);
			if (err == NetworkErrorCodes.SUCCESS) {
				BarrierSlaveSignOnResponse response = (BarrierSlaveSignOnResponse) request.getResponse();
				if (response.getSyncToken() == m_barrierIdentifer) {
					if (response.getStatusCode() == 1) {
						m_loggerService.error(getClass(),
								"Signed on to master " + NodeID.toHexString(p_masterNodeId) + " with identifier "
										+ m_barrierIdentifer + " failed, invalid sync token");
					} else {
						m_loggerService.debug(getClass(),
								"Signed on to master " + NodeID.toHexString(p_masterNodeId) + " with identifier "
										+ m_barrierIdentifer);
						break;
					}
				}
			} else {
				m_loggerService.debug(getClass(), "Sign on in progress: " + err);
			}

			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {}
		} while (true);

		while (!m_masterBarrierReleased) {
			Thread.yield();
		}

		m_loggerService.debug(getClass(),
				"Master barrier " + m_barrierIdentifer + " released, data received: " + m_barrierDataFromMaster);

		return true;
	}

	public long getBarrierData() {
		return m_barrierDataFromMaster;
	}

	@Override
	public void onIncomingMessage(final AbstractMessage p_message) {
		if (p_message != null) {
			if (p_message.getType() == CoordinatorMessages.TYPE) {
				switch (p_message.getSubtype()) {
					case CoordinatorMessages.SUBTYPE_BARRIER_MASTER_RELEASE:
						incomingBarrierMasterRelease((MasterSyncBarrierReleaseMessage) p_message);
						break;
					default:
						break;
				}
			}
		}
	}

	/**
	 * Handle incoming MasterSyncBarrierReleaseMessage.
	 * @param p_message
	 *            Message to handle.
	 */
	private void incomingBarrierMasterRelease(final MasterSyncBarrierReleaseMessage p_message) {
		// ignore non matching sync tokens
		if (p_message.getSyncToken() != m_barrierIdentifer) {
			return;
		}

		m_networkService.unregister(MasterSyncBarrierReleaseMessage.class, this);

		m_barrierDataFromMaster = p_message.getData();
		m_masterBarrierReleased = true;
	}

}