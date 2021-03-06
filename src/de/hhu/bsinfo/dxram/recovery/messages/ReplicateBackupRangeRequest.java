/*
 * Copyright (C) 2018 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.recovery.messages;

import de.hhu.bsinfo.dxram.DXRAMMessageTypes;
import de.hhu.bsinfo.dxram.backup.RangeID;
import de.hhu.bsinfo.dxnet.core.AbstractMessageExporter;
import de.hhu.bsinfo.dxnet.core.AbstractMessageImporter;
import de.hhu.bsinfo.dxnet.core.Request;

/**
 * Replicate Backup Range Message
 *
 * @author Kevin Beineke, kevin.beineke@hhu.de, 30.06.2017
 */
public class ReplicateBackupRangeRequest extends Request {

    // Attributes
    private short m_rangeID;

    // Constructors

    /**
     * Creates an instance of RecoverBackupRangeRequest
     */
    public ReplicateBackupRangeRequest() {
        super();

        m_rangeID = RangeID.INVALID_ID;
    }

    /**
     * Creates an instance of RecoverBackupRangeRequest
     *
     * @param p_destination
     *         the destination
     * @param p_rangeID
     *         the range ID
     */
    public ReplicateBackupRangeRequest(final short p_destination, final short p_rangeID) {
        super(p_destination, DXRAMMessageTypes.RECOVERY_MESSAGES_TYPE, RecoveryMessages.SUBTYPE_REPLICATE_BACKUP_RANGE_REQUEST);

        m_rangeID = p_rangeID;
    }

    // Getters

    /**
     * Get the range ID
     *
     * @return the RangeID
     */
    public final short getRangeID() {
        return m_rangeID;
    }

    @Override
    protected final int getPayloadLength() {
        return Short.BYTES;
    }

    // Methods
    @Override
    protected final void writePayload(final AbstractMessageExporter p_exporter) {
        p_exporter.writeShort(m_rangeID);
    }

    @Override
    protected final void readPayload(final AbstractMessageImporter p_importer) {
        m_rangeID = p_importer.readShort(m_rangeID);
    }

}
