package ch.uzh.ch.nfclib;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.ch.nfclib.messages.NfcMessage;
import ch.uzh.ch.nfclib.util.Constants;

public class CustomHostApduService extends HostApduService {
	
	public static final String TAG = "CustomHostApduService";
	
	private static INfcEventListener nfcEventListener;
	private static Activity hostActivity;
	
	public static void init(Activity activity, INfcEventListener nfcEventListener) {
		CustomHostApduService.nfcEventListener = nfcEventListener;
		hostActivity = activity;
	}
	
	/*
	 * The empty constructor is needed by android to instantiate the service.
	 * Handler and BuyerRole are therefore static.
	 */
	public CustomHostApduService() {
		if (hostActivity == null) {
			Log.d(TAG, "activity has not been set yet or user is not in the given activity");
			nfcEventListener.notify(NfcEvent.NFC_INIT_FAILED, null);
		}
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (hostActivity == null) {
			Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
			//TODO: forward error to nfc event listener
			//TODO: return abort
			return null;
		}
		
		if (selectAidApdu(bytes)) {
			Log.d(TAG, "AID selected");
			//TODO: decide based on time if to resume or restart!
			return new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x01, new byte[]{NfcMessage.START_PROTOCOL}).getData();
		}
		
		
		// TODO Auto-generated method stub
		return null;
	}
	
	private boolean selectAidApdu(byte[] bytes) {
		return bytes.length >= 2 && bytes[0] == Constants.CLA_INS_P1_P2[0] && bytes[1] == Constants.CLA_INS_P1_P2[1];
	}

	private byte[] getResponse() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onDeactivated(int reason) {
		Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "("+reason+")");
	}

}
