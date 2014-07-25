package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;

import ch.uzh.csg.nfclib.NfcLibException;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import android.app.Activity;

/**
 * The implementation of this interface must handle the initialization and the
 * near field communication.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public interface INfcTransceiver {

	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An unexpected error occured while writing over NFC.";

	/**
	 * Turns on the NFC controller, i.e., binds the NFC controller to the given
	 * activity.
	 * 
	 * @throws NfcLibException
	 */
	public void turnOn(Activity activity) throws NfcLibException;

	/**
	 * Turns off the NFC controller, i.e., removes the binding between the NFC
	 * controller and the given activity.
	 * 
	 * @throws IOException
	 */
	public void turnOff(Activity activity);

	/**
	 * Enables the NFC message exchange (as soon as a NFC device is in range,
	 * the init handshake is done). The {@link INfcTransceiver} has to be turned
	 * on before enabling it.
	 */
	public void enable();
	
	/**
	 * Disables the NFC transceiver in order to avoid restarting the protocol.
	 * (The Samsung Galaxy Note 3 for example restarts the protocol by calling
	 * an init internally. This results in sequential payments if the two NFC
	 * devices stay tapped together).
	 * 
	 * This affects only the next init. The current message exchange is not
	 * affected and will be executed according the the upper layer protocol.
	 */
	public void disable();
	
	/**
	 * Checks if the the transceiver is enabled, i.e., if it is turned on.
	 * Attention: it does not return if enable or disable have been called!
	 * 
	 * @return true if it is turned on, false otherwise
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
