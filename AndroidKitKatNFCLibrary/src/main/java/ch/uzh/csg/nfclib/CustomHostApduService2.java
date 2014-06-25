package ch.uzh.csg.nfclib;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
public class CustomHostApduService2 extends HostApduService {

	public static final String TAG = "##NFC## CustomHostApduService2";

	private static NfcResponder nfcResponder;
	
	private final SendLater sendLater = new SendLater() {	
		@Override
		public void sendLater(byte[] data) {
			if(data == null) {
				throw new IllegalArgumentException("cannot be null");
			}
			NfcMessage first = NfcResponder.fragmentData(data);
			byte[] me = NfcResponder.prepareWrite(first);
			CustomHostApduService2.this.sendResponseApdu(me);
		}
	};

	public static void init(NfcResponder nfcResponder2) {
		nfcResponder = nfcResponder2;
		
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		return nfcResponder.processCommandApdu(bytes, sendLater);
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
