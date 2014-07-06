package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcInitiator.TagDiscoveredHandler;
import ch.uzh.csg.nfclib.NfcLibException;
import ch.uzh.csg.nfclib.events.INfcEventHandler;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.messages.NfcMessage.Type;
import ch.uzh.csg.nfclib.utils.Config;

/**
 * This class handles the initialization and the message exchange over NFC for
 * the internal or build-in NFC controller of the Android smartphone or tablet.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class InternalNfcTransceiver implements ReaderCallback, INfcTransceiver {
	
	private static final String TAG = "ch.uzh.csg.nfclib.transceiver.InternalNfcTransceiver";

	/*
	 * NXP chip supports max 255 bytes (problems might arise sometimes if
	 * sending exactly 255 bytes)
	 */
	private static final int MAX_WRITE_LENGTH = 245;

	private final INfcEventHandler eventHandler;
	private final TagDiscoveredHandler nfcInit;

	private NfcAdapter nfcAdapter;
	private IsoDep isoDep;
	private int maxLen = Integer.MAX_VALUE;
	/*
	 * not sure if this is called from different threads. Make it volatile just
	 * in case.
	 */
	private volatile boolean enabled = false;

	/**
	 * Creates a new instance.
	 * 
	 * @param eventHandler
	 *            the {@link INfcEventHandler} (may not be null)
	 * @param nfcInit
	 *            the {@link TagDiscoveredHandler} which is notified as soon as
	 *            a NFC connection is established (may not be null)
	 */
	public InternalNfcTransceiver(INfcEventHandler eventHandler, TagDiscoveredHandler nfcInit) {
		this.eventHandler = eventHandler;
		this.nfcInit = nfcInit;
	}

	@Override
	public void enable(Activity activity) throws NfcLibException {
		nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(activity);
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
		//this causes a huge delay for a second reconnect! don't use this!
		//options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);

		nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, options);
		enabled = true;
	}

	@Override
	public void disable(Activity activity) {
		if (isoDep != null && isoDep.isConnected()) {
			try {
				isoDep.close();
			} catch (IOException e) {
				if (Config.DEBUG)
					Log.d(TAG, "could not close isodep", e);
			}
		}
		if (nfcAdapter != null && enabled) {
			nfcAdapter.disableReaderMode(activity);
		}
		enabled = false;
	}

	@Override
	public boolean isEnabled() {
		if (nfcAdapter == null) {
			return false;
		}
		if (!enabled) {
			return false;
		}
		return nfcAdapter.isEnabled();
	}

	@Override
	public void onTagDiscovered(Tag tag) {
		if (Config.DEBUG)
			Log.d(TAG, "tag discovered: " + tag);
		
		isoDep = IsoDep.get(tag);
		try {
			isoDep.connect();
			nfcInit.tagDiscovered();
		} catch (IOException e) {
			if (Config.DEBUG)
				Log.e(TAG, "Could not connnect isodep: ", e);
			
			eventHandler.handleMessage(NfcEvent.INIT_FAILED, null);
		}
	}

	@Override
	public int maxLen() {
		/*
		 * NXP chip supports max 255 bytes (problems might arise sometimes if
		 * sending exactly 255 bytes)
		 */
		// TODO: NXP has limit of 245, broadcom not, make distincion
		return MAX_WRITE_LENGTH;
	}

	@Override
	public NfcMessage write(NfcMessage input) throws IOException {
		if (!isEnabled()) {
			if (Config.DEBUG)
				Log.d(TAG, "could not write message, isodep is not enabled");
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
			return new NfcMessage(Type.EMPTY).sequenceNumber(input).error();
		}

		if (!isoDep.isConnected()) {
			if (Config.DEBUG)
				Log.d(TAG, "could not write message, isodep is not or no longer connected");
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NFCTRANSCEIVER_NOT_CONNECTED);
			return new NfcMessage(Type.EMPTY).sequenceNumber(input).error();
		}

		byte[] bytes = input.bytes();
		if (bytes.length > isoDep.getMaxTransceiveLength()) {
			throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + isoDep.getMaxTransceiveLength() + " bytes.");
		} else if (bytes.length > maxLen) {
			throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + maxLen + " bytes.");
		}
		
		return new NfcMessage(isoDep.transceive(bytes));
	}

}
