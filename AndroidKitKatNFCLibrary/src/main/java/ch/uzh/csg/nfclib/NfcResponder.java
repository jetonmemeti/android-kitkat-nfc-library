package ch.uzh.csg.nfclib;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.nfc.cardemulation.HostApduService;
import android.util.Log;
import ch.uzh.csg.nfclib.events.INfcEventHandler;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.messages.NfcMessageSplitter;
import ch.uzh.csg.nfclib.messages.NfcMessage.Type;
import ch.uzh.csg.nfclib.utils.Config;
import ch.uzh.csg.nfclib.utils.Utils;

/**
 * This class represents the counterpart of the {@link NfcInitiator}. It listens
 * for incoming NFC messages and provides the appropriate response.
 * 
 * Message fragmentation and reassembly is handled internally.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcResponder {
	private static final String TAG = "ch.uzh.csg.nfclib.NfcResponder";

	private final INfcEventHandler eventHandler;
	private final ITransceiveHandler messageHandler;

	private final NfcMessageSplitter messageSplitter = new NfcMessageSplitter();
	private final Deque<NfcMessage> messageQueue = new LinkedList<NfcMessage>();
	private final Object lock = new Object();

	// state
	private long userIdReceived = 0;
	private NfcMessage lastMessageSent;
	private NfcMessage lastMessageReceived;
	private long timeInitMessageReceived;

	private ExecutorService executorService = null;
	private TimeoutTask task;
	private byte[] data = null;

	private final ISendLater sendLater = new ISendLater() {
		@Override
		public void sendLater(final byte[] bytes) {
			if (bytes == null) {
				throw new IllegalArgumentException("cannot be null");
			}
			
			if (Config.DEBUG)
				Log.d(TAG, "send later " + Arrays.toString(bytes));
			
			NfcResponder.this.sendLater(bytes);
		}
	};

	/**
	 * Instantiates a new object to response to incoming NFC messages.
	 * 
	 * @param eventHandler
	 *            the {@link INfcEventHandler} to listen for {@link NfcEvent}s
	 * @param messageHandler
	 *            the {@link ITransceiveHandler} which provides appropriate
	 *            responses for incoming messages
	 */
	public NfcResponder(INfcEventHandler eventHandler, ITransceiveHandler messageHandler) {
		this.eventHandler = eventHandler;
		this.messageHandler = messageHandler;
		userIdReceived = 0;
		lastMessageSent = null;
		lastMessageReceived = null;

		executorService = Executors.newSingleThreadExecutor();
	}

	private void sendLater(byte[] bytes) {
		synchronized (lock) {
			data = bytes;
		}
	}

	private NfcMessage checkForData() {
		synchronized (lock) {
			if (data == null) {
				return null;
			}
			NfcMessage nfcMessage = fragmentData(data);
			data = null;
			return nfcMessage;
		}
	}

	/**
	 * Processes the incoming data and returns the appropriate response.
	 * 
	 * @param bytes
	 *            the data which has been received over NFC
	 * @return the response which should be returned over NFC to the
	 *         {@link NfcInitiator}
	 */
	public byte[] processIncomingData(byte[] bytes) {
		// if a shutdowtask is running, shutdown, as we can continue
		shutdownTask();
		
		if (Config.DEBUG)
			Log.d(TAG, "processCommandApdu with " + Arrays.toString(bytes));
		
		NfcMessage inputMessage = new NfcMessage(bytes);
		NfcMessage outputMessage = null;
		
		if (inputMessage.version() > NfcMessage.getSupportedVersion()) {
			if (Config.DEBUG)
				Log.d(TAG, "excepted NfcMessage version "+NfcMessage.getSupportedVersion()+" but was "+inputMessage.version());
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcInitiator.INCOMPATIBLE_VERSIONS);
			
			//TODO: send the complete string??
			outputMessage = new NfcMessage(Type.ERROR).payload(NfcInitiator.INCOMPATIBLE_VERSIONS.getBytes());
			return prepareWrite(outputMessage, true);
		}

		if (inputMessage.isSelectAidApdu()) {
			/*
			 * The size of the returned message is specified in NfcTransceiver
			 * and is currently set to 2.
			 */
			if (Config.DEBUG)
				Log.d(TAG, "AID selected");
			
			timeInitMessageReceived = System.currentTimeMillis();
			outputMessage = new NfcMessage(Type.AID_SELECTED).response();
			// no sequence number in handshake
			return outputMessage.bytes();
		} else if (inputMessage.isReadBinary()) {
			if (Config.DEBUG)
				Log.d(TAG, "keep alive message");
			
			outputMessage = new NfcMessage(Type.READ_BINARY);
			// no sequence number in handshake
			return outputMessage.bytes();
		} else {
			if (Config.DEBUG)
				Log.d(TAG, "regular message");

			// check sequence if we are not a user_id message. As this message
			// resets the sequence numbers
			boolean isUserMessage = inputMessage.type() == Type.USER_ID;
			if (!isUserMessage) {
				boolean check = inputMessage.check(lastMessageReceived);
				boolean repeat = inputMessage.repeatLast(lastMessageReceived);
				lastMessageReceived = inputMessage;

				if (!check && !repeat) {
					if (Config.DEBUG)
						Log.e(TAG, "sequence number mismatch " + inputMessage.sequenceNumber() + " / " + (lastMessageReceived == null ? 0 : lastMessageReceived.sequenceNumber()));
					
					eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcInitiator.UNEXPECTED_ERROR);
					outputMessage = new NfcMessage(Type.ERROR);
					return prepareWrite(outputMessage, true);
				}
				if (!check && repeat) {
					return lastMessageSent.bytes();
				}
			}
			// eventHandler fired in handleRequest
			outputMessage = handleRequest(inputMessage, sendLater);
			
			// do not increase sequence number for user_id as this belongs to
			// the handshake
			return prepareWrite(outputMessage, !isUserMessage);
		}
	}

	private byte[] prepareWrite(NfcMessage outputMessage, boolean increaseSeq) {
		if (increaseSeq) {
			lastMessageSent = outputMessage.sequenceNumber(lastMessageSent);
		} else {
			lastMessageSent = outputMessage;
		}
		byte[] retVal = outputMessage.bytes();
		
		if (Config.DEBUG)
			Log.d(TAG, "sending: " + outputMessage);
		
		return retVal;
	}

	private void resetStates() {
		data = null;
		messageSplitter.clear();
		messageQueue.clear();
	}

	private NfcMessage handleRequest(NfcMessage incoming, final ISendLater sendLater) {
		if (Config.DEBUG)
			Log.d(TAG, "received: " + incoming);

		if (incoming.isError()) {
			if (Config.DEBUG)
				Log.d(TAG, "nfc error reported - returning null");
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcInitiator.UNEXPECTED_ERROR);
			return null;
		}
		boolean hasMoreFragments = incoming.hasMoreFragments();

		switch (incoming.type()) {
		case USER_ID:
			// now we have the user id, get it
			long newUserId = Utils.byteArrayToLong(incoming.payload(), 0);
			int maxFragLen = Utils.byteArrayToInt(incoming.payload(), 8);
			
			messageSplitter.maxTransceiveLength(maxFragLen);
			if (incoming.isResume() && newUserId == userIdReceived && (System.currentTimeMillis() - timeInitMessageReceived < NfcInitiator.SESSION_RESUME_THRESHOLD)) {
				if (Config.DEBUG)
					Log.d(TAG, "resume");
				
				return lastMessageSent.resume();
			} else {
				if (Config.DEBUG)
					Log.d(TAG, "new session (no resume)");
				
				userIdReceived = newUserId;
				lastMessageSent = null;
				lastMessageReceived = null;
				eventHandler.handleMessage(NfcEvent.INITIALIZED, Long.valueOf(userIdReceived));
				resetStates();
				return new NfcMessage(Type.USER_ID);
			}
		case DEFAULT:
			if (hasMoreFragments) {
				messageSplitter.reassemble(incoming);
				return new NfcMessage(Type.GET_NEXT_FRAGMENT);
			}

			messageSplitter.reassemble(incoming);
			final byte[] receivedData = messageSplitter.data();
			messageSplitter.clear();

			eventHandler.handleMessage(NfcEvent.MESSAGE_RECEIVED, receivedData);

			byte[] response = messageHandler.handleMessage(receivedData, sendLater);

			// the user can decide to use sendLater. In that case, we'll start
			// to poll. This is triggered by returning null.
			if (response == null) {
				return new NfcMessage(NfcMessage.Type.POLLING).request();
			} else {
				return fragmentData(response);
			}
		case GET_NEXT_FRAGMENT:
			if (messageQueue.isEmpty()) {
				if (Config.DEBUG)
					Log.e(TAG, "nothing to return (get next fragment)");
				
				eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcInitiator.UNEXPECTED_ERROR);
			}
			return messageQueue.poll();
		case POLLING:
			NfcMessage msg = checkForData();
			if (msg != null) {
				return msg;
			} else {
				return new NfcMessage(Type.POLLING).request();
			}
		default:
			return new NfcMessage(Type.ERROR);
		}
	}

	private NfcMessage fragmentData(byte[] response) {
		if (response == null) {
			return null;
		}
		for (NfcMessage msg : messageSplitter.getFragments(response)) {
			messageQueue.offer(msg);
		}

		if (Config.DEBUG)
			Log.d(TAG, "returning: " + response.length + " bytes, " + messageQueue.size() + " fragments");
		
		if (messageQueue.isEmpty()) {
			if (Config.DEBUG)
				Log.e(TAG, "nothing to return - message queue is empty");
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, NfcInitiator.UNEXPECTED_ERROR);
		}
		return messageQueue.poll();
	}

	/**
	 * This has to be called whenever the system detects that the NFC has been
	 * aborted.
	 * 
	 * @param reason
	 *            see {@link HostApduServiceNfcLib}
	 */
	public void onDeactivated(int reason) {
		if (Config.DEBUG)
			Log.d(TAG, "deactivated due to " + (reason == HostApduService.DEACTIVATION_LINK_LOSS ? "link loss" : "deselected") + "(" + reason + ")");

		shutdownTask();
		task = new TimeoutTask();
		executorService.submit(task);
	}

	private void shutdownTask() {
		if (task != null) {
			task.shutdown();
			task = null;
		}
	}

	private class TimeoutTask implements Runnable {
		private final CountDownLatch latch = new CountDownLatch(1);
		private final long lastAcitivity = System.currentTimeMillis();

		public void shutdown() {
			latch.countDown();
		}

		@Override
		public void run() {
			try {
				long waitTime = NfcInitiator.SESSION_RESUME_THRESHOLD;
				while (!latch.await(waitTime, TimeUnit.MILLISECONDS)) {
					final long now = System.currentTimeMillis();
					final long idle;
					synchronized (this) {
						idle = now - lastAcitivity;
					}
					if (idle > NfcInitiator.SESSION_RESUME_THRESHOLD) {
						if (Config.DEBUG)
							Log.e(TAG, "connection lost, idle: " + idle);
						
						latch.countDown();
						eventHandler.handleMessage(NfcEvent.CONNECTION_LOST, null);
						return;
					} else {
						waitTime = NfcInitiator.SESSION_RESUME_THRESHOLD - idle;
					}
				}

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
	
}
