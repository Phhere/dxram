
package de.hhu.bsinfo.dxram.failure.messages;

import de.hhu.bsinfo.menet.AbstractResponse;

/**
 * Response to a FailureRequest
 * @author Kevin Beineke
 *         18.10.2016
 */
public class FailureResponse extends AbstractResponse {

	// Constructors
	/**
	 * Creates an instance of FailureResponse
	 */
	public FailureResponse() {
		super();
	}

	/**
	 * Creates an instance of FailureResponse
	 * @param p_request
	 *            the corresponding FailureRequest
	 */
	public FailureResponse(final FailureRequest p_request) {
		super(p_request, FailureMessages.SUBTYPE_FAILURE_RESPONSE);
	}

}