package ch.uzh.csg.nfclib;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.util.Constants;

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
		}
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (hostActivity == null) {
			Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
			//TODO: add sequence number?
			return new NfcMessage(NfcMessage.ERROR, (byte) (0x00), null).getData();
		}
		
		if (selectAidApdu(bytes)) {
			Log.d(TAG, "AID selected");
			//TODO: decide based on time if to resume or restart!
			return new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x01, new byte[]{NfcMessage.START_PROTOCOL}).getData();
		}
		
		return getResponse(bytes);
	}
	
	private boolean selectAidApdu(byte[] bytes) {
		return bytes.length >= 2 && bytes[0] == Constants.CLA_INS_P1_P2[0] && bytes[1] == Constants.CLA_INS_P1_P2[1];
	}

	private byte[] getResponse(byte[] bytes) {
		
		//TODO: fragmentation
		//TODO: implement logic! i.e. what to return
		return new NfcMessage(NfcMessage.START_PROTOCOL, (byte) 0x00, null).getData();
	}

	@Override
	public void onDeactivated(int reason) {
		Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "("+reason+")");
	}

}
