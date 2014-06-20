package ch.uzh.csg.nfclib;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
public class CustomHostApduService {

	public static final String TAG = "##NFC## CustomHostApduService";

	/*
	 * NXP chip supports max 255 bytes (10 bytes is header of nfc protocol)
	 */
	//public static final int MAX_WRITE_LENGTH = 245;

	private static Activity hostActivity;
	private static NfcEvent eventHandler;
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

	public CustomHostApduService(Activity activity, NfcEvent eventHandler, IMessageHandler messageHandler) {
		hostActivity = activity;
		CustomHostApduService.eventHandler = eventHandler;
		CustomHostApduService.messageHandler = messageHandler;
		messageSplitter = new NfcMessageSplitter();
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
		Log.d(TAG, "processCommandApdu with " + Arrays.toString(bytes));

		NfcMessage outputMessage = null;
		if (hostActivity == null) {
			Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
			outputMessage = new NfcMessage().error();
			return prepareWrite(outputMessage);
		}
		
		NfcMessage inputMessage = new NfcMessage().bytes(bytes);

		working = true;

		if (inputMessage.isSelectAidApdu()) {
			/*
			 * The size of the returned message is specified in NfcTransceiver
			 * and is set to 2 actually.
			 */
			Log.d(TAG, "AID selected");
			now = System.currentTimeMillis();
			outputMessage = new NfcMessage().type(NfcMessage.AID_SELECTED);
			return prepareWrite(outputMessage);
		} else if (inputMessage.isReadBinary()) {
			Log.d(TAG, "keep alive message");
			outputMessage = new NfcMessage().readBinary();
			return prepareWrite(outputMessage);
		} else {
			Log.d(TAG, "regular message");
			outputMessage = handleRequest(inputMessage);
			if (outputMessage == null) {
				Log.e(TAG, "could not handle request");
				outputMessage = new NfcMessage().error();
			}
			return prepareWrite(outputMessage);
		}
	}

	private byte[] prepareWrite(NfcMessage outputMessage) {
		outputMessage.sequenceNumber(lastMessage);
		lastMessage = outputMessage;
		byte[] retVal = outputMessage.bytes();
		Log.d(TAG, "about to write " + Arrays.toString(retVal));
		return retVal;
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
		Log.d(TAG, "received msg: " + incoming);

		// byte status = (byte) (incoming.getStatus());

		if (incoming.isError()) {
			// less than zero means the error flag is set: NfcMessage.ERROR
			Log.d(TAG, "nfc error reported - returning null");
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, NfcTransceiver.UNEXPECTED_ERROR);
			return null;
		}

		NfcMessage toReturn;

		switch (incoming.type()) {
		case NfcMessage.USER_ID:
			// now we have the user id, get it
			long newUserId = Utils.byteArrayToLong(incoming.payload(), 0);
			int maxFragLen = Utils.byteArrayToInt(incoming.payload(), 8);
			Log.d(TAG, "received user id " + newUserId+ " and max frag len: "+maxFragLen);
			messageSplitter.maxTransceiveLength(maxFragLen);
			if (newUserId == userIdReceived && (now - timeDeactivated < NfcTransceiver.SESSION_RESUME_THRESHOLD)) {
				return new NfcMessage().type(NfcMessage.USER_ID);
			} else {
				userIdReceived = newUserId;
				eventHandler.handleMessage(NfcEvent.Type.INITIALIZED, Long.valueOf(userIdReceived));
				resetStates();
				return new NfcMessage().type(NfcMessage.USER_ID).startProtocol();
			}
		case NfcMessage.HAS_MORE_FRAGMENTS:
			Log.d(TAG, "has more fragments");
			messageReassembler.handleReassembly(incoming);
			return new NfcMessage().type(NfcMessage.GET_NEXT_FRAGMENT).sequenceNumber(incoming);
		case NfcMessage.DEFAULT:
			Log.d(TAG, "handle default");

			eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, null);

			messageReassembler.handleReassembly(incoming);
			// TODO: what if implementation takes to long?? polling?
			byte[] response = messageHandler.handleMessage(messageReassembler.data());
			fragments = messageSplitter.getFragments(response);
			messageReassembler.clear();
			
			Log.d(TAG, "returning: " + response.length + " bytes, " + fragments.size() + " fragments");
			if (fragments.size() == 1) {
				toReturn = fragments.get(0);
				lastSqNrReceived = lastSqNrSent = 0;
				index = 0;
				fragments = null;
				eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RETURNED, null);
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
					eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RETURNED, null);
				}

				Log.d(TAG, "returning next fragment (index: " + (index - 1) + ")");
				return toReturn;
			} else {
				Log.e(TAG, "IsoDep wants next fragment, but there is nothing to reply!");
				eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, NfcTransceiver.UNEXPECTED_ERROR);
				return new NfcMessage().error().sequenceNumber(incoming);
			}
		default:
			return new NfcMessage().type(NfcMessage.DEFAULT);

		}
	}

	public void onDeactivated(int reason) {
		Log.d(TAG, "deactivated due to "
		        + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "(" + reason + ")");
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
					if (now - startTime < NfcTransceiver.SESSION_RESUME_THRESHOLD) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
						}
					} else {
						cont = false;
						eventHandler.handleMessage(NfcEvent.Type.CONNECTION_LOST, null);
					}
				} else {
					cont = false;
				}
			}
		}

	}

}
