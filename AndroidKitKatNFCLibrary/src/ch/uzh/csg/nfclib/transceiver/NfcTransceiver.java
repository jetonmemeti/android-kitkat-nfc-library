package ch.uzh.csg.nfclib.transceiver;

import java.io.IOException;
import java.util.LinkedList;

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
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";
	
	private boolean enabled = false;
	private NfcEventHandler eventHandler;
	
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
	
	public NfcTransceiver(NfcEventHandler eventHandler, int maxWriteLength, long userId) {
		this.eventHandler = eventHandler;
		messageSplitter = new NfcMessageSplitter(maxWriteLength);
		messageReassembler = new NfcMessageReassembler();
		this.userId = userId;
	}
	
	public abstract void enable(Activity activity) throws NoNfcException, NfcNotEnabledException;
	
	public abstract void disable(Activity activity);
	
	protected abstract byte[] writeRaw(byte[] bytes) throws IllegalArgumentException, TransceiveException, IOException;
	
	protected void initNfc() throws IOException {
		try {
			byte[] response = writeRaw(createSelectAidApdu(getUserId()));
			handleAidApduResponse(response);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Illegal argument: ", e);
			getNfcEventHandler().handleMessage(NfcEvent.INIT_FAILED, null);
		} catch (TransceiveException e) {
			Log.e(TAG, "TransceiveException: ", e);
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
			} catch (TransceiveException e) {
				/*
				 * When is thrown, there is no need to wait for retransmission
				 * or re-init by nfc handshake.
				 */
				
				sessionResumeThread.interrupt();
				getNfcEventHandler().handleMessage(e.getNfcEvent(), e.getMessage());
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
	
	private synchronized void transceive(NfcMessage nfcMessage) throws IllegalArgumentException, TransceiveException, IOException {
		NfcMessage response = write(nfcMessage, false);
		
		if (response.requestsNextFragment()) {
			Log.i(TAG, "sending next fragment");
		} else {
			if (response.requestsRetransmission()) {
				response = retransmit(nfcMessage);
			}
			
			if (!response.requestsNextFragment()) {
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
				responseReady = true;
			}
		}
	}
	
	private NfcMessage write(NfcMessage nfcMessage, boolean isRetransmission) throws IllegalArgumentException, TransceiveException, IOException {
		if (!isRetransmission) {
			lastSqNrSent++;
			if (lastSqNrSent > 255) {
				// reset the counter, because next message will have sq nr 1!
				lastSqNrSent = 1;
			}
		}
		
		nfcMessage.setSequenceNumber((byte) lastSqNrSent);
		
		lastNfcMessageSent = nfcMessage;
		NfcMessage response = new NfcMessage(writeRaw(nfcMessage.getData()));
		
		if (response.getStatus() == NfcMessage.ERROR) {
			Log.d(TAG, "nfc error reported");
			throw new TransceiveException(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
		}
		
		boolean sendSuccess = false;
		for (int i=0; i<=Config.MAX_RETRANSMITS; i++) {
			if (responseCorrupt(response) || response.invalidSequenceNumber(lastSqNrReceived+1)) {
				Log.d(TAG, "requesting retransmission because answer was not as expected");
				
				if (response.invalidSequenceNumber(lastSqNrReceived+1) && response.requestsRetransmission()) {
					//this is a deadlock, since both parties are requesting a retransmit
					throw new TransceiveException(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
				}
				
				lastSqNrSent++;
				if (lastSqNrSent > 255) {
					// reset the counter, because next message will have sq nr 1!
					lastSqNrSent = 1;
				}
				
				lastNfcMessageSent = new NfcMessage(NfcMessage.RETRANSMIT, (byte) lastSqNrSent, null);
				response = new NfcMessage(writeRaw(lastNfcMessageSent.getData()));
			} else {
				sendSuccess = true;
				lastSqNrReceived++;
				if (lastSqNrReceived == 255) {
					// reset the counter, because next message will have sq nr 1!
					lastSqNrReceived = 0;
				}
				break;
			}
		}
		
		if (!sendSuccess) {
			//Requesting retransmit failed
			throw new TransceiveException(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
		}
		
		return response;
	}

	private NfcMessage retransmit(NfcMessage nfcMessage) throws TransceiveException, IllegalArgumentException, IOException {
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
			throw new TransceiveException(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
		}
		
		return response;
	}
	
	private boolean responseCorrupt(NfcMessage response) {
		return response.getData() == null || response.getData().length < NfcMessage.HEADER_LENGTH; 
	}
	
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
		if (msg.aidSelected()) {
			//HostApduService recognized the AID
			if (msg.isStartProtocol()) {
				eventHandler.handleMessage(NfcEvent.INITIALIZED, null);
				resetStates();
			} else {
				/*
				 * decrement because lastNfcMessageSent has the same sequence
				 * number as before and the following messages should have
				 * consecutive sq nrs but was incremented before exceptions was
				 * thrown
				 */
				lastSqNrSent--;
				messageQueue.addFirst(lastNfcMessageSent);
				transceiveQueue();
			}
		} else {
			Log.d(TAG, "apdu response is not as expected!");
			eventHandler.handleMessage(NfcEvent.INIT_FAILED, null);
		}
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
	
	protected NfcEventHandler getNfcEventHandler() {
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
				getNfcEventHandler().handleMessage(NfcEvent.MESSAGE_RECEIVED, messageReassembler.getData());
			} else if (returnErrorMessage) {
				getNfcEventHandler().handleMessage(NfcEvent.CONNECTION_LOST, null);
			}
		}
		
	}
	
}
