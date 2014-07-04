package ch.uzh.csg.nfclib;

/**
 * This exception is thrown, when a fatal NFC error occurs (e.g., NFC is not
 * enabled).
 * 
 * @author Jeton
 * 
 */
public class NfcLibException extends Exception {

	private static final long serialVersionUID = -8780373763513187959L;

	public NfcLibException(String msg) {
		super(msg);
	}

}
