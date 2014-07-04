package ch.uzh.csg.nfclib;

/**
 * The implementation of this interface must implement what has to be done on
 * the given event {@link Type}.
 * 
 * @author Jeton
 * 
 */
public interface NfcEvent {
	
	/**
	 * This represents all event types, which are or can be send during a NFC
	 * communication.
	 * 
	 * INIT_FAILED --> no data
	 * INITIALIZED --> the user id of the communication partner
	 * FATAL_ERROR --> the error code
	 * MESSAGE_RECEIVED --> the received serialized message
	 * CONNECTION_LOST --> no data
	 */
	public enum Type {
		INIT_FAILED, //exit condition
		INITIALIZED, //may continue
		FATAL_ERROR, //exit condition
		MESSAGE_RECEIVED, //exit condition
		CONNECTION_LOST, //exit condition
	}

	/**
	 * Handles and takes appropriate steps on any given event {@link Type}.
	 * Based on the type, further data may be provided in the object parameter.
	 * 
	 * @param event
	 *            the given {@link Type}
	 * @param object
	 *            additional data (based on the {@link Type}) or null
	 */
	public abstract void handleMessage(Type event, Object object);

}
