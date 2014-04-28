package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.CommandApdu;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventHandler;
import ch.uzh.csg.nfclib.exceptions.NfcNotEnabledException;
import ch.uzh.csg.nfclib.exceptions.NoNfcException;
import ch.uzh.csg.nfclib.exceptions.TransceiveException;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.util.Config;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

//TODO: javadoc
public abstract class NfcTransceiver {
	private static final String TAG = "NfcTransceiver";
	
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String ISODEP_NOT_CONNECTED = "could not write message, IsoDep is no longer connected";
	public static final String UNEXPECTED_ERROR = "Unexpected error occured while transceiving a message.";
	
	private boolean enabled = false;
	private NfcEventHandler eventHandler;
	
	private long userId;
	
	private NfcMessageSplitter messageSplitter;
	private NfcMessageReassembler messageReassembler;
	
	private int lastSqNrReceived;
	private int lastSqNrSent;
	
	public NfcTransceiver(NfcEventHandler eventHandler, int maxWriteLength, long userId) {
		this.eventHandler = eventHandler;
		messageSplitter = new NfcMessageSplitter(maxWriteLength);
		messageReassembler = new NfcMessageReassembler();
		this.userId = userId;
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
			
			if (response.requestsNextFragment()) {
				Log.i(TAG, "sending next fragment");
			} else {
				if (response.requestsRetransmission()) {
					response = retransmit(nfcMessage);
				}
				
				if (response.requestsNextFragment()) {
					continue;
				} else {
					// the last message has been sent to the HCE, now we receive the response
					
					getNfcEventHandler().handleMessage(NfcEvent.MESSAGE_SENT, null);
					
					messageReassembler.handleReassembly(response);
					while (response.hasMoreFragments()) {
						NfcMessage toSend = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x00, null);
						response = write(toSend, false);
						if (response.requestsRetransmission()) {
							response = retransmit(toSend);
						}
						messageReassembler.handleReassembly(response);
					}
				}
			}
		}
		
		getNfcEventHandler().handleMessage(NfcEvent.MESSAGE_RECEIVED, null);
		return messageReassembler.getData();
	}
	
	private NfcMessage retransmit(NfcMessage nfcMessage) throws TransceiveException {
		boolean retransmissionSuccess = false;
		int count = 0;
		NfcMessage response = null;
		
		do {
			Log.d(TAG, "retransmitting last nfc message since requested");
			response = write(nfcMessage, true);
			count++;
			
			if (!response.requestsRetransmission()) {
				retransmissionSuccess = true;
				break;
			}
		} while (count < Config.MAX_RETRANSMITS);
		
		if (!retransmissionSuccess) {
			//Retransmitting message failed
			getNfcEventHandler().handleMessage(NfcEvent.RETRANSMIT_ERROR, null);
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
			Log.d(TAG, "nfc error reported");
			getNfcEventHandler().handleMessage(NfcEvent.ERROR_REPORTED, null);
			throw new TransceiveException(UNEXPECTED_ERROR);
		}
		
		boolean sendSuccess = false;
		for (int i=0; i<=Config.MAX_RETRANSMITS; i++) {
			if (responseCorrupt(response) || invalidSequenceNumber(response.getSequenceNumber())) {
				Log.d(TAG, "requesting retransmission because answer was not as expected");
				
				if (invalidSequenceNumber(response.getSequenceNumber()) && response.requestsRetransmission()) {
					//this is a deadlock, since both parties are requesting a retransmit
					getNfcEventHandler().handleMessage(NfcEvent.RETRANSMIT_ERROR, null);
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
			getNfcEventHandler().handleMessage(NfcEvent.RETRANSMIT_ERROR, null);
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
	
	//TODO: what about?
//	public abstract void reset();
	
	/**
	 * To initiate a NFC connection, the NFC reader sends a "SELECT AID" APDU to
	 * the emulated card. Android OS then instantiates the service which has
	 * this AID registered (see apduservice.xml).
	 * 
	 * @param userId
	 *            the user id is needed to recognize that the same device is
	 *            re-connecting to the HCE (after an unintended NFC hand-shake
	 *            with NXP controllers)
	 * @return the select aid apdu message
	 */
	protected byte[] createSelectAidApdu(long userId) {
		return CommandApdu.getCommandApdu(userId);
	}
	
	protected void handleAidApduResponse(byte[] response) {
		NfcMessage msg = new NfcMessage(response);
		if (msg.getStatus() == NfcMessage.AID_SELECTED) {
			//HostApduService recognized the AID
			eventHandler.handleMessage(NfcEvent.INITIALIZED, null);
		} else {
			Log.d(TAG, "apdu response is not as expected!");
			eventHandler.handleMessage(NfcEvent.INIT_FAILED, null);
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
	
	protected long getUserId() {
		return userId;
	}
	
}
