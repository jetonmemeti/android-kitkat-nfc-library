package ch.uzh.csg.nfclib.events;


/**
 * The implementation of this interface must implement what has to be done on
 * the given {@link NfcEvent}.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public interface INfcEventHandler {
	
	/**
	 * Handles and takes appropriate steps on any given {@link NfcEvent}.
	 * Based on the type, further data may be provided in the object parameter.
	 * 
	 * @param event
	 *            the given {@link NfcEvent}
	 * @param object
	 *            additional data or null
	 */
	public abstract void handleMessage(NfcEvent event, Object object);

}
