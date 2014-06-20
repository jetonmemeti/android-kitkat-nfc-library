package ch.uzh.csg.nfclib;

import java.io.IOException;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcTransceiver.NfcInit;

//TODO: javadoc
public class InternalNfcTransceiver implements ReaderCallback, NfcTransceiverImpl {
	private static final String TAG = "##NFC## InternalNfcTransceiver";

	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	private static final int MAX_WRITE_LENGTH = 245;

	private final NfcEvent eventHandler;
	private final NfcInit nfcInit;

	private NfcAdapter nfcAdapter;
	private IsoDep isoDep;
	private int maxLen = Integer.MAX_VALUE;
	private NfcMessage lastNfcMessageSent;

	public InternalNfcTransceiver(NfcEvent eventHandler, NfcInit nfcInit) {
		this.eventHandler = eventHandler;
		this.nfcInit = nfcInit;
	}

	@Override
	public void enable(Activity activity) throws NfcLibException {
		disable(activity);
		Log.d(TAG, "enable NFC");
		nfcAdapter = createAdapter(activity);
		if (nfcAdapter == null) {
			throw new NfcLibException("NFC Adapter is null");
		}

		if (!nfcAdapter.isEnabled()) {
			throw new NfcLibException("NFC is not enabled");
		}

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

		nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A
		        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, options);
	}

	@Override
	public void disable(Activity activity) {
		Log.d(TAG, "disable NFC");
		if (isoDep != null && isoDep.isConnected()) {
			try {
				isoDep.close();
			} catch (IOException e) {
				Log.d(TAG, "tried close!");
			}
		}
		if (nfcAdapter != null && nfcAdapter.isEnabled()) {
			nfcAdapter.disableReaderMode(activity);
		}
	}

	@Override
	public boolean isEnabled() {
		if (nfcAdapter == null) {
			return false;
		} else {
			return nfcAdapter.isEnabled();
		}
	}

	@Override
	public void onTagDiscovered(Tag tag) {
		Log.d(TAG, "tag discovered " + tag);
		isoDep = IsoDep.get(tag);
		try {
			isoDep.connect();
			nfcInit.init();
		} catch (IOException e) {
			Log.e(TAG, "Could not connnect isodep: ", e);
			eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
		}
	}

	@Override
	public int maxLen() {
		// TODO: NXP has limit of 245, broadcom not, make distincion
		return MAX_WRITE_LENGTH;
	}

	@Override
	public NfcMessage write(NfcMessage input) throws IOException {
		// sequenz number are handled here
		input.sequenceNumber(lastNfcMessageSent);
		lastNfcMessageSent = input;

		if (!isEnabled()) {
			Log.d(TAG, "could not write message, isodep is not enabled");
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
			return new NfcMessage().sequenceNumber(lastNfcMessageSent).error();
		}

		if (!isoDep.isConnected()) {
			Log.d(TAG, "could not write message, isodep is no longer connected");
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
			return new NfcMessage().sequenceNumber(lastNfcMessageSent).error();
		}

		byte[] bytes = input.bytes();
		if (bytes.length > isoDep.getMaxTransceiveLength()) {
			throw new IllegalArgumentException("The message length exceeds the maximum capacity of "
			        + isoDep.getMaxTransceiveLength() + " bytes.");
		} else if (bytes.length > maxLen) {
			throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + maxLen
			        + " bytes.");
		}
		Log.d(TAG, "about to write: " + Arrays.toString(bytes));
		return new NfcMessage().bytes(isoDep.transceive(bytes));
	}

	/**
	 * Create an NFC adapter, if NFC is enabled, return the adapter, otherwise
	 * null and open up NFC settings.
	 * 
	 * @param context
	 * @return
	 */
	private static NfcAdapter createAdapter(final Activity activity) {
		NfcAdapter nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(activity);

		if (!nfcAdapter.isEnabled()) {
			AlertDialog.Builder alertbox = new AlertDialog.Builder(activity.getApplicationContext());
			alertbox.setTitle("Info");
			alertbox.setMessage("Enable NFC");
			alertbox.setPositiveButton("Turn On", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
						Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
						activity.getApplicationContext().startActivity(intent);
					} else {
						Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
						activity.getApplicationContext().startActivity(intent);
					}
				}
			});
			alertbox.setNegativeButton("Close", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {

				}
			});
			alertbox.show();
			return null;
		}
		return nfcAdapter;
	}

}
