package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.Arrays;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventInterface;

//TODO: javadoc
public class InternalNfcTransceiver extends NfcTransceiver implements ReaderCallback {
	private static final String TAG = "##NFC## InternalNfcTransceiver";
	
	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	protected static final int MAX_WRITE_LENGTH = 245;
	
	private NfcAdapter nfcAdapter;
	private CustomIsoDep isoDep;
	
	public InternalNfcTransceiver(NfcEventInterface eventHandler, long userId) {
		super(eventHandler, MAX_WRITE_LENGTH, userId);
		isoDep = new CustomIsoDep();
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the IsoDep.
	 * For productive use please use the constructor above, otherwise the NFC
	 * will not work.
	 */
	protected InternalNfcTransceiver(NfcEventInterface eventHandler, long userId, CustomIsoDep isoDep) {
		this(eventHandler, userId);
		this.isoDep = isoDep;
	}

	@Override
	public void enable(Activity activity) throws NfcLibException {
		nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
		if (nfcAdapter == null)
			throw new NfcLibException("NFC Adapter is null");
		
		if (!nfcAdapter.isEnabled())
			throw new NfcLibException("NFC is not enabled");
		
		/*
		 * Based on the reported issue in
		 * https://code.google.com/p/android/issues/detail?id=58773, there is a
		 * failure in the Android NFC protocol. The IsoDep might transceive a
		 * READ BINARY, if the communication with the tag (or HCE) has been idle
		 * for a given time (125ms as mentioned on the issue report). This idle
		 * time can be changed with the EXTRA_READER_PRESENCE_CHECK_DELAY
		 * option.
		 */
		Bundle options = new Bundle();
		options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
		
		nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, options);
		setEnabled(true);
	}

	@Override
	public void disable(Activity activity) {
		if (isEnabled()) {
			cancel();
			if (nfcAdapter != null)
				nfcAdapter.disableReaderMode(activity);
			
			setEnabled(false);
		}
	}

	public void onTagDiscovered(Tag tag) {
		Log.d(TAG, "tag discovered "+tag);
		isoDep.init(tag);
		try {
			isoDep.connect();
			
			initNfc();
		} catch (IOException e) {
			Log.e(TAG, "Could not connnect isodep: ", e);
			getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
		}
	}

	@Override
	protected byte[] writeRaw(byte[] bytes) throws NfcLibException, IOException {
		if (!isEnabled()) {
			Log.d(TAG, "could not write message, isodep is not enabled");
			throw new NfcLibException(NfcEvent.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
		}
		
		if (isoDep.isConnected()) {
			if (bytes == null) {
				throw new IllegalArgumentException(NULL_ARGUMENT);
			} else if (bytes.length > isoDep.getMaxTransceiveLength()) {
				throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + isoDep.getMaxTransceiveLength() + " bytes.");
			} else if (bytes.length > MAX_WRITE_LENGTH) {
				throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + MAX_WRITE_LENGTH + " bytes.");
			}
			Log.d(TAG, "about to write: "+Arrays.toString(bytes));
			return isoDep.transceive(bytes);
		} else {
			Log.d(TAG, "could not write message, isodep is no longer connected");
			/*
			 * throw new IOException so that NfcTransceiver waits for a resume
			 */
			throw new IOException(NFCTRANSCEIVER_NOT_CONNECTED);
		}
	}
	
	private void cancel() {
		if (isoDep != null && isoDep.isConnected()) {
			try {
				isoDep.close();
			} catch (IOException e) {
				Log.d(TAG, "close isodep failed", e);
			}
		}
	}
	
}
