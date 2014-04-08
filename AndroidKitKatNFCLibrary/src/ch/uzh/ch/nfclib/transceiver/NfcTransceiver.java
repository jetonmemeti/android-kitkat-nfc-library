package ch.uzh.ch.nfclib.transceiver;

import java.io.IOException;

import android.app.Activity;
import android.util.Log;
import ch.uzh.ch.nfclib.INfcEventListener;
import ch.uzh.ch.nfclib.NfcEvent;
import ch.uzh.ch.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.ch.nfclib.exceptions.NoNfcException;
import ch.uzh.ch.nfclib.messages.NfcMessage;
import ch.uzh.ch.nfclib.util.Constants;

//TODO: javadoc
public abstract class NfcTransceiver {
	private static final String TAG = "NfcTransceiver";
	
	private boolean enabled = false;
	private INfcEventListener nfcEventListener;
	
	public NfcTransceiver(INfcEventListener nfcEventListener) {
		this.nfcEventListener = nfcEventListener;
	}
	
	public abstract void enable(Activity activity) throws NoNfcException, NfcNotEnabledException;
	
	public abstract void disable(Activity activity);
	
	protected abstract void initNfc() throws IOException;
	
	public abstract byte[] transceive(byte[] bytes) throws IllegalArgumentException;
	
//	public abstract void reset();
//	
//	public abstract void processResponse();
	
	/**
	 * To initiate a NFC connection, the NFC reader sends a "SELECT AID" APDU to
	 * the emulated card. Android OS then instantiates the service which has
	 * this AID registered (see apduservice.xml).
	 */
	protected byte[] createSelectAidApdu() {
		byte[] temp = new byte[Constants.CLA_INS_P1_P2.length + Constants.AID_MBPS.length + 2];
		System.arraycopy(Constants.CLA_INS_P1_P2, 0, temp, 0, Constants.CLA_INS_P1_P2.length);
		temp[4] = (byte) Constants.AID_MBPS.length;
		System.arraycopy(Constants.AID_MBPS, 0, temp, 5, Constants.AID_MBPS.length);
		temp[temp.length - 1] = 3;
		return temp;
	}
	
	protected void handleAidApduResponse(byte[] response) {
		NfcMessage msg = new NfcMessage(response);
		if (msg.getStatus() == NfcMessage.AID_SELECTED) {
			//HostApduService recognized the AID
			nfcEventListener.notify(NfcEvent.NFC_INITIALIZED, null);
		} else {
			Log.d(TAG, "apdu response is not as expected!");
			nfcEventListener.notify(NfcEvent.NFC_INIT_FAILED, null);
		}
	}
	
	protected boolean isEnabled() {
		return enabled;
	}
	
	protected void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	protected INfcEventListener getNfcEventListener() {
		return nfcEventListener;
	}
	
}
