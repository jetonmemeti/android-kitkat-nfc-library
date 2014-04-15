package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.exceptions.TransceiveException;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

//TODO: javadoc
public class InternalNfcTransceiver extends NfcTransceiver implements ReaderCallback {
	
	private static final String TAG = "InternalNfcTransceiver";
	
	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	private static final int MAX_WRITE_LENGTH = 245;
	
	private NfcAdapter nfcAdapter;
	private CustomIsoDep isoDep;
	
	private NfcMessageSplitter messageSplitter;
	private NfcMessageReassembler messageReassembler;
	
	public InternalNfcTransceiver(NfcEventHandler eventHandler) {
		super(eventHandler);
		messageSplitter = new NfcMessageSplitter(MAX_WRITE_LENGTH);
		messageReassembler = new NfcMessageReassembler();
	}

	@Override
	public void enable(Activity activity) throws NoNfcException, NfcNotEnabledException {
		nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
		if (nfcAdapter == null)
			throw new NoNfcException();
		
		if (!nfcAdapter.isEnabled())
			throw new NfcNotEnabledException();
		
		/*
		 * This will send a check presence message that needs to be handled by
		 * HostApduService! You may run into the issue with other cards as shown
		 * here: http://code.google.com/p/android/issues/detail?id=58773. For
		 * more details on the ISO spec see
		 * http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816
		 * -4_6_basic_interindustry_commands.aspx#chap6_1
		 */
		nfcAdapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, new Bundle());
		setEnabled(true);
		isoDep = new CustomIsoDep();
	}

	@Override
	public void disable(Activity activity) {
		if (isEnabled() && nfcAdapter != null) {
			cancel();
			nfcAdapter.disableReaderMode(activity);
			setEnabled(false);
		}
	}

	public void onTagDiscovered(Tag tag) {
		isoDep.init(tag);
		try {
			isoDep.connect();
			initNfc();
		} catch (IOException e) {
			getNfcEventHandler().handleMessage(NfcEvent.NFC_INIT_FAILED, null);
			Log.e(TAG, "Could not connnect isodep", e);
		}
	}

	@Override
	protected void initNfc() throws IOException {
		byte[] response = isoDep.transceive(createSelectAidApdu());
		handleAidApduResponse(response);
	}

	@Override
	public synchronized byte[] transceive(byte[] bytes) throws IllegalArgumentException, TransceiveException {
		messageReassembler.clear();
		ArrayList<NfcMessage> list = messageSplitter.getFragments(bytes);
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + list.size() + " fragments");
		
		for (NfcMessage nfcMessage : list) {
			NfcMessage response = write(nfcMessage);
			if (response == null || response.getData().length < NfcMessage.HEADER_LENGTH) {
				Log.e(TAG, "error occured while transceiving a message");
				throw new TransceiveException("error occured while transceiving a message");
			}
			
			if (response.getStatus() == NfcMessage.ERROR) {
				getNfcEventHandler().handleMessage(NfcEvent.NFC_ERROR_REPORTED, null);
				return null;
			}
			
			boolean sendNext = (response.getStatus() & NfcMessage.GET_NEXT_FRAGMENT) == NfcMessage.GET_NEXT_FRAGMENT;
			
			if (sendNext) {
				continue;
			} else {
				messageReassembler.handleReassembly(response);
			}
		}
		
		return messageReassembler.getData();
	}
	
	private NfcMessage write(NfcMessage nfcMessage) throws IllegalArgumentException, TransceiveException {
		if (isEnabled() && isoDep.isConnected()) {
			if (nfcMessage == null) {
				throw new IllegalArgumentException("The message is null");
			} else if (nfcMessage.getData().length > isoDep.getMaxTransceiveLength()) {
				throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + isoDep.getMaxTransceiveLength() + " bytes.");
			} else if (nfcMessage.getData().length > MAX_WRITE_LENGTH) {
				throw new IllegalArgumentException("The argument length exceeds the maximum capacity of " + MAX_WRITE_LENGTH + " bytes.");
			}
			
			try {
				return new NfcMessage(isoDep.transceive(nfcMessage.getData()));
			} catch (IOException e) {
				Log.d(TAG, "could not write message", e);
				throw new TransceiveException("could not write message: "+e.getMessage());
			}
		} else {
			Log.d(TAG, "could not write message, isodep is no longer connected");
			throw new TransceiveException("could not write message, isodep is no longer connected");
		}
	}

//	@Override
//	public void reset() {
//		// TODO Auto-generated method stub
//		
//	}
//
//	@Override
//	public void processResponse() {
//		// TODO Auto-generated method stub
//		
//	}

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
