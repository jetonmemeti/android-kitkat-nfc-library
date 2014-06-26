package ch.uzh.csg.nfclib;

import java.util.Arrays;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
public class CustomHostApduService2 extends HostApduService {

	public static final String TAG = "##NFC## CustomHostApduService2";

	private static NfcResponder nfcResponder;
	
	public class SendLater {	
		public void sendLater(byte[] data) {
			if(data == null) {
				throw new IllegalArgumentException("cannot be null");
			}
			Log.d(TAG, "about to send later "+Arrays.toString(data));
			NfcMessage first = NfcResponder.fragmentData(data);
			byte[] me = NfcResponder.prepareWrite(first);
			CustomHostApduService2.this.sendResponseApdu(me);
		}
	};
	
	private final SendLater sendLater = new SendLater();

	public static void init(NfcResponder nfcResponder2) {
		nfcResponder = nfcResponder2;
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		byte[] me = nfcResponder.processCommandApdu(bytes, sendLater);
		Log.d(TAG, "about to return "+Arrays.toString(me));
		return me;
	}

	@Override
	public void onDeactivated(int reason) {
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return;
		}
		nfcResponder.onDeactivated(reason);
	}
}
