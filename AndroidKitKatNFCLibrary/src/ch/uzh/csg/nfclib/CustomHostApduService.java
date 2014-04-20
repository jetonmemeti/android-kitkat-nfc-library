package ch.uzh.csg.nfclib;

import java.util.ArrayList;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.util.Constants;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

//TODO: javadoc
public class CustomHostApduService extends HostApduService {
	
	public static final String TAG = "CustomHostApduService";
	
	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	protected static final int MAX_WRITE_LENGTH = 245;
	
	private static Activity hostActivity;
	private static NfcEventHandler eventHandler;
	private static IMessageHandler messageHandler;
	
	private static NfcMessageSplitter messageSplitter;
	private static NfcMessageReassembler messageReassembler;
	
	private static ArrayList<NfcMessage> fragments;
	private static int index = 0;
	
	private int lastSqNrReceived;
	private int lastSqNrSent;
	private NfcMessage lastMessage;
	
	public static void init(Activity activity, NfcEventHandler eventHandler, IMessageHandler messageHandler) {
		hostActivity = activity;
		CustomHostApduService.eventHandler = eventHandler;
		CustomHostApduService.messageHandler = messageHandler;
		messageSplitter = new NfcMessageSplitter(MAX_WRITE_LENGTH);
		messageReassembler = new NfcMessageReassembler();
	}
	
	/*
	 * The empty constructor is needed by android to instantiate the service.
	 * That is the reason why most fields are static.
	 */
	public CustomHostApduService() {
		if (hostActivity == null) {
			Log.d(TAG, "activity has not been set yet or user is not in the given activity");
		}
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		if (hostActivity == null) {
			Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
			//TODO: add sequence number?
			return new NfcMessage(NfcMessage.ERROR, (byte) (0x00), null).getData();
		}
		
		if (selectAidApdu(bytes)) {
			Log.d(TAG, "AID selected");
			//TODO: decide based on time if to resume or restart!
			//TODO: appropriately reset seq number references!
			eventHandler.handleMessage(NfcEvent.NFC_INITIALIZED, null);
			//TODO: change payload! combine into status with OR!
			return new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x00, new byte[]{NfcMessage.START_PROTOCOL}).getData();
		}
		
		return getResponse(bytes).getData();
	}
	
	private boolean selectAidApdu(byte[] bytes) {
		if (bytes == null || bytes.length < 2)
			return false;
		else
			return bytes[0] == Constants.CLA_INS_P1_P2[0] && bytes[1] == Constants.CLA_INS_P1_P2[1];
	}

	private NfcMessage getResponse(byte[] bytes) {
		//TODO: request retransmission here? similar in INT!
		if (bytes == null || bytes.length < NfcMessage.HEADER_LENGTH) {
			Log.e(TAG, "error occured when receiving message");
			eventHandler.handleMessage(NfcEvent.NFC_COMMUNICATION_ERROR, null);
			return new NfcMessage(NfcMessage.ERROR, (byte) 0x00, null);
		}
		
		NfcMessage incoming = new NfcMessage(bytes);
		Log.d(TAG, "received msg: "+incoming);
		
		byte status = (byte) (incoming.getStatus());
		
		
		//TODO: check sqnr of received msg
		
		
		switch (status) {
		case NfcMessage.HAS_MORE_FRAGMENTS:
			Log.d(TAG, "has more fragments");
			messageReassembler.handleReassembly(incoming);
			return new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x00, null);
		case NfcMessage.DEFAULT:
			Log.d(TAG, "handle default");
			messageReassembler.handleReassembly(incoming);
			//TODO: what if implementation takes to long?? polling?
			byte[] response = messageHandler.handleMessage(messageReassembler.getData());
			messageReassembler.clear();
		
			fragments = messageSplitter.getFragments(response);
			Log.d(TAG, "returning: " + response.length + " bytes, " + fragments.size() + " fragments");
			if (fragments.size() == 1) {
				NfcMessage nfcMessage = fragments.get(0);
				index = 0;
				fragments = null;
				return nfcMessage;
			} else {
				return fragments.get(index++);
			}
		case NfcMessage.GET_NEXT_FRAGMENT:
			if (fragments != null && !fragments.isEmpty() && index < fragments.size()) {
				NfcMessage toReturn = fragments.get(index++);
				if (toReturn.getStatus() != NfcMessage.HAS_MORE_FRAGMENTS) {
					index = 0;
					fragments = null;
				}
				
				Log.d(TAG, "returning next fragment (index: "+(index-1)+")");
				return toReturn;
			} else {
				Log.e(TAG, "IsoDep wants next fragment, but there is nothing to reply!");
				eventHandler.handleMessage(NfcEvent.NFC_COMMUNICATION_ERROR, null);
				return new NfcMessage(NfcMessage.ERROR, (byte) 0x00, null);
			}
		case NfcMessage.RETRANSMIT:
			//TODO: implement
			break;
		case NfcMessage.ERROR:
			//TODO: implement
			break;
			default:
				//TODO: handle, since this is an error!! should not receive something else than above
				//TODO: does this ever occur?
			
		}
		//TODO: fix this!
		return new NfcMessage(NfcMessage.DEFAULT, (byte) 0x00, null);
	}

	@Override
	public void onDeactivated(int reason) {
		//TODO: event handler!
		Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "("+reason+")");
	}

}
