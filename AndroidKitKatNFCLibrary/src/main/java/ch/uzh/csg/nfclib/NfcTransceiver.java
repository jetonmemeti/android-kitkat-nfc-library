package ch.uzh.csg.nfclib;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import android.app.Activity;
import android.util.Log;

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

	public static final long SESSION_RESUME_THRESHOLD = 300;

	private static final String TAG = "##NFC## NfcTransceiver";

	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";

	private Queue<NfcMessage> messageQueue;

	private final NfcTransceiverImpl transceiver;
	private final NfcMessageSplitter messageSplitter;
	private final NfcMessageReassembler messageReassembler;
	private final NfcEvent eventHandler;
	private final long userId;
	
	public NfcTransceiver(NfcEvent eventHandler, Activity activity, long userId, NfcTransceiverImpl transceiver) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		this.transceiver = transceiver;
		
		messageReassembler = new NfcMessageReassembler();
		messageSplitter = new NfcMessageSplitter();
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
		messageQueue = new LinkedList<NfcMessage>();
	}

	public NfcTransceiver(NfcEvent eventHandler, Activity activity, long userId) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		if (ExternalNfcTransceiver.isExternalReaderAttached(activity)) {
			transceiver = new ExternalNfcTransceiver(eventHandler, new NfcInit());
		} else {
			transceiver = new InternalNfcTransceiver(eventHandler, new NfcInit());
		}
		messageReassembler = new NfcMessageReassembler();
		messageSplitter = new NfcMessageSplitter();
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
		messageQueue = new LinkedList<NfcMessage>();
	}

	class NfcInit {
		public void init() throws IOException {
			initNfc();
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

	protected void initNfc() throws IOException {
		try {
			Log.d(TAG, "init NFC");
			NfcMessage response = transceiver.write(new NfcMessage().selectAidApdu());
			if (!response.isSelectAidApdu()) {
				Log.e(TAG, "handshake unexpecetd: " + response);
			}

			byte[] sendUserId = Utils.longToByteArray(userId);
			byte[] sendFragLen = Utils.intToByteArray(transceiver.maxLen());
			byte[] merged = Utils.merge(sendUserId, sendFragLen);
			NfcMessage msg = new NfcMessage().type(NfcMessage.USER_ID).payload(merged);
			NfcMessage responseUserId = transceiver.write(msg);

			if (responseUserId.type() != NfcMessage.USER_ID) {
				Log.e(TAG, "handshake user id unexpecetd: " + responseUserId);
			}
			if (!responseUserId.isStartProtocol()) {
				Log.d(TAG, "resume!");
			} else {
				Log.d(TAG, "do not resume, fresh session");
				messageQueue = null;
				messageReassembler.clear();
			}
			Log.d(TAG, "handshake completed!");
			eventHandler.handleMessage(NfcEvent.Type.INITIALIZED, null);
		} catch (NfcLibException e) {
			Log.e(TAG, "Illegal argument: ", e);
			eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
		}
	}

	public synchronized void transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException(NULL_ARGUMENT);

		if (messageQueue != null && messageQueue.size() > 0) {
			Log.d(TAG, "still something left in the queue");
			transceiveQueue();
			return;
		}

		messageQueue = new LinkedList<NfcMessage>(messageSplitter.getFragments(bytes));
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");
		transceiveQueue();
	}

	private void transceiveQueue() {
		while (!messageQueue.isEmpty()) {
			try {
				transceive(messageQueue.poll());
			} catch (NfcLibException e) {
				/*
				 * When is thrown, there is no need to wait for retransmission
				 * or re-init by nfc handshake.
				 */
				Log.e(TAG, "tranceive exception1", e);
				break;
			} catch (IOException e) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				Log.e(TAG, "tranceive exception2", e);
				break;
			}
		}
	}

	public void transceive(NfcMessage nfcMessage) throws NfcLibException, IOException {
		NfcMessage response = transceiver.write(nfcMessage);
		// the last message has been sent to the HCE, now we receive the
		// response
		eventHandler.handleMessage(NfcEvent.Type.MESSAGE_SENT, null);

		while (response.hasMoreFragments()) {
			messageReassembler.handleReassembly(response);
			NfcMessage toSend = new NfcMessage().type(NfcMessage.GET_NEXT_FRAGMENT);
			response = transceiver.write(toSend);
			messageReassembler.handleReassembly(response);
		}
		messageQueue = null;
		byte[] retVal = messageReassembler.data();
		messageReassembler.clear();
		eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, retVal);
	}
}
