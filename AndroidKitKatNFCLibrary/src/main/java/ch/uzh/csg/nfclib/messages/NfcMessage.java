package ch.uzh.csg.nfclib.messages;

import java.util.Arrays;

/**
 * This is the NFC layer protocol message. It is responsible for sending
 * Messages between two devices over NFC. It is build to allow message
 * fragmentation and reassembly, based on the communication device's NFC message
 * size capabilities.
 * 
 * The flags in the header can be combined (OR, AND, etc.) to transmit more than
 * one status to the counterpart. The header contains also a sequence number in
 * order to detect multiple transmission of the same messages or a message loss.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcMessage {
	
	/*
	 * The number of the first version is 0. Future versions might be 1, 2, or
	 * 3. Afterwards, a new byte has to be allocated for to contain the version
	 * number.
	 */
	private static final int VERSION = 0;

	/*
	 * When a remote NFC device wants to talk to your service, it sends a
	 * so-called "SELECT AID" APDU as defined in the ISO/IEC 7816-4
	 * specification.
	 */
	public static final byte[] CLA_INS_P1_P2 = { 0x00, (byte) 0xA4, 0x04, 0x00 };
	public static final byte[] AID_MBPS = { (byte) 0xF0, (byte) 0xF0, 0x07, 0x77, (byte) 0xFF, 0x55, 0x11 };
	public static final byte[] CLA_INS_P1_P2_AID_MBPS;
	static {
		// for details see:
		// http://www.cardwerk.com/smartcards/smartcard_standard_ISO7816-4_9_application-independent_card_services.aspx
		byte Lc = (byte) AID_MBPS.length;
		// we return 2 bytes
		byte Le = 2;
		CLA_INS_P1_P2_AID_MBPS = new byte[] { CLA_INS_P1_P2[0], CLA_INS_P1_P2[1], CLA_INS_P1_P2[2], CLA_INS_P1_P2[3],
		        Lc, AID_MBPS[0], AID_MBPS[1], AID_MBPS[2], AID_MBPS[3], AID_MBPS[4], AID_MBPS[5], AID_MBPS[6], Le };
	}

	public static final byte[] READ_BINARY = { 0x00, (byte) 0xB0, 0x00, 0x00, 0x01 };

	public static final int HEADER_LENGTH = 2;

	// messages, uses 3 bits at most
	public enum Type {
		DEFAULT, ERROR, AID, GET_NEXT_FRAGMENT, USER_ID, READ_BINARY, POLLING, UNUSED;
	}

	// flags
	public static final byte RESUME = 0x20;
	public static final byte REQUEST = 0x40;
	public static final byte HAS_MORE_FRAGMENTS = (byte) 0x80;
	
	// data
	private int header = 0;
	private int sequenceNumber = 0;
	private byte[] payload = new byte[0];

	/**
	 * Sets the data of this message and returns it.
	 * 
	 * @param input
	 *            the header as well as the payload of this {@link NfcMessage}
	 */
	public NfcMessage(byte[] input) {
		final int len = input.length;
		if (Arrays.equals(input, READ_BINARY)) {
			/*
			 * Based on the reported issue in
			 * https://code.google.com/p/android/issues/detail?id=58773, there
			 * is a failure in the Android NFC protocol. The IsoDep might
			 * transceive a READ BINARY, if the communication with the tag (or
			 * HCE) has been idle for a given time (125ms as mentioned on the
			 * issue report). This idle time can be changed with the
			 * EXTRA_READER_PRESENCE_CHECK_DELAY option.
			 */
			header = Type.READ_BINARY.ordinal();
			header = header | (VERSION << 3);
		} else if (input[0] == CLA_INS_P1_P2[0] && input[1] == CLA_INS_P1_P2[1]) {
			// we got the initial handshake
			header = Type.AID.ordinal();
			header = header | (VERSION << 3);
		} else {
			// this is now a custom message
			header = input[0];
			sequenceNumber = input[1] & 0xFF;

			if (len > HEADER_LENGTH) {
				final int payloadLen = len - HEADER_LENGTH;
				payload = new byte[payloadLen];
				System.arraycopy(input, HEADER_LENGTH, payload, 0, payloadLen);
			}
		}
	}

	/**
	 * Sets the type of this message and returns it.
	 * 
	 * @param messageType
	 *            the {@link Type} to set
	 */
	public NfcMessage(Type messageType) {
		header = messageType.ordinal();
		header = header | (VERSION << 3);
	}

	/**
	 * Returns the type of this message.
	 */
	public Type type() {
		// type is encoded in the last 3 bits
		return Type.values()[header & 0x7];
	}
	
	/**
	 * Returns the version of this message.
	 */
	public int version() {
		return (header >>> 3) & 0x03;
	}
	
	/**
	 * Returns the highest supported version of Nfc Messages. If version()
	 * returns an higher version that this method, we cannot process that
	 * message.
	 */
	public static int getSupportedVersion() {
		return VERSION;
	}
	
	/**
	 * Sets the payload of this message and returns it.
	 * 
	 * @param payload
	 *            the payload to set
	 */
	public NfcMessage payload(byte[] payload) {
		this.payload = payload;
		return this;
	}

	/**
	 * Returns the payload of this message.
	 */
	public byte[] payload() {
		return payload;
	}

	/**
	 * Sets the sequence number of this message and returns it.
	 * 
	 * @param previousMessage
	 *            the previous {@link NfcMessage} which has been sent over NFC
	 * @return this message with the appropriate sequence number
	 */
	public NfcMessage sequenceNumber(NfcMessage previousMessage) {
		if (previousMessage == null) {
			sequenceNumber = 0;
		} else {
			sequenceNumber = (previousMessage.sequenceNumber + 1) % 255;
		}
		return this;
	}

	/**
	 * Returns the sequence number of this message.
	 */
	public int sequenceNumber() {
		return sequenceNumber;
	}

	/**
	 * Verifies that this message has a correct sequence number based on the
	 * previous {@link NfcMessage} sent or received.
	 * 
	 * @param previousMessage
	 *            the previous {@link NfcMessage} sent or received
	 * @return true if the sequence number is by one larger than the previous,
	 *         false otherwise
	 */
	public boolean check(NfcMessage previousMessage) {
		final int check;
		if (previousMessage == null) {
			check = -1;
		} else {
			check = previousMessage.sequenceNumber;
		}
		return sequenceNumber == (check + 1) % 255;
	}
	
	/**
	 * Returns true, if the sequence number of this message is equals to the
	 * sequence number of the previous message. If the previous message is null,
	 * false is returned.
	 * 
	 * @param previousMessage
	 *            the last {@link NfcMessage} sent or received
	 */
	public boolean repeatLast(NfcMessage previousMessage) {
		if (previousMessage == null) {
			return false;
		}
		return sequenceNumber == previousMessage.sequenceNumber;
	}

	/**
	 * Returns true if this message has more fragments which need to be
	 * reassembled after receiving. (Returns if the given flag is set).
	 */
	public boolean hasMoreFragments() {
		return (header & HAS_MORE_FRAGMENTS) != 0;
	}

	private NfcMessage hasMoreFragments(boolean hasMoreFragments) {
		if (hasMoreFragments) {
			header = header | HAS_MORE_FRAGMENTS;
		} else {
			header = header & ~HAS_MORE_FRAGMENTS;
		}
		return this;
	}

	/**
	 * Sets the has more fragments flag of this message and returns it.
	 */
	public NfcMessage setMoreFragments() {
		hasMoreFragments(true);
		return this;
	}

	/**
	 * Returns true if the type of this message is error.
	 */
	public boolean isError() {
		return type() == Type.ERROR;
	}

	/**
	 * Returns true if this message is a request (i.e., if the request flag of
	 * this message is set).
	 */
	public boolean isRequest() {
		return (header & REQUEST) != 0;
	}

	private NfcMessage request(boolean request) {
		if (request) {
			header = header | REQUEST;
		} else {
			header = header & ~REQUEST;
		}
		return this;
	}
	
	/**
	 * Sets the request flag of this message and returns it.
	 */
	public NfcMessage request() {
		request(true);
		return this;
	}
	
	/**
	 * Returns true if this message is a response (i.e., if the request lag of
	 * this message is not set).
	 */
	public boolean isResponse() {
		return !isRequest();
	}

	/**
	 * Sets this message as a response (i.e., unsets the request flag).
	 */
	public NfcMessage response() {
		request(false);
		return this;
	}

	/**
	 * Returns true, if this is a resume message (i.e., the resume flag is set).
	 */
	public boolean isResume() {
		return (header & RESUME) != 0;
	}

	/**
	 * Sets the resume flag of this message to the given value and returns it.
	 * 
	 * @param resume
	 *            true or false
	 */
	public NfcMessage resume(boolean resume) {
		if (resume) {
			header = header | RESUME;
		} else {
			header = header & ~RESUME;
		}
		return this;
	}
	
	/**
	 * Sets the resume flag of this message and returns it.
	 */
	public NfcMessage resume() {
		resume(true);
		return this;
	}

	/**
	 * Returns true if the type of this message is read binary.
	 */
	public boolean isReadBinary() {
		return type() == Type.READ_BINARY;
	}

	/**
	 * Returns true if the type of this message is aid selected.
	 */
	public boolean isSelectAidApdu() {
		// does not matter if request or response
		return type() == Type.AID;
	}

	/**
	 * Returns the bytes of this message (i.e., serializes it).
	 */
	public byte[] bytes() {
		if (isSelectAidApdu() && isRequest()) {
			return CLA_INS_P1_P2_AID_MBPS;
		} else if (isReadBinary()) {
			return new byte[] { 0x00 };
		}

		final int len = payload.length;
		byte[] output = new byte[HEADER_LENGTH + len];
		output[0] = (byte) header;
		output[1] = (byte) sequenceNumber;
		System.arraycopy(payload, 0, output, HEADER_LENGTH, len);
		return output;
	}

	private boolean isEmpty() {
		return header == 0 && sequenceNumber == 0 && payload.length == 0;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof NfcMessage)) {
			return false;
		}
		NfcMessage m = (NfcMessage) o;
		return m.header == header && m.sequenceNumber == sequenceNumber && Arrays.equals(m.payload, payload);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("NfcMsg: ");
		if (isEmpty()) {
			sb.append("empty");
		} else {
			sb.append("type: ").append(type().toString());
			sb.append("/").append(sequenceNumber);
			sb.append(",len:").append(payload.length);
			sb.append(",res:").append(isResume());
			sb.append(",req:").append(isRequest());
		}
		return sb.toString();
	}
	
}
