package ch.uzh.csg.nfclib;

/**
 * The implementation of this interface must process the incoming message and
 * return the appropriate response immediately or return null and pass the
 * response to the {@link ISendLater} instance.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public interface ITransceiveHandler {

	/**
	 * Handles an incoming message and provides the response directly (by
	 * returning the appropriate byte array) or as soon as it is ready (by
	 * returning null and passing the response to the {@link ISendLater}
	 * instance, e.g., when the response cannot be returned because the
	 * application if waiting for a user interaction).
	 * 
	 * @param message
	 *            the serialized message to be processed
	 * @param sendLater
	 *            the instance to pass the response to if it has not been
	 *            returned immediately
	 * @return the serialized response or null, if the response is returned via
	 *         the {@link ISendLater} instance
	 */
	public byte[] handleMessage(byte[] message, ISendLater sendLater);
	
}
