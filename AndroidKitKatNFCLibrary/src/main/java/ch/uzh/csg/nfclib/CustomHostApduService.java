package ch.uzh.csg.nfclib;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
import ch.uzh.csg.nfclib.util.Config;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;
import ch.uzh.csg.nfclib.util.Utils;

//TODO: javadoc
public class CustomHostApduService {
	
	public static final String TAG = "##NFC## CustomHostApduService";
	
	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	public static final int MAX_WRITE_LENGTH = 245;
	
	private static Activity hostActivity;
	private static NfcEventInterface eventHandler;
	private static IMessageHandler messageHandler;
	
	private static NfcMessageSplitter messageSplitter;
	private static NfcMessageReassembler messageReassembler;
	
	private static ArrayList<NfcMessage> fragments;
	private static int index = 0;
	
	private static long userIdReceived = 0;
	
	private static long timeDeactivated = 0;
	
	private static int lastSqNrReceived;
	private static int lastSqNrSent;
	private static NfcMessage lastMessage;
	private static int nofRetransmissions = 0;
	
	private static Thread sessionResumeThread = null;
	private static volatile boolean working = false;
	
	private long now;
	
	private static Object lock = new Object();
	
	public CustomHostApduService(Activity activity, NfcEventInterface eventHandler, IMessageHandler messageHandler) {
		hostActivity = activity;
		CustomHostApduService.eventHandler = eventHandler;
		CustomHostApduService.messageHandler = messageHandler;
		messageSplitter = new NfcMessageSplitter(MAX_WRITE_LENGTH);
		messageReassembler = new NfcMessageReassembler();
		
		fragments = null;
		index = 0;
		userIdReceived = 0;
		timeDeactivated = 0;
		
		lastSqNrReceived = 0;
		lastSqNrSent = 0;
		lastMessage = null;
		nofRetransmissions = 0;
		Log.d(TAG, "init hostapdu1");
	}
	
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		Log.d(TAG, "processCommandApdu");
		NfcMessage inputMessage = new NfcMessage().bytes(bytes);
		synchronized (lock) {
			if (hostActivity == null) {
				Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
				return new NfcMessage().sequenceNumber(inputMessage).error().bytes();
			}
			
			working = true;
			
			if (inputMessage.isSelectAidApdu()) {
				/*
				 * The size of the returned message is specified in NfcTransceiver
				 * and is set to 2 actually.
				 */
				Log.d(TAG, "AID selected");
				now = System.currentTimeMillis();
				return new NfcMessage().sequenceNumber(inputMessage).type(NfcMessage.AID_SELECTED).bytes();
			} else if (inputMessage.isReadBinary()) {
				return new NfcMessage().readBinary().bytes();
			}
			
			NfcMessage response = handleRequest(inputMessage);
			if (response == null) {
				return null;
			} else {
				lastMessage = response;
				byte[] retVal = response.bytes();
				Log.d(TAG, "about to write "+Arrays.toString(retVal));
				return retVal;
			}
		}
	}

	

	

	private void resetStates() {
		messageReassembler.clear();
		fragments = null;
		index = 0;
		
		lastSqNrReceived = 0;
		lastSqNrSent = 0;
		lastMessage = null;
		
		nofRetransmissions = 0;
	}

	private NfcMessage handleRequest(NfcMessage incoming) {
		Log.d(TAG, "received msg: "+incoming);
		
		//byte status = (byte) (incoming.getStatus());
		
		if (incoming.isError()) {
			// less than zero means the error flag is set:  NfcMessage.ERROR
			Log.d(TAG, "nfc error reported - returning null");
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcTransceiver.UNEXPECTED_ERROR);
			return null;
		}
		
		NfcMessage toReturn;
		
		switch (incoming.type()) {
		case NfcMessage.USER_ID:
			//now we have the user id, get it
			long newUserId = Utils.byteArrayToLong(incoming.payload(), 0);
			Log.d(TAG, "received user id "+ newUserId);
			if (newUserId == userIdReceived && (now - timeDeactivated < Config.SESSION_RESUME_THRESHOLD)) {
				return new NfcMessage().type(NfcMessage.USER_ID);						
			} else {
				userIdReceived = newUserId;
				eventHandler.handleMessage(NfcEvent.INITIALIZED, Long.valueOf(userIdReceived));
				resetStates();
				return new NfcMessage().type(NfcMessage.USER_ID).sequenceNumber(incoming).startProtocol();						
			}
		case NfcMessage.HAS_MORE_FRAGMENTS:
			Log.d(TAG, "has more fragments");
			messageReassembler.handleReassembly(incoming);
			return new NfcMessage().type(NfcMessage.GET_NEXT_FRAGMENT).sequenceNumber(incoming);
		case NfcMessage.DEFAULT:
			Log.d(TAG, "handle default");
			
			eventHandler.handleMessage(NfcEvent.MESSAGE_RECEIVED, null);
			
			messageReassembler.handleReassembly(incoming);
			//TODO: what if implementation takes to long?? polling?
			byte[] response = messageHandler.handleMessage(messageReassembler.data());
			messageReassembler.clear();
		
			fragments = messageSplitter.getFragments(response);
			Log.d(TAG, "returning: " + response.length + " bytes, " + fragments.size() + " fragments");
			if (fragments.size() == 1) {
				toReturn = fragments.get(0);
				lastSqNrReceived = lastSqNrSent = 0;
				index = 0;
				fragments = null;
				eventHandler.handleMessage(NfcEvent.MESSAGE_RETURNED, null);
			} else {
				toReturn = fragments.get(index++);
			}
			return toReturn;
		case NfcMessage.GET_NEXT_FRAGMENT:
			if (fragments != null && !fragments.isEmpty() && index < fragments.size()) {
				toReturn = fragments.get(index++);
				if (toReturn.hasMoreFragments()) {
					lastSqNrReceived = lastSqNrSent = 0;
					index = 0;
					fragments = null;
					eventHandler.handleMessage(NfcEvent.MESSAGE_RETURNED, null);
				}
				
				Log.d(TAG, "returning next fragment (index: "+(index-1)+")");
				return toReturn;
			} else {
				Log.e(TAG, "IsoDep wants next fragment, but there is nothing to reply!");
				eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcTransceiver.UNEXPECTED_ERROR);
				return new NfcMessage().error().sequenceNumber(incoming);
			}
		default:
			return new NfcMessage().type(NfcMessage.DEFAULT);
			
		}
	}
	
	public void onDeactivated(int reason) {
		Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "("+reason+")");
		timeDeactivated = System.currentTimeMillis();
		
		if (sessionResumeThread != null && !sessionResumeThread.isInterrupted())
			sessionResumeThread.interrupt();

		working = false;
		
		sessionResumeThread = new Thread(new SessionResumeTask());
		sessionResumeThread.start();
	}
	
	private class SessionResumeTask implements Runnable {
		
		public void run() {
			long startTime = System.currentTimeMillis();
			boolean cont = true;
			long now;
			
			while (cont && !Thread.currentThread().isInterrupted()) {
				now = System.currentTimeMillis();
				if (!working) {
					if (now - startTime < Config.SESSION_RESUME_THRESHOLD) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
						}
					} else {
						cont = false;
						eventHandler.handleMessage(NfcEvent.CONNECTION_LOST, null);
					}
				} else {
					cont = false;
				}
			}
		}
		
	}

}
