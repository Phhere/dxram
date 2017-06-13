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

package de.hhu.bsinfo.dxram.lock.messages;

import de.hhu.bsinfo.ethnet.core.AbstractResponse;

/**
 * Response to a LockRequest
 *
 * @author Florian Klein, florian.klein@hhu.de, 09.03.2012
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 05.01.2016
 */
public class LockResponse extends AbstractResponse {

    /**
     * Creates an instance of LockResponse as a receiver.
     */
    public LockResponse() {
        super();
    }

    /**
     * Creates an instance of LockResponse as a sender.
     *
     * @param p_request
     *     Corresponding request to this response.
     * @param p_statusCode
     *     Status code for locking the chunk.
     */
    public LockResponse(final LockRequest p_request, final byte p_statusCode) {
        super(p_request, LockMessages.SUBTYPE_LOCK_RESPONSE);

        setStatusCode(p_statusCode);
    }

}
