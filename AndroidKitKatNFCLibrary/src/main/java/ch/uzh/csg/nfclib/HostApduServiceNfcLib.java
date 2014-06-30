package ch.uzh.csg.nfclib;

import java.util.Arrays;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc

// do not rename this class!! it is referenced in other projects in xml files and will crash other projects if you do so!
final public class HostApduServiceNfcLib extends HostApduService {

	private static final String TAG = "HostApduServiceMBPS";

	private static NfcResponder fNfcResponder;
	
	public static void init(final NfcResponder nfcResponder) {
		fNfcResponder = nfcResponder;
	}

	@Override
	public byte[] processCommandApdu(final byte[] bytes, final Bundle extras) {
		final long start = System.currentTimeMillis();
		if (fNfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		final byte[] retVal = fNfcResponder.processIncomingData(bytes);
		Log.d(TAG, "about to return "+Arrays.toString(retVal));
		Log.e(TAG, "time to respond: "+(System.currentTimeMillis() - start));
		return retVal;
	}

	@Override
	public void onDeactivated(final int reason) {
		if (fNfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return;
		}
		fNfcResponder.onDeactivated(reason);
	}
}
