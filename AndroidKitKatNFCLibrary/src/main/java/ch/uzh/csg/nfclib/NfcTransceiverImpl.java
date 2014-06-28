package ch.uzh.csg.nfclib;

import java.io.IOException;

import android.app.Activity;

public interface NfcTransceiverImpl {
	
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";

	public void enable(Activity activity) throws NfcLibException;

	public void disable(Activity activity) throws IOException;
	
	public boolean isEnabled();

	public NfcMessage write(NfcMessage input) throws NfcLibException, IOException;

	public int maxLen();

}
