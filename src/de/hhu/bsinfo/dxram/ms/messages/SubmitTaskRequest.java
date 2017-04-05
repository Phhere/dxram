/*
 * Copyright (C) 2017 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science, Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.ms.messages;

import java.nio.ByteBuffer;

import de.hhu.bsinfo.dxram.data.MessagesDataStructureImExporter;
import de.hhu.bsinfo.dxram.ms.TaskScript;
import de.hhu.bsinfo.dxram.DXRAMMessageTypes;
import de.hhu.bsinfo.ethnet.AbstractRequest;

/**
 * Submit a task script to a remote master compute node.
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 22.04.2016
 */
public class SubmitTaskRequest extends AbstractRequest {

    private TaskScript m_taskScript;

    /**
     * Creates an instance of RemoteExecuteTaskRequest.
     * This constructor is used when receiving this message.
     */
    public SubmitTaskRequest() {
        super();
    }

    /**
     * Creates an instance of RemoteExecuteTaskRequest.
     * This constructor is used when sending this message.
     *
     * @param p_destination
     *     the destination node id.
     * @param p_taskScript
     *     TaskScript to submit to the remote master node.
     */
    public SubmitTaskRequest(final short p_destination, final TaskScript p_taskScript) {
        super(p_destination, DXRAMMessageTypes.MASTERSLAVE_MESSAGES_TYPE, MasterSlaveMessages.SUBTYPE_SUBMIT_TASK_REQUEST);
        m_taskScript = p_taskScript;
    }

    /**
     * Get the task script submitted to the remote master.
     *
     * @return TaskScript for the master
     */
    public TaskScript getTaskScript() {
        return m_taskScript;
    }

    @Override
    protected final void writePayload(final ByteBuffer p_buffer) {
        MessagesDataStructureImExporter exporter = new MessagesDataStructureImExporter(p_buffer);

        exporter.exportObject(m_taskScript);
    }

    @Override
    protected final void readPayload(final ByteBuffer p_buffer) {
        MessagesDataStructureImExporter importer = new MessagesDataStructureImExporter(p_buffer);

        m_taskScript = new TaskScript();
        importer.importObject(m_taskScript);
    }

    @Override
    protected final int getPayloadLength() {
        return m_taskScript.sizeofObject();
    }
}