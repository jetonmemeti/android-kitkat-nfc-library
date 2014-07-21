package ch.uzh.csg.nfclib;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.events.INfcEventHandler;
import ch.uzh.csg.nfclib.events.NfcEvent;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.messages.NfcMessage.Type;
import ch.uzh.csg.nfclib.messages.NfcMessageSplitter;
import ch.uzh.csg.nfclib.transceiver.ExternalNfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.INfcTransceiver;
import ch.uzh.csg.nfclib.transceiver.InternalNfcTransceiver;
import ch.uzh.csg.nfclib.utils.Config;
import ch.uzh.csg.nfclib.utils.Utils;

/**
 * This class represents the NFC party which initiates a NFC connection. It
 * sends a request and receives a response from the {@link NfcResponder}. This
 * can be repeated as often as required.
 * 
 * To be able to send and receive messages, enable() has to be called first.
 * Afterwards, transceive(byte[]) can be called. Once all messages are
 * exchanged, disable() has to be called in order to stop the services
 * appropriately.
 * 
 * Packet flow (handshake):
 * sender -> recipient
 * APDU ->
 * <- AID_SELECTED
 * -> USER_ID
 * <- USER_ID
 * = handshake complete
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcInitiator {
	private static final String TAG = "ch.uzh.csg.nfclib.NfcInitiator";
	
	//TODO: delete one of these two
	public static final int CONNECTION_TIMEOUT = 500;
	public static final int SESSION_RESUME_THRESHOLD = 500;
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";
	public static final String INCOMPATIBLE_VERSIONS = "The versions used are incompatible. The party with the lower version needs to update the app before you can use this feature.";

	private final INfcTransceiver transceiver;
	private final INfcEventHandler eventHandler;
	private final long userId;

	private final TagDiscoveredHandler tagDiscoveredHandler = new TagDiscoveredHandler();

	private boolean initDone = false;

	// state
	private final Deque<NfcMessage> messageQueue = new LinkedList<NfcMessage>();
	private final NfcMessageSplitter messageSplitter = new NfcMessageSplitter();
	private NfcMessage lastMessageSent;
	// if the task is null, it means either we did not start or we are done.
	private ExecutorService executorService = null;
	private TimeoutTask task;

	/**
	 * Instantiates a new object. Use this constructor, if you want to provide a
	 * specific {@link INfcTransceiver}.
	 * 
	 * @param eventHandler
	 *            the {@link INfcEventHandler} to listen for {@link NfcEvent}s
	 * @param activity
	 *            the application's current activity to bind the NFC service to
	 *            it
	 * @param userId
	 *            the identifier of this user (or this mobile device)
	 * @param transceiver
	 *            the transceiver responsible for writing messages and returning
	 *            the incoming response
	 */
	public NfcInitiator(INfcEventHandler eventHandler, Activity activity, long userId, INfcTransceiver transceiver) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		this.transceiver = transceiver;
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	/**
	 * Instantiates a new object. If the ACR122u USB NFC reader is attached, it
	 * will be used for the NFC. Otherwise, the build-in NFC controller will be
	 * used.
	 * 
	 * @param eventHandler
	 *            the {@link INfcEventHandler} to listen for {@link NfcEvent}s
	 * @param activity
	 *            the application's current activity to bind the NFC service to
	 *            it
	 * @param userId
	 *            the identifier of this user (or this mobile device)
	 */
	public NfcInitiator(INfcEventHandler eventHandler, Activity activity, long userId) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		if (ExternalNfcTransceiver.isExternalReaderAttached(activity)) {
			transceiver = new ExternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		} else {
			transceiver = new InternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		}
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	protected TagDiscoveredHandler tagDiscoveredHandler() {
		return tagDiscoveredHandler;
	}

	/**
	 * Binds the NFC service to this activity and initializes the NFC features.
	 * 
	 * @param activity
	 *            the application's current activity (may not be null)
	 */
	public void enable(Activity activity) {
		executorService = Executors.newSingleThreadExecutor();
		try {
			transceiver.enable(activity);
		} catch (NfcLibException e) {
			if (Config.DEBUG)
				Log.e(TAG, "enable failed: ", e);
		}
	}

	/**
	 * Unbinds the NFC service from this activity and releases it for other
	 * applications.
	 * 
	 * @param activity
	 *            the application's current activity (may not be null)
	 */
	public void disable(Activity activity) {
		if (executorService != null) {
			executorService.shutdown();
			try {
				executorService.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				if (Config.DEBUG)
					Log.e(TAG, "shutdown failed: ", e);
			}
		}
		transceiver.disable(activity);
	}

	private boolean isResume() {
		return task != null && task.isActive();
	}

	/**
	 * Initializes the NFC feature by a handshake. This method always needs to
	 * call the event handler in any case.
	 * 
	 * The handshake is as follows:
	 * send AID
	 * get NfcMessage.AID
	 * send NfcMessage.USER_ID
	 * get NfcMessage.USER_ID
	 */
	protected void initNfc() {
		try {
			if (Config.DEBUG)
				Log.d(TAG, "init NFC");

			NfcMessage initMessage = new NfcMessage(Type.AID_SELECTED).request();
			// no sequence number here, as this is a special message
			NfcMessage response = transceiver.write(initMessage);
			// --> here we can get an exception
			if (!response.isSelectAidApdu()) {
				if (Config.DEBUG)
					Log.e(TAG, "handshake unexpected: " + response);
				
				eventHandler.handleMessage(NfcEvent.INIT_FAILED, null);
				return;
			}

			if (response.version() > NfcMessage.getSupportedVersion()) {
				if (Config.DEBUG)
					Log.d(TAG, "excepted NfcMessage version "+NfcMessage.getSupportedVersion()+" but was "+response.version());
				
				eventHandler.handleMessage(NfcEvent.FATAL_ERROR, INCOMPATIBLE_VERSIONS);
				return;
			}
			
			byte[] sendUserId = Utils.longToByteArray(userId);
			byte[] sendFragLen = Utils.intToByteArray(transceiver.maxLen());
			byte[] merged = Utils.merge(sendUserId, sendFragLen);

			NfcMessage msg = new NfcMessage(NfcMessage.Type.USER_ID).payload(merged).resume(isResume());
			// no sequence number, this is considered as part of the handshake
			NfcMessage responseUserId = transceiver.write(msg);
			// --> here we can get an exception
			
			if (responseUserId.version() > NfcMessage.getSupportedVersion()) {
				if (Config.DEBUG)
					Log.d(TAG, "excepted NfcMessage version "+NfcMessage.getSupportedVersion()+" but was "+responseUserId.version());
				
				eventHandler.handleMessage(NfcEvent.FATAL_ERROR, INCOMPATIBLE_VERSIONS);
				return;
			}

			if (isResume()) {
				if (Config.DEBUG)
					Log.d(TAG, "resume");
				
				transceiveLoop(true);
			} else {
				if (Config.DEBUG)
					Log.d(TAG, "new session (no resume)");
				
				if (responseUserId.type() != NfcMessage.Type.USER_ID) {
					if (Config.DEBUG)
						Log.e(TAG, "handshake user id unexpected: " + responseUserId);
					
					initFailed(NfcEvent.INIT_FAILED);
					return;
				}
				reset();
				
				if (Config.DEBUG)
					Log.d(TAG, "handshake complete");
				
				initDone = true;
				eventHandler.handleMessage(NfcEvent.INITIALIZED, null);
			}

		} catch (Throwable t) {
			if (Config.DEBUG)
				Log.e(TAG, "init exception: ", t);
			
			initFailed(NfcEvent.INIT_FAILED);
		}
	}

	private void reset() {
		messageSplitter.clear();
		messageQueue.clear();
		lastMessageSent = null;
	}

	private void initFailed(NfcEvent event) {
		initDone = false;
		eventHandler.handleMessage(event, null);
	}

	/**
	 * Sends any byte message to the NFC communication partner and returns the
	 * response. Enable has to be called first before transceiving any data.
	 * 
	 * It is possible (with NXP controllers) that the NFC connection is aborted
	 * in the meantime and directly followed by a handshake. In this case, the
	 * response cannot be provided immediately. For a blocking behavior,
	 * Future.get() has to be called.
	 * 
	 * The message to send can be arbitrary large. If it exceeds the size
	 * limitation of the underlying NFC, it will be fragmented and reassembled
	 * internally.
	 * 
	 * @param bytes
	 *            the payload to be sent
	 * @return the future object which returns the response as soon as it is
	 *         ready (blocking)
	 * @throws IllegalArgumentException
	 *             if bytes is null or empty
	 */
	public void transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException(NULL_ARGUMENT);
		}

		if (isResume() && !messageQueue.isEmpty()) {
			throw new IllegalArgumentException("previous message did not finish, cannot send now!");
		}

		if (!initDone) {
			throw new IllegalArgumentException("init not done");
		}

		/*
		 * hint the gc that now is a good time to cleanup. Its better to cleanup
		 * before we start the timeout task
		 */
		System.gc();
		task = new TimeoutTask();
		executorService.submit(task);

		for (NfcMessage msg : messageSplitter.getFragments(bytes)) {
			messageQueue.offer(msg);
		}
		
		if (Config.DEBUG)
			Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");

		transceiveLoop(false);
	}

	private void transceiveLoop(boolean resume) {
		while (!messageQueue.isEmpty() && task.isActive()) {
			try {
				final NfcMessage request = messageQueue.peek();
				if (!resume) {
					request.sequenceNumber(lastMessageSent);
				} else {
					resume = false;
				}
				
				if (Config.DEBUG)
					Log.d(TAG, "sending: " + request);
				
				NfcMessage response = transceiver.write(request);
				
				// --> here we can get an exception
				if (response == null) {
					if (Config.DEBUG)
						Log.d(TAG, "received null");
					
					// we sent a request, the other side received it, handled
					// it, but the reply did not arrive. Response will only be
					// null when debugging. In reality, we need a timeout
					// handler.
					return;
				}
				
				if (Config.DEBUG)
					Log.d(TAG, "received: " + response);
				
				// indicate activity to not run into a timeout
				task.active();
				boolean cont = handleTransceive(request, response);
				if (!cont) {
					return;
				}
			} catch (IOException e) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				if (Config.DEBUG)
					Log.e(TAG, "tranceive exception", e);
				
				return;
			} catch (Throwable t) {
				// in any other case, make sure that we exit properly
				done();
				eventHandler.handleMessage(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
				
				if (Config.DEBUG)
					Log.e(TAG, "tranceive exception nfc", t);
				
				return;
			}
		}
	}

	/*
	 * important to return boolean, as we want to know if we want to proceed or
	 * quit the message queue.
	 */
	private boolean handleTransceive(NfcMessage request, NfcMessage response) {
		lastMessageSent = request;
		// every thing is ok, remove from queue
		final NfcMessage request2 = messageQueue.poll();
		// sanity check
		if (!request.equals(request2)) {
			if (Config.DEBUG)
				Log.e(TAG, "sync exception " + request + " / " + request2);
			
			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
			return false;
		}

		if (!validateSequence(request, response)) {
			if (Config.DEBUG)
				Log.e(TAG, "sequence error " + request + " / " + response);

			eventHandler.handleMessage(NfcEvent.FATAL_ERROR, UNEXPECTED_ERROR);
			return false;
		}

		messageSplitter.reassemble(response);
		byte[] retVal = messageSplitter.data();

		if (response.hasMoreFragments()) {
			NfcMessage toSend = new NfcMessage(Type.GET_NEXT_FRAGMENT);
			messageQueue.offer(toSend);
			return true;
		} else if (response.type() == Type.POLLING) {
			NfcMessage toSend = new NfcMessage(Type.POLLING).response();
			messageQueue.offer(toSend);
			return true;
		} else if (response.type() != Type.GET_NEXT_FRAGMENT) {
			done();
			eventHandler.handleMessage(NfcEvent.MESSAGE_RECEIVED, retVal);
			return false;
		} else {
			return true;
		}
	}

	private void done() {
		// we are done
		task.shutdown();
		messageSplitter.clear();
		messageQueue.clear();
	}

	private boolean validateSequence(final NfcMessage request, final NfcMessage response) {
		final boolean check = response.check(request);
		if (!check) {
			if (Config.DEBUG)
				Log.e(TAG, "sequence number mismatch, expected " + ((request.sequenceNumber() + 1) % 255) + ", but was: " + response.sequenceNumber());
			
			return false;
		}
		return true;
	}

	/**
	 * This class initializes the {@link NfcInitiator} as soon as a NFC tag has
	 * been discovered.
	 */
	public class TagDiscoveredHandler {
		public void tagDiscovered() throws IOException {
			initNfc();
		}
	}

	private class TimeoutTask implements Runnable {
		private final CountDownLatch latch = new CountDownLatch(1);
		private long lastAcitivity;

		public TimeoutTask() {
			active();
		}

		public boolean isActive() {
			return latch.getCount() > 0;
		}

		public void active() {
			synchronized (this) {
				lastAcitivity = System.currentTimeMillis();
			}
		}

		public void shutdown() {
			latch.countDown();
		}

		@Override
		public void run() {
			try {
				long waitTime = CONNECTION_TIMEOUT;
				while (!latch.await(waitTime, TimeUnit.MILLISECONDS)) {
					final long now = System.currentTimeMillis();
					final long idle;
					synchronized (this) {
						idle = now - lastAcitivity;
					}
					if (idle > CONNECTION_TIMEOUT) {
						if (Config.DEBUG)
							Log.d(TAG, "connection lost, idle: " + idle);
						
						latch.countDown();
						done();
						initFailed(NfcEvent.CONNECTION_LOST);
						return;
					} else {
						waitTime = CONNECTION_TIMEOUT - idle;
					}
				}

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
}
