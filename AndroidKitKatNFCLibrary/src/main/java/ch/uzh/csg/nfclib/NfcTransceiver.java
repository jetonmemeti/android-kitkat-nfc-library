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
	public static final String NULL_ARGUMENT = "The message is null";
	public static final String NFCTRANSCEIVER_NOT_CONNECTED = "Could not write message, NfcTransceiver is not connected.";
	public static final String UNEXPECTED_ERROR = "An error occured while transceiving the message.";
	private static final String TAG = "##NFC## NfcTransceiver";

	private final NfcMessageSplitter messageSplitter = new NfcMessageSplitter();
	private final NfcTransceiverImpl transceiver;
	private final NfcEvent eventHandler;
	private final long userId;

	private final TagDiscoveredHandler tagDiscoveredHandler = new TagDiscoveredHandler();

	private ExecutorService executorService = null;

	// state
	private final Deque<NfcMessage> messageQueue = new LinkedList<NfcMessage>();
	private final NfcMessageReassembler messageReassembler = new NfcMessageReassembler();
	private NfcMessage lastMessageSent;
	private NfcMessage lastMessageReceived;
	private int retry = 0;
	// if the task is null, it means either we did not start or we are done.
	private TimeoutTask task;
	private ByteCallable byteCallable;

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
			transceiver = new ExternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		} else {
			transceiver = new InternalNfcTransceiver(eventHandler, tagDiscoveredHandler);
		}
		messageSplitter.maxTransceiveLength(transceiver.maxLen());
	}

	private void reset() {
		messageReassembler.clear();
		messageQueue.clear();
		lastMessageSent = null;
		lastMessageReceived = null;
		retry = 0;
	}

	public TagDiscoveredHandler tagDiscoveredHandler() {
		return tagDiscoveredHandler;
	}

	public NfcTransceiverImpl nfcTransceiver() {
		return transceiver;
	}

	public void enable(Activity activity) {
		disable(activity);
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
		try {
			transceiver.disable(activity);
		} catch (IOException e) {
			Log.e(TAG, "disable failed: ", e);
		}
	}

	private boolean isResume() {
		return task != null;
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
				if (retry++ > MAX_RETRY) {
					Log.e(TAG, "retry failed: " + responseUserId);
					eventHandler.handleMessage(NfcEvent.Type.INIT_RETRY_FAILED, null);
					return;
				}
				transceiveQueue(true);
			} else {
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
			t.printStackTrace();
			eventHandler.handleMessage(NfcEvent.Type.INIT_FAILED, null);
		}
	}

	public synchronized Future<byte[]> transceive(byte[] bytes) throws IllegalArgumentException {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException(NULL_ARGUMENT);
		}

		if (task != null || !messageQueue.isEmpty()) {
			throw new IllegalArgumentException("previous message did not finish, cannot send now!");
		}

		byteCallable = new ByteCallable();
		FutureTask<byte[]> futureTask = new FutureTask<byte[]>(byteCallable);
		byteCallable.future(futureTask);

		task = new TimeoutTask();
		executorService.submit(task);

		for (NfcMessage msg : messageSplitter.getFragments(bytes)) {
			messageQueue.offer(msg);
		}
		Log.d(TAG, "writing: " + bytes.length + " bytes, " + messageQueue.size() + " fragments");

		transceiveQueue(false);
		return futureTask;
	}

	private void transceiveQueue(boolean resume) {
		while (!messageQueue.isEmpty()) {
			
			try {
				final NfcMessage request1 = messageQueue.peek();
				if (!resume) {
					request1.sequenceNumber(lastMessageSent);
				} else {
					resume = false;
				}
				NfcMessage response = transceiver.write(request1);
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
				handleTransceive(request1, response);
			} catch (NfcLibException e) {
				eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, e);
				done(null);
				Log.e(TAG, "tranceive exception nfc", e);
				return;
			} catch (Throwable t) {
				/*
				 * This might occur due to a connection lost and can be followed
				 * by a nfc handshake to re-init the nfc connection. Therefore
				 * the session resume thread waits before returning the response
				 * or an error message to the event handler.
				 */
				Log.e(TAG, "tranceive exception", t);
				return;
			}
		}
	}

	private void handleTransceive(NfcMessage request1, NfcMessage response) {

		lastMessageSent = request1;
		// every thing is ok, remove from queue
		final NfcMessage request2 = messageQueue.poll();
		// sanity check
		if (!request1.equals(request2)) {
			Log.e(TAG, "sync exception");
		}

		if (!validateSequence(response)) {
			// TODO thomas: if sq nr is not ok, you fire the fatal_error event
			// in checkSequence, then you fire it here again. afterwards, you
			// continue sending the next message from the queue. if you have
			// another 3 messages, you will receive tons of other fatal_error
			// events in your listener. insert a break
			// condition in transceiveQueue or smthg.
			// Again, fatal_error means we can't do anything to restore the
			// session, so abort everything, and receive the event only once.
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, "sequence error");
			return;
		}

		// the last message has been sent to the HCE, now we receive the
		// response
		if (!request1.hasMoreFragments() && request1.payload().length > 0) {
			eventHandler.handleMessage(NfcEvent.Type.MESSAGE_SENT, request1);
		}

		messageReassembler.handleReassembly(response);
		byte[] retVal = messageReassembler.data();

		if (response.hasMoreFragments()) {
			NfcMessage toSend = new NfcMessage(Type.GET_NEXT_FRAGMENT);
			messageQueue.offer(toSend);
		} else {
			// TODO thomas: if retVal.length==0, and no ERROR or so, then we
			// should still fire the MESSAGE_RECEIVED event. this indicates that
			// the protocol has finished correctly, but the counterpart did not
			// send anything. (someone might design a protocol with an empty
			// message)
			if (retVal.length > 0) {
				eventHandler.handleMessage(NfcEvent.Type.MESSAGE_RECEIVED, retVal);
				done(retVal);
			}
			messageReassembler.clear();
		}
	}

	private void done(byte[] retVal) {
		// we are done
		task.shutdown();
		task = null;
		messageReassembler.clear();
		messageQueue.clear();
		retry = 0;
		byteCallable.set(retVal);
	}

	private boolean validateSequence(NfcMessage response) {

		boolean check = response.check(lastMessageReceived);
		NfcMessage was = lastMessageReceived;
		lastMessageReceived = response;
		if (!check) {
			check = response.check(was);
			Log.e(TAG, "sequence number mismatch");
			// TODO thomas: do not fire events from within the methods when you
			// do so where they are called. we do not want to get two or more
			// fatal_error events for the same problem
			eventHandler.handleMessage(NfcEvent.Type.FATAL_ERROR, response.toString());
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
				long waitTime = NfcTransceiver.SESSION_RESUME_THRESHOLD;
				boolean timeout = false;
				while (latch.getCount() > 0) {
					latch.await(waitTime, TimeUnit.MILLISECONDS);
					if (System.currentTimeMillis() - lastAcitivity < NfcTransceiver.SESSION_RESUME_THRESHOLD) {
						waitTime = System.currentTimeMillis() - lastAcitivity;
					} else {
						timeout = true;
						break;
					}
				}

				if (timeout && latch.getCount() > 0) {
					Log.e(TAG, "connection lost");
					eventHandler.handleMessage(NfcEvent.Type.CONNECTION_LOST, null);
					done(null);
				}

			} catch (Throwable t) {
				t.printStackTrace();
			}

		}

	}
}
