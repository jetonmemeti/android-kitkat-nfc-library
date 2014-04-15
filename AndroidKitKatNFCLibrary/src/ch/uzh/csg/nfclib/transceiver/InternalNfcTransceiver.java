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
	
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String ISODEP_NOT_CONNECTED = "could not write message, IsoDep is no longer connected";
	public static final String UNEXPECTED_ERROR = "Unexpected error occured while transceiving a message.";
	
	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	protected static final int MAX_WRITE_LENGTH = 245;

	private NfcAdapter nfcAdapter;
	private CustomIsoDep isoDep;
	
	private NfcMessageSplitter messageSplitter;
	private NfcMessageReassembler messageReassembler;
	
	public InternalNfcTransceiver(NfcEventHandler eventHandler) {
		super(eventHandler);
		messageSplitter = new NfcMessageSplitter(MAX_WRITE_LENGTH);
		messageReassembler = new NfcMessageReassembler();
		isoDep = new CustomIsoDep();
	}
	
	/*
	 * This constructor is only for test purposes, in order to mock the IsoDep.
	 * For productive use please use the constructor above, otherwise the NFC
	 * will not work.
	 */
	protected InternalNfcTransceiver(NfcEventHandler eventHandler, CustomIsoDep isoDep) {
		this(eventHandler);
		this.isoDep = isoDep;
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
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException(NULL_ARGUMENT);
		
		messageReassembler.clear();
		ArrayList<NfcMessage> list = messageSplitter.getFragments(bytes);
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + list.size() + " fragments");
		
		//TODO: refactor! duplicated code!
		//TODO: delete logs!
		for (NfcMessage nfcMessage : list) {
			NfcMessage response = write(nfcMessage);
			if (response.getData().length < NfcMessage.HEADER_LENGTH) {
				Log.e(TAG, "error occured while transceiving a message");
				throw new TransceiveException(UNEXPECTED_ERROR);
			}
			
			if (response.getStatus() == NfcMessage.ERROR) {
				Log.d(TAG, "nfc error reported - returning null");
				getNfcEventHandler().handleMessage(NfcEvent.NFC_ERROR_REPORTED, null);
				return null;
			}
			
			boolean sendNext = (response.getStatus() & NfcMessage.GET_NEXT_FRAGMENT) == NfcMessage.GET_NEXT_FRAGMENT;
			Log.d(TAG, "status: "+response.getStatus());
			
			if (sendNext) {
				Log.d(TAG, "sending next fragment");
				continue;
			} else {
				Log.d(TAG, "else branch");
				messageReassembler.handleReassembly(response);
				boolean hasMore = (response.getStatus() & NfcMessage.HAS_MORE_FRAGMENTS) == NfcMessage.HAS_MORE_FRAGMENTS;
				while (hasMore) {
					Log.d(TAG, "has more fragments to return");
					response = write(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x00, null));
					
					if (response.getData().length < NfcMessage.HEADER_LENGTH) {
						Log.e(TAG, "error occured while transceiving a message");
						throw new TransceiveException(UNEXPECTED_ERROR);
					}
					
					if (response.getStatus() == NfcMessage.ERROR) {
						Log.d(TAG, "nfc error send!");
						getNfcEventHandler().handleMessage(NfcEvent.NFC_ERROR_REPORTED, null);
						return null;
					}
					
					messageReassembler.handleReassembly(response);
					
					hasMore = (response.getStatus() & NfcMessage.HAS_MORE_FRAGMENTS) == NfcMessage.HAS_MORE_FRAGMENTS;
				}
			}
		}
		
		return messageReassembler.getData();
	}
	
	private NfcMessage write(NfcMessage nfcMessage) throws IllegalArgumentException, TransceiveException {
		if (isEnabled() && isoDep.isConnected()) {
			if (nfcMessage == null) {
				throw new IllegalArgumentException(NULL_ARGUMENT);
			} else if (nfcMessage.getData().length > isoDep.getMaxTransceiveLength()) {
				throw new IllegalArgumentException("The message length exceeds the maximum capacity of " + isoDep.getMaxTransceiveLength() + " bytes.");
			} else if (nfcMessage.getData().length > MAX_WRITE_LENGTH) {
				throw new IllegalArgumentException("The argument length exceeds the maximum capacity of " + MAX_WRITE_LENGTH + " bytes.");
			}
			
			try {
				return new NfcMessage(isoDep.transceive(nfcMessage.getData()));
			} catch (IOException e) {
				Log.d(TAG, "could not write message", e);
				throw new TransceiveException("Could not write message: "+e.getMessage());
			}
		} else {
			Log.d(TAG, "could not write message, isodep is no longer connected");
			throw new TransceiveException(ISODEP_NOT_CONNECTED);
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
