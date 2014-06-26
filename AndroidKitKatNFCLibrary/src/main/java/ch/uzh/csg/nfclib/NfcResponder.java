package ch.uzh.csg.nfclib;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import android.app.Activity;
import android.nfc.cardemulation.HostApduService;
import android.util.Log;
import ch.uzh.csg.nfclib.CustomHostApduService2.SendLater;
import ch.uzh.csg.nfclib.NfcMessage.Type;

//TODO: javadoc
public class NfcResponder {

	public static final String TAG = "##NFC## CustomHostApduService";

	private static Activity hostActivity;
	private static NfcEvent eventHandler;
	private static TransceiveHandler messageHandler;

	private static NfcMessageSplitter messageSplitter = new NfcMessageSplitter();
	private static NfcMessageReassembler messageReassembler = new NfcMessageReassembler();
	
	//TODO: handle tag lost
	private static final Deque<NfcMessage> messageQueue = new LinkedList<NfcMessage>();


	private static long userIdReceived = 0;

	private static NfcMessage lastMessageSent;
	static NfcMessage lastMessageReceived;


	private static Thread sessionResumeThread = null;
	private static volatile boolean working = false;

	private long now;
	
	//private SendLater sendLater;

	

	public NfcResponder(Activity activity, NfcEvent eventHandler, TransceiveHandler messageHandler) {
		hostActivity = activity;
		NfcResponder.eventHandler = eventHandler;
		NfcResponder.messageHandler = messageHandler;
		messageSplitter = new NfcMessageSplitter();
		messageReassembler = new NfcMessageReassembler();
		userIdReceived = 0;
		lastMessageSent = null;
		lastMessageReceived = null;
		Log.d(TAG, "init hostapdu constructor");
	}

	public byte[] processCommandApdu(byte[] bytes, SendLater sendLater) {
		working = true;
		Log.d(TAG, "processCommandApdu with " + Arrays.toString(bytes));

		NfcMessage outputMessage = null;
		if (hostActivity == null) {
			Log.e(TAG, "The user is not in the correct activity but tries to establish a NFC connection.");
			outputMessage = new NfcMessage(Type.EMPTY).error();
			return prepareWrite(outputMessage);
		}
		
		NfcMessage inputMessage = new NfcMessage(bytes);
		
		if (inputMessage.isSelectAidApdu()) {
			/*
			 * The size of the returned message is specified in NfcTransceiver
			 * and is currently set to 2.
			 */
			Log.d(TAG, "AID selected");
			now = System.currentTimeMillis();
			outputMessage = new NfcMessage(Type.AID_SELECTED).response();
			return outputMessage.bytes();
			//no sequnece number in handshake
			//return prepareWrite(outputMessage);
		} else if (inputMessage.isReadBinary()) {
			Log.d(TAG, "keep alive message");
			outputMessage = new NfcMessage(Type.READ_BINARY);
			return outputMessage.bytes();
			//no sequnece number in handshake
			//return prepareWrite(outputMessage);
		} else {
			Log.d(TAG, "regular message");
			if(lastMessageReceived != null) {
				Log.e(TAG, "last:"+ lastMessageReceived);
			}
			
			if(inputMessage.type() != Type.USER_ID) {
				Pair<Boolean, Boolean> seqCheck = checkSequence(inputMessage); 
				if(!seqCheck.element0() && !seqCheck.element1()) {
					
					Log.e(TAG, "sequence number mismatch " + inputMessage.sequenceNumber() + " / " + (lastMessageReceived == null? 0 : lastMessageReceived.sequenceNumber()));
					eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, inputMessage.toString());
					outputMessage = new NfcMessage(Type.EMPTY).error();
					return prepareWrite(outputMessage);
				}
				if(seqCheck.element1()) {
					return lastMessageSent.bytes();
				}
			}		
			//eventHandler fired in handleRequest
			outputMessage = handleRequest(inputMessage, sendLater);
			if (outputMessage == null) {
				//Log.e(TAG, "could not handle request");
				//outputMessage = new NfcMessage(Type.EMPTY).error();
				return null;
			}
			
			if(inputMessage.type() == Type.USER_ID) {
				Log.d(TAG, "repeat last message");
				return outputMessage.bytes();
			}
			return prepareWrite(outputMessage);
		}
	}

	static byte[] prepareWrite(NfcMessage outputMessage) {
		lastMessageSent = outputMessage.sequenceNumber(lastMessageSent);
		byte[] retVal = outputMessage.bytes();
		Log.d(TAG, "about to write " + Arrays.toString(retVal));
		return retVal;
	}

	private void resetStates() {
		messageReassembler.clear();
		messageQueue.clear();
	}
	
	private Pair<Boolean, Boolean> checkSequence(NfcMessage response) {
		// TODO thomas: why does checkSequence() not simply return !check &&
		// !repeat ? its harder to read the current code.
		// TODO thomas: therefore, the Pair class can also be deleted since it
		// is not used elsewhere
		boolean check = response.check(lastMessageReceived);
		boolean repeat = response.repeatLast(lastMessageReceived);
		lastMessageReceived = response;		
		return new Pair<Boolean, Boolean>(check, repeat);
	}

	private NfcMessage handleRequest(NfcMessage incoming, final SendLater sendLater) {
		Log.d(TAG, "received msg: " + incoming);

		if (incoming.isError()) {
			Log.d(TAG, "nfc error reported - returning null");
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, NfcTransceiver.UNEXPECTED_ERROR);
			return null;
		}
		boolean hasMoreFragments = incoming.hasMoreFragments();

		switch (incoming.type()) {
		case USER_ID:
			// now we have the user id, get it
			long newUserId = Utils.byteArrayToLong(incoming.payload(), 0);
			int maxFragLen = Utils.byteArrayToInt(incoming.payload(), 8);
			Log.d(TAG, "received user id " + newUserId+ " and max frag len: "+maxFragLen);
			messageSplitter.maxTransceiveLength(maxFragLen);
			if (incoming.isResume() && newUserId == userIdReceived && (System.currentTimeMillis() - now < NfcTransceiver.SESSION_RESUME_THRESHOLD)) {
				Log.d(TAG, "resume");
				return lastMessageSent.resume();
			} else {
				Log.d(TAG, "start fresh");
				userIdReceived = newUserId;
				lastMessageSent = null;
				lastMessageReceived = null;
				eventHandler.handleMessage(NfcEvent.Type.INITIALIZED, Long.valueOf(userIdReceived));
				resetStates();
				return new NfcMessage(Type.USER_ID).startProtocol();
			}
		case DEFAULT:
			Log.d(TAG, "handle default");
			
			if(hasMoreFragments) {
				messageReassembler.handleReassembly(incoming);
				return new NfcMessage(Type.GET_NEXT_FRAGMENT);
			}

			messageReassembler.handleReassembly(incoming);
			final byte[] receivedData = messageReassembler.data();
			messageReassembler.clear();
			
			eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, receivedData);
			
			messageHandler.handleMessage(receivedData, sendLater);
			
			return null;
			//return fragmentData(response);
		case GET_NEXT_FRAGMENT:
			if (messageQueue.isEmpty()) {
				Log.e(TAG, "nothing to return1");
				eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, null);
			}
			return messageQueue.poll();
			
		case POLLING:
			return new NfcMessage(Type.POLLING).request();
		default:
			return new NfcMessage(Type.DEFAULT);
		}
	}

	static NfcMessage fragmentData(byte[] response) {
		if(response == null) {
			return null;
		}
	    for(NfcMessage msg:messageSplitter.getFragments(response)) {
	    	messageQueue.offer(msg);
	    }
	    
	    Log.d(TAG, "returning: " + response.length + " bytes, " + messageQueue.size() + " fragments");
	    if (messageQueue.isEmpty()) {
	    	Log.e(TAG, "nothing to return2");
	    	eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, null);
	    }
	    return messageQueue.poll();
    }

	public void onDeactivated(int reason) {
		Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "(" + reason + ")");

		if (sessionResumeThread != null && !sessionResumeThread.isInterrupted())
			sessionResumeThread.interrupt();

		working = false;

		sessionResumeThread = new Thread(new SessionResumeTask());
		sessionResumeThread.start();
	}

	//TODO: make clean
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
