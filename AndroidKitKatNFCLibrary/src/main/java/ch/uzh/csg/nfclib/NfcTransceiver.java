package ch.uzh.csg.nfclib;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcMessage.Type;

/**
 * packet flow:
 * 
 * <pre>
 * sender -> recipient
 * apdu_contst -> AID_SELECTED -> USER_ID (REQ) -> USER_ID (OK) -> handshake complete
 * 
 * 1st message:username, currency, amount - not signed -> 2nd message:username payer - payee,  (see paymentrequesthandler)
 * -> server call -> signed response from server -> send ok back
 * 
 * 
 * </pre>
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcTransceiver {

	public static final int MAX_RETRY = 10;
	public static final long SESSION_RESUME_THRESHOLD = 300;

	private static final String TAG = "##NFC## NfcTransceiver";

	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";

	private final Deque<NfcMessage> messageQueue = new LinkedList<NfcMessage>();

	private final NfcTransceiverImpl transceiver;
	private final NfcMessageSplitter messageSplitter = new NfcMessageSplitter();
	private final NfcMessageReassembler messageReassembler = new NfcMessageReassembler();
	private final NfcEvent eventHandler;
	private final long userId;
	private NfcMessage lastMessagePrepareSend;
	private NfcMessage lastMessageSent;
	private NfcMessage lastMessageReceived;
	private int retry = 0;

	public NfcTransceiver(NfcEvent eventHandler, Activity activity, long userId, NfcTransceiverImpl transceiver) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		this.transceiver = transceiver;
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	public NfcTransceiver(NfcEvent eventHandler, Activity activity, long userId) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		if (ExternalNfcTransceiver.isExternalReaderAttached(activity)) {
			transceiver = new ExternalNfcTransceiver(eventHandler, new NfcInit());
		} else {
			transceiver = new InternalNfcTransceiver(eventHandler, new NfcInit());
		}
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	class NfcInit {
		public void init() throws IOException {
			initNfc(false);
		}
	}

	public NfcTransceiverImpl nfcTransceiver() {
		return transceiver;
	}

	public void enable(Activity activity) {
		try {
			transceiver.enable(activity);
		} catch (NfcLibException e) {
			Log.e(TAG, "enable failed: " + e);
		}
	}

	public void disable(Activity activity) {
		try {
			transceiver.disable(activity);
		} catch (IOException e) {
			Log.e(TAG, "disable failed: " + e);
		}
	}
	
	void initNfc() {
		initNfc(false);
	}

	/**
	 * The init NFC messages that sends first a handshake. This method always
	 * needs to call the evenhandler in any case. The handshake is as follows:
	 * send AID -> get NfcMessage.AID / send NfcMessage.USER_ID -> get
	 * NfcMessage.USER_ID ok
	 */
	void initNfc(boolean resume) {
		try {
			Log.d(TAG, "init NFC");
			
			NfcMessage initMessage = new NfcMessage(Type.AID_SELECTED).request();
			//no sequence number here, as this is a special message
			NfcMessage response = transceiver.write(initMessage);
			
			if (!response.isSelectAidApdu()) {
				Log.e(TAG, "handshake unexpecetd: " + response);
				eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
				return;
			}
			if(!checkSequence(response)) {
				Log.e(TAG, "handshake unexpecetd, wrong sequence: " + response + "expceted:"+lastMessageReceived);
				eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
				return;
			}

			byte[] sendUserId = Utils.longToByteArray(userId);
			byte[] sendFragLen = Utils.intToByteArray(transceiver.maxLen());
			byte[] merged = Utils.merge(sendUserId, sendFragLen);
			
			NfcMessage msg = new NfcMessage(NfcMessage.Type.USER_ID).payload(merged).resume(resume);
			//no sequence number, this is considered as part of the handshake
			NfcMessage responseUserId = transceiver.write(msg);
			////--> here we can get an exception
			
			if(resume) {
				Log.d(TAG, "resume!");
				retry++;
				if(retry > MAX_RETRY) {
					Log.e(TAG, "retry failed: " + responseUserId);
					eventHandler.handleMessage(NfcEvent.Type.INIT_RETRY_FAILED, null);
					return;
				}
				transceiveQueue();
			} else {
				if(!checkSequence(responseUserId)) {
					Log.e(TAG, "handshake unexpecetd, wrong sequence: " + responseUserId);
					eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
					return;
				}
				Log.d(TAG, "do not resume, fresh session");
				if (responseUserId.type() != NfcMessage.Type.USER_ID) {
					Log.e(TAG, "handshake user id unexpecetd: " + responseUserId);
					eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
					return;
				}
				reset();
				Log.d(TAG, "handshake completed!");
				eventHandler.handleMessage(NfcEvent.Type.INITIALIZED, null);
			}
			
		} catch (Throwable t) {
			Log.e(TAG, "init exception: ", t);
			eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
		}
	}

	private void reset() {
		messageQueue.clear();
		messageReassembler.clear();
    }

	public synchronized void transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException(NULL_ARGUMENT);

		if (messageQueue != null && messageQueue.size() > 0) {
			Log.d(TAG, "still something left in the queue");
			transceiveQueue();
			return;
		}

		for(NfcMessage msg: messageSplitter.getFragments(bytes)) {
			messageQueue.offer(msg);
		}
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");
		transceiveQueue();
	}

	private void transceiveQueue() {
		while (!messageQueue.isEmpty()) {
			final NfcMessage request1;
			try {
				request1 = messageQueue.peek();
				request1.sequenceNumber(lastMessageSent);
				lastMessagePrepareSend = request1;
				NfcMessage response = transceiver.write(request1);
				////--> here we can get an exception
				
				handleTransceive(request1, response);
			} catch (Throwable t) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				Log.e(TAG, "tranceive exception", t);
				eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, t);
				initNfc(true);
				break;
			}
		}
	}

	private void handleTransceive(NfcMessage request1, NfcMessage response) {
		
		lastMessageSent = request1;
		//every thing is ok, remove from queue
		final NfcMessage request2 = messageQueue.poll();
		//sanity check
		if(!request1.equals(request2)) {
			Log.e(TAG, "sync exception");
		}
		
		if(!checkSequence(response)) {
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, "sequence error");
			return;
		}
		
		// the last message has been sent to the HCE, now we receive the
		// response
		if(!request1.hasMoreFragments() && request1.payload().length > 0) {
			eventHandler.handleMessage(NfcEvent.Type.MESSAGE_SENT, request1);
		}
		messageReassembler.handleReassembly(response);
		
		byte[] retVal = messageReassembler.data();
		if (response.hasMoreFragments()) {
			NfcMessage toSend = new NfcMessage(Type.GET_NEXT_FRAGMENT);
			messageQueue.offer(toSend);
		} else {
			if(retVal.length > 0) {
				eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, retVal);
			}
			messageReassembler.clear();
		}
	}
	
	private boolean checkSequence(NfcMessage response) {
		
		boolean check = response.check(lastMessageReceived);
		NfcMessage was = lastMessageReceived;
		lastMessageReceived = response;
		if(!check) {
			check = response.check(was);
			Log.e(TAG, "sequence number mismatch");
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, response.toString());
			return false;
		}
		return true;
	}
}
