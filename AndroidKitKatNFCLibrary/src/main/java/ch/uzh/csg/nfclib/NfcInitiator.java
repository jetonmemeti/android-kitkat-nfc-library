package ch.uzh.csg.nfclib;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcMessage.Type;

//TODO: javadoc

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
public class NfcInitiator {

	public static final int CONNECTION_TIMEOUT = 500;
	public static final int SESSION_RESUME_THRESHOLD = 500;
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";
	private static final String TAG = "ch.uzh.csg.nfclib.NfcInitiator";

	private final INfcTransceiver transceiver;
	private final NfcEvent eventHandler;
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
	private ByteCallable byteCallable;

	public NfcInitiator(NfcEvent eventHandler, Activity activity, long userId, INfcTransceiver transceiver) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		this.transceiver = transceiver;
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	public NfcInitiator(NfcEvent eventHandler, Activity activity, long userId) {
		this.eventHandler = eventHandler;
		this.userId = userId;
		if (ExternalNfcTransceiver.isExternalReaderAttached(activity)) {
			transceiver = new ExternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		} else {
			transceiver = new InternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		}
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	public TagDiscoveredHandler tagDiscoveredHandler() {
		return tagDiscoveredHandler;
	}

	public INfcTransceiver nfcTransceiver() {
		return transceiver;
	}

	public void enable(Activity activity) {
		executorService = Executors.newSingleThreadExecutor();
		try {
			transceiver.enable(activity);
		} catch (NfcLibException e) {
			Log.e(TAG, "enable failed: ", e);
		}
	}

	public void disable(Activity activity) {
		if (executorService != null) {
			executorService.shutdown();
			try {
				executorService.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Log.e(TAG, "shutdown failed: ", e);
			}
		}
		transceiver.disable(activity);
	}

	private boolean isResume() {
		return task != null && task.isActive();
	}

	/**
	 * The init NFC messages that sends first a handshake. This method always
	 * needs to call the evenhandler in any case. The handshake is as follows:
	 * send AID -> get NfcMessage.AID / send NfcMessage.USER_ID -> get
	 * NfcMessage.USER_ID ok
	 */
	void initNfc() {
		try {
			Log.d(TAG, "init NFC");

			NfcMessage initMessage = new NfcMessage(Type.AID_SELECTED).request();
			// no sequence number here, as this is a special message
			NfcMessage response = transceiver.write(initMessage);
			// //--> here we can get an exception

			if (!response.isSelectAidApdu()) {
				Log.e(TAG, "handshake unexpecetd: " + response);
				eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
				return;
			}

			byte[] sendUserId = Utils.longToByteArray(userId);
			byte[] sendFragLen = Utils.intToByteArray(transceiver.maxLen());
			byte[] merged = Utils.merge(sendUserId, sendFragLen);

			NfcMessage msg = new NfcMessage(NfcMessage.Type.USER_ID).payload(merged).resume(isResume());
			// no sequence number, this is considered as part of the handshake
			NfcMessage responseUserId = transceiver.write(msg);
			// //--> here we can get an exception

			if (isResume()) {
				Log.d(TAG, "resume!");
				transceiveLoop(true);
			} else {
				Log.d(TAG, "do not resume, fresh session");
				if (responseUserId.type() != NfcMessage.Type.USER_ID) {
					Log.e(TAG, "handshake user id unexpecetd: " + responseUserId);
					initFailed(NfcEvent.Type.INIT_FAILED);
					return;
				}
				reset();
				Log.d(TAG, "handshake completed!");
				initDone = true;
				eventHandler.handleMessage(NfcEvent.Type.INITIALIZED, null);
			}

		} catch (Throwable t) {
			t.printStackTrace();
			Log.e(TAG, "init exception: ", t);
			initFailed(NfcEvent.Type.INIT_FAILED);
		}
	}

	private void reset() {
		messageSplitter.clear();
		messageQueue.clear();
		lastMessageSent = null;
	}

	private void initFailed(NfcEvent.Type type) {
		initDone = false;
		eventHandler.handleMessage(type, null);
		byteCallable.set(null);
	}

	// TODO: try to rething future here
	public Future<byte[]> transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException(NULL_ARGUMENT);
		}

		if (isResume() && !messageQueue.isEmpty()) {
			throw new IllegalArgumentException("previous message did not finish, cannot send now!");
		}

		if (!initDone) {
			throw new IllegalArgumentException("init not done");
		}

		byteCallable = new ByteCallable();
		FutureTask<byte[]> futureTask = new FutureTask<byte[]>(byteCallable);
		byteCallable.future(futureTask);

		// hint the gc that now is a good time to cleanup. Its better to cleanup
		// before we start the timeout task
		System.gc();
		task = new TimeoutTask();
		executorService.submit(task);

		for (NfcMessage msg : messageSplitter.getFragments(bytes)) {
			messageQueue.offer(msg);
		}
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");

		transceiveLoop(false);
		return futureTask;
	}

	private void transceiveLoop(boolean resume) {
		while (!messageQueue.isEmpty() && task.isActive()) {
			try {
				final NfcMessage request1 = messageQueue.peek();
				if (!resume) {
					request1.sequenceNumber(lastMessageSent);
				} else {
					resume = false;
				}
				long start = System.currentTimeMillis();
				NfcMessage response = transceiver.write(request1);
				Log.e(TAG, "time to write: " + (System.currentTimeMillis() - start));
				Log.d(TAG, "trans request: " + request1);
				Log.d(TAG, "trans response: " + response);
				// //--> here we can get an exception
				if (response == null) {
					// we sent a request, the other side received it, handled
					// it, but the reply did not arrive. Response will only be
					// null when
					// debugging. In reality, we need a timeout handler
					return;
				}
				// inidcate acitivity to not run into a timeout
				task.active();
				boolean cont = handleTransceive(request1, response);
				if (!cont) {
					return;
				}
				//TODO: this catch block is obsolete, since never thrown!
//			} catch (NfcLibException e) {
//				done(null);
//				eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, e);
//				byteCallable.set(null);
//				Log.e(TAG, "tranceive exception nfc", e);
//				return;
			} catch (IOException e) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				e.printStackTrace();
				Log.e(TAG, "tranceive exception", e);

				return;
			}
		}
	}

	// important to return boolean, as we want to know if we want to proceed or
	// quit the message queue.
	private boolean handleTransceive(NfcMessage request1, NfcMessage response) {

		lastMessageSent = request1;
		// every thing is ok, remove from queue
		final NfcMessage request2 = messageQueue.poll();
		// sanity check
		if (!request1.equals(request2)) {
			Log.e(TAG, "sync exception " + request1 + " / " + request2);
		}

		if (!validateSequence(request1, response)) {
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, "sequence error");
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
			done(retVal);
			eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, retVal);
			byteCallable.set(retVal);
			return false;
		} else {
			return true;
		}
	}

	private void done(byte[] retVal) {
		// we are done
		task.shutdown();
		messageSplitter.clear();
		messageQueue.clear();
	}

	private boolean validateSequence(final NfcMessage request, final NfcMessage response) {
		final boolean check = response.check(request);
		if (!check) {
			Log.e(TAG, "sequence number mismatch, expected " + ((request.sequenceNumber() + 1) % 255) + ", but was: "
			        + response.sequenceNumber());
			return false;
		}
		return true;
	}

	private static class ByteCallable implements Callable<byte[]> {
		private byte[] value;
		private FutureTask<byte[]> futureTask;

		public void set(byte[] value) {
			synchronized (this) {
				this.value = value;
			}
			futureTask.run();
		}

		public void future(FutureTask<byte[]> futureTask) {
			synchronized (this) {
				this.futureTask = futureTask;
			}
		}

		@Override
		public byte[] call() throws Exception {
			synchronized (this) {
				return value;
			}
		}
	};

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
						Log.e(TAG, "connection lost, idle: " + idle);
						latch.countDown();
						done(null);
						initFailed(NfcEvent.Type.CONNECTION_LOST);
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
