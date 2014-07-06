package ch.uzh.csg.nfclib;

import java.io.IOException;

import android.app.Activity;

/**
 * The implementation of this interface must handle the initialization and the
 * near field communication.
 * 
 * @author Jeton
 * 
 */
public interface INfcTransceiver {

	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An unexpected error occured while writing over NFC.";

	/**
	 * Binds the NFC controller to the given activity.
	 * 
	 * @throws NfcLibException
	 */
	public void enable(Activity activity) throws NfcLibException;

	/**
	 * Removes the binding between the NFC controller and the given activity.
	 * 
	 * @throws IOException
	 */
	public void disable(Activity activity);

	/**
	 * Checks if the the transceiver is enabled, i.e., if enable has been
	 * called.
	 * 
	 * @return true if it is enabled, false otherwise
	 */
	public boolean isEnabled();

	/**
	 * Writes a {@link NfcMessage} to the NFC partner.
	 * 
	 * @param input
	 *            the {@link NfcMessage} to be send
	 * @return the response as {@link NfcMessage}
	 * @throws IOException
	 *             if there is an I/O error
	 */
	public NfcMessage write(NfcMessage input) throws IOException;

	/**
	 * Returns the maximum transceive (send/receive) length.
	 */
	public int maxLen();

}
