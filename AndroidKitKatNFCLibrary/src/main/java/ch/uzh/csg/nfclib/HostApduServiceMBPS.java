package ch.uzh.csg.nfclib;

import java.util.Arrays;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
final public class HostApduServiceMBPS extends HostApduService {

	private static final String TAG = "ch.uzh.csg.nfclib.HostApduServiceMBPS";

	private static NfcResponder nfcResponder;
	
	public static void init(final NfcResponder nfcResponder2) {
		nfcResponder = nfcResponder2;
	}

	@Override
	public byte[] processCommandApdu(final byte[] bytes, final Bundle extras) {
		final long start = System.currentTimeMillis();
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		final byte[] retVal = nfcResponder.processIncomingData(bytes);
		Log.d(TAG, "about to return "+Arrays.toString(retVal));
		Log.e(TAG, "time to respond: "+(System.currentTimeMillis() - start));
		return retVal;
	}

	@Override
	public void onDeactivated(final int reason) {
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return;
		}
		nfcResponder.onDeactivated(reason);
	}
}
