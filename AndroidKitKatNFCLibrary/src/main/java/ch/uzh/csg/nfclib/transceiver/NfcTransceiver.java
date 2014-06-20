package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.LinkedList;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventInterface;
import ch.uzh.csg.nfclib.NfcMessage;
import ch.uzh.csg.nfclib.util.Config;
import ch.uzh.csg.nfclib.util.NfcMessageReassembler;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;
import ch.uzh.csg.nfclib.util.Utils;

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
public abstract class NfcTransceiver {
	private static final String TAG = "##NFC## NfcTransceiver";
	
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";
	
	private boolean enabled = false;
	private NfcEventInterface eventHandler;
	
	private long userId;
	
	private NfcMessageSplitter messageSplitter;
	private NfcMessageReassembler messageReassembler;
	
	private int lastSqNrReceived;
	private int lastSqNrSent;
	
	private NfcMessage lastNfcMessageSent;
	private LinkedList<NfcMessage> messageQueue;
	
	private volatile boolean responseReady = false;
	private volatile boolean working = false;
	private volatile boolean returnErrorMessage = false;
	
	private Thread sessionResumeThread;
	
	public NfcTransceiver(NfcEventInterface eventHandler, int maxWriteLength, long userId) {
		this.eventHandler = eventHandler;
		messageSplitter = new NfcMessageSplitter(maxWriteLength);
		messageReassembler = new NfcMessageReassembler();
		this.userId = userId;
	}
	
	public abstract void enable(Activity activity) throws NfcLibException;
	
	public abstract void disable(Activity activity);
	
	protected NfcMessage writeRaw(NfcMessage nfcMessage) throws NfcLibException, IOException {
		nfcMessage.sequenceNumber(lastNfcMessageSent);
		lastNfcMessageSent = nfcMessage;
		return new NfcMessage().bytes(writeRaw(nfcMessage.bytes()));
	}
	
	protected abstract byte[] writeRaw(byte[] bytes) throws NfcLibException, IOException;
	
	protected void initNfc() throws IOException {
		try {
			NfcMessage response = writeRaw(new NfcMessage().selectAidApdu());
			if(response.isSelectAidApdu()) {
				byte[] sendUserId = Utils.longToByteArray(userId);
				NfcMessage msg = new NfcMessage().type(NfcMessage.USER_ID).payload(sendUserId);
				NfcMessage responseUserId = writeRaw(msg);
				if(responseUserId.type() != NfcMessage.USER_ID) {
					Log.e(TAG, "handshake user id unexpecetd: " + responseUserId);
				}
				if(!responseUserId.isStartProtocol()) {
					Log.d(TAG, "resume!");
				} else {
					resetStates();
				}
				Log.d(TAG, "handshake completed!");
				eventHandler.handleMessage(NfcEvent.INITIALIZED, null);
			} else {
				Log.e(TAG, "handshake unexpecetd: " + response);
			}
			
		} catch (NfcLibException e) {
			Log.e(TAG, "Illegal argument: ", e);
			getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
		}
	}
	
	public synchronized void transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException(NULL_ARGUMENT);
		
		messageReassembler.clear();
		lastSqNrReceived = lastSqNrSent = 0;
		responseReady = working = returnErrorMessage = false;
		lastNfcMessageSent = null;
		messageQueue = new LinkedList<NfcMessage>(messageSplitter.getFragments(bytes));
		
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");
		
		working = true;
		sessionResumeThread = new Thread(new SessionResumeTask());
		sessionResumeThread.start();
		
		transceiveQueue();
	}

	private void transceiveQueue() {
		working = true;
		
		while (!messageQueue.isEmpty()) {
			try {
				transceive(messageQueue.poll());
			} catch (NfcLibException e) {
				/*
				 * When is thrown, there is no need to wait for retransmission
				 * or re-init by nfc handshake.
				 */
				
				sessionResumeThread.interrupt();
				if(e.nfcEvent() != null) {
					getNfcEventHandler().handleMessage(e.nfcEvent(), e.getMessage());
				} else {
					returnErrorMessage = true;
				}
				break;
			} catch (IOException e) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				
				returnErrorMessage = true;
				break;
			}
		}
		
		working = false;
	}
	
	private synchronized void transceive(NfcMessage nfcMessage) throws NfcLibException, IOException {
		NfcMessage response = write(nfcMessage, false);
		
		
				// the last message has been sent to the HCE, now we receive the response
				
				getNfcEventHandler().handleMessage(NfcEvent.MESSAGE_SENT, null);
				messageReassembler.handleReassembly(response);
				while (response.hasMoreFragments()) {
					NfcMessage toSend = new NfcMessage().type(NfcMessage.GET_NEXT_FRAGMENT);
					response = write(toSend, false);
					messageReassembler.handleReassembly(response);
				}
				responseReady = true;
		
	}
	
	private NfcMessage write(NfcMessage nfcMessage, boolean isRetransmission) throws NfcLibException, IOException {
		
		
		lastNfcMessageSent = nfcMessage;
		NfcMessage response = writeRaw(nfcMessage);
		
		if (response.isError()) {
			Log.d(TAG, "nfc error reported");
			throw new NfcLibException(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
		}
		
		return response;
	}
	
	private void resetStates() {
		messageReassembler.clear();
		lastSqNrReceived = 0;
		lastSqNrSent = 0;
		lastNfcMessageSent = null;
		messageQueue = null;
		
		responseReady = false;
		working = false;
		returnErrorMessage = false;
		
		if (sessionResumeThread != null && sessionResumeThread.isAlive() && !sessionResumeThread.isInterrupted()) {
			sessionResumeThread.interrupt();
		}
	}
	
	protected boolean isEnabled() {
		return enabled;
	}
	
	protected void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	protected NfcEventInterface getNfcEventHandler() {
		return eventHandler;
	}
	
	protected long getUserId() {
		return userId;
	}

	private class SessionResumeTask implements Runnable {
		
		public void run() {
			long startTime = System.currentTimeMillis();
			boolean cont = true;
			long now;
			while (cont && !Thread.currentThread().isInterrupted()) {
				now = System.currentTimeMillis();
				if (working) {
					try {
						startTime = System.currentTimeMillis()+50;
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
				} else if (now - startTime < Config.SESSION_RESUME_THRESHOLD) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
				} else {
					cont = false;
				}
			}
			
			if (responseReady) {
				getNfcEventHandler().handleMessage(NfcEvent.MESSAGE_RECEIVED, messageReassembler.data());
			} else if (returnErrorMessage) {
				getNfcEventHandler().handleMessage(NfcEvent.CONNECTION_LOST, null);
			}
		}
		
	}
	
}
