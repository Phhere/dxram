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

import de.hhu.bsinfo.ethnet.AbstractResponse;

/**
 * Reponse to the execute task script request with status code.
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 22.04.2016
 */
public class ExecuteTaskScriptResponse extends AbstractResponse {
    /**
     * Creates an instance of ExecuteTaskScriptResponse.
     * This constructor is used when receiving this message.
     */
    public ExecuteTaskScriptResponse() {
        super();
    }

    /**
     * Creates an instance of ExecuteTaskScriptResponse.
     * This constructor is used when sending this message.
     *
     * @param p_request
     *     the request to respond to
     */
    public ExecuteTaskScriptResponse(final ExecuteTaskScriptRequest p_request) {
        super(p_request, MasterSlaveMessages.SUBTYPE_EXECUTE_TASK_RESPONSE);
    }
}