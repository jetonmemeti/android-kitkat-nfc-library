package ch.uzh.csg.nfclib;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
public class CustomHostApduService2 extends HostApduService {

	public static final String TAG = "##NFC## CustomHostApduService2";

	private static CustomHostApduService customHostApduService;

	public static void init(CustomHostApduService customHostApduService2) {
		customHostApduService = customHostApduService2;
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (customHostApduService == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		return customHostApduService.processCommandApdu(bytes);
	}

	@Override
	public void onDeactivated(int reason) {
		if (customHostApduService == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return;
		}
		customHostApduService.onDeactivated(reason);
	}

}
