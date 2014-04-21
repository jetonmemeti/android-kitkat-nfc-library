package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.exceptions.TransceiveException;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.util.Constants;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

//TODO: javadoc
public abstract class NfcTransceiver {
	private static final String TAG = "NfcTransceiver";
	
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String ISODEP_NOT_CONNECTED = "could not write message, IsoDep is no longer connected";
	public static final String UNEXPECTED_ERROR = "Unexpected error occured while transceiving a message.";
	
	/*
	 * 1 means that a message will be retransmitted at most 1 time if the first
	 * write failed for some reason (i.e., we got not the sequence number we
	 * expected)
	 */
	protected static final int MAX_RETRANSMITS = 1;
	
	private boolean enabled = false;
	private NfcEventHandler eventHandler;
	
	private NfcMessageSplitter messageSplitter;
	private NfcMessageReassembler messageReassembler;
	
	private int lastSqNrReceived;
	private int lastSqNrSent;
	
	public NfcTransceiver(NfcEventHandler eventHandler, int maxWriteLength) {
		this.eventHandler = eventHandler;
		messageSplitter = new NfcMessageSplitter(maxWriteLength);
		messageReassembler = new NfcMessageReassembler();
	}
	
	public abstract void enable(Activity activity) throws NoNfcException, NfcNotEnabledException;
	
	public abstract void disable(Activity activity);
	
	protected abstract void initNfc() throws IOException;
	
	protected abstract NfcMessage writeRaw(NfcMessage nfcMessage) throws IllegalArgumentException, TransceiveException;
	
	public synchronized byte[] transceive(byte[] bytes) throws IllegalArgumentException, TransceiveException {
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException(NULL_ARGUMENT);
		
		messageReassembler.clear();
		lastSqNrReceived = lastSqNrSent = 0;
		
		ArrayList<NfcMessage> list = messageSplitter.getFragments(bytes);
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + list.size() + " fragments");
		
		for (NfcMessage nfcMessage : list) {
			NfcMessage response = write(nfcMessage, false);
			
			if (requestsNextFragment(response.getStatus())) {
				Log.i(TAG, "sending next fragment");
				continue;
			} else {
				response = retransmitIfRequested(nfcMessage, response);
				
				messageReassembler.handleReassembly(response);
				while (hasMoreFragments(response.getStatus())) {
					NfcMessage toSend = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x00, null);
					response = write(toSend, false);
					response = retransmitIfRequested(toSend, response);
					messageReassembler.handleReassembly(response);
				}
			}
		}
		
		return messageReassembler.getData();
	}
	
	private NfcMessage retransmitIfRequested(NfcMessage toSend, NfcMessage response) throws TransceiveException {
		boolean retransmissionSuccess = false;
		for (int i=0; i<=MAX_RETRANSMITS; i++) {
			if (retransmissionRequested(response.getStatus())) {
				Log.d(TAG, "retransmitting last nfc message since requested");
				response = write(toSend, true);
			} else {
				retransmissionSuccess = true;
			}
		}
		
		if (!retransmissionSuccess) {
			//Retransmitting message failed
			getNfcEventHandler().handleMessage(NfcEvent.NFC_RETRANSMIT_ERROR, null);
			throw new TransceiveException(UNEXPECTED_ERROR);
		}
		
		return response;
	}

	private NfcMessage write(NfcMessage nfcMessage, boolean isRetransmission) throws IllegalArgumentException, TransceiveException {
		if (!isRetransmission) {
			lastSqNrSent++;
		}
		
		nfcMessage.setSequenceNumber((byte) lastSqNrSent);
		
		NfcMessage response = writeRaw(nfcMessage);
		
		if (response.getStatus() == NfcMessage.ERROR) {
			Log.d(TAG, "nfc error reported - returning null");
			getNfcEventHandler().handleMessage(NfcEvent.NFC_ERROR_REPORTED, null);
			throw new TransceiveException(UNEXPECTED_ERROR);
		}
		
		boolean sendSuccess = false;
		for (int i=0; i<=MAX_RETRANSMITS; i++) {
			if (responseCorrupt(response) || invalidSequenceNumber(response.getSequenceNumber())) {
				Log.d(TAG, "requesting retransmission because answer was not as expected");
				
				if (invalidSequenceNumber(response.getSequenceNumber()) && retransmissionRequested(response.getStatus())) {
					//this is a deadlock, since both parties are requesting a retransmit
					getNfcEventHandler().handleMessage(NfcEvent.NFC_RETRANSMIT_ERROR, null);
					throw new TransceiveException(UNEXPECTED_ERROR);
				}
				
				lastSqNrSent++;
				response = writeRaw(new NfcMessage(NfcMessage.RETRANSMIT, (byte) lastSqNrSent, null));
			} else {
				sendSuccess = true;
				lastSqNrReceived++;
				break;
			}
		}
		
		if (!sendSuccess) {
			//Requesting retransmit failed
			getNfcEventHandler().handleMessage(NfcEvent.NFC_RETRANSMIT_ERROR, null);
			throw new TransceiveException(UNEXPECTED_ERROR);
		}
		
		return response;
	}
	
	private boolean responseCorrupt(NfcMessage response) {
		return response.getData() == null || response.getData().length < NfcMessage.HEADER_LENGTH; 
	}
	
	private boolean invalidSequenceNumber(byte sequenceNumber) {
		/*
		 * Because Java does not support unsigned bytes, we have to convert the
		 * (signed) byte to an integer in order to get values from 0 to 255
		 * (instead of -128 to 127)
		 */
		int temp = sequenceNumber & 0xFF;
		if (temp == 255) {
			if (lastSqNrReceived == 254)
				return false;
			else
				return true;
		}
		
		if (lastSqNrReceived == 255)
			lastSqNrReceived = 0;
		
		return temp != (lastSqNrReceived+1);
	}
	
	private boolean requestsNextFragment(byte status) {
		return (status & NfcMessage.GET_NEXT_FRAGMENT) == NfcMessage.GET_NEXT_FRAGMENT;
	}
	
	private boolean retransmissionRequested(byte status) {
		return (status & NfcMessage.RETRANSMIT) == NfcMessage.RETRANSMIT;
	}
	
	private boolean hasMoreFragments(byte status) {
		return (status & NfcMessage.HAS_MORE_FRAGMENTS) == NfcMessage.HAS_MORE_FRAGMENTS;
	}
	
	//TODO: what about?
//	public abstract void reset();
	
	//TODO: what about?
//	public abstract void processResponse();
	
	/**
	 * To initiate a NFC connection, the NFC reader sends a "SELECT AID" APDU to
	 * the emulated card. Android OS then instantiates the service which has
	 * this AID registered (see apduservice.xml).
	 */
	protected byte[] createSelectAidApdu() {
		byte[] temp = new byte[Constants.CLA_INS_P1_P2.length + Constants.AID_MBPS.length + 2];
		System.arraycopy(Constants.CLA_INS_P1_P2, 0, temp, 0, Constants.CLA_INS_P1_P2.length);
		temp[4] = (byte) Constants.AID_MBPS.length; //lc
		System.arraycopy(Constants.AID_MBPS, 0, temp, 5, Constants.AID_MBPS.length); //data //TODO: add user id
		temp[temp.length - 1] = 3; //le //TODO: do not hardcode 3, size of expected response
		
		return temp;
	}
	
	protected void handleAidApduResponse(byte[] response) {
		NfcMessage msg = new NfcMessage(response);
		if (msg.getStatus() == NfcMessage.AID_SELECTED) {
			//HostApduService recognized the AID
			eventHandler.handleMessage(NfcEvent.NFC_INITIALIZED, null);
		} else {
			Log.d(TAG, "apdu response is not as expected!");
			eventHandler.handleMessage(NfcEvent.NFC_INIT_FAILED, null);
		}
	}
	
	protected boolean isEnabled() {
		return enabled;
	}
	
	protected void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	protected NfcEventHandler getNfcEventHandler() {
		return eventHandler;
	}
	
}
