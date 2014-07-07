package ch.uzh.csg.nfclib;

import java.util.Arrays;

import ch.uzh.csg.nfclib.utils.Config;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

/**
 * This class handles incoming messages over NFC, which are passed to this
 * directly by the Android OS. It is to be registered in the Manifest.xml of
 * your application.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public final class HostApduServiceNfcLib extends HostApduService {
	/*
	 * DO NOT RENAME THIS CLASS!! It is referenced in other projects in xml
	 * files and will crash other projects if you do so!
	 */

	private static final String TAG = "ch.uzh.csg.nfclib.hce.HostApduServiceNfcLib";

	private static NfcResponder fNfcResponder;
	
	/**
	 * Sets the {@link NfcResponder} to handle incoming messages.
	 */
	public static void init(final NfcResponder nfcResponder) {
		fNfcResponder = nfcResponder;
	}

	@Override
	public byte[] processCommandApdu(final byte[] bytes, final Bundle extras) {
		if (fNfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		final byte[] retVal = fNfcResponder.processIncomingData(bytes);
		
		if (Config.DEBUG)
			Log.d(TAG, "about to return "+Arrays.toString(retVal));
		
		return retVal;
	}

	@Override
	public void onDeactivated(final int reason) {
		if (fNfcResponder != null) {
			fNfcResponder.onDeactivated(reason);
		}
	}
	
}
