package ch.uzh.csg.nfclib;

import java.util.Arrays;

/**
 * This is the lower layer protocol message. It is responsible for sending
 * Messages between two devices via NFC. It is build to allow message
 * fragmentation and reassembly, based on the communication device's NFC message
 * size capabilities. The status values in the header can be combined (OR, AND,
 * etc.) to transmit more than one status to the counterpart. The header
 * contains also a sequence number in order to detect multiple transmission of
 * the same messages or the lost of a message.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcMessage {

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
		EMPTY, DEFAULT, AID_SELECTED, RETRANSMIT, GET_NEXT_FRAGMENT, WAIT_FOR_ANSWER, USER_ID, READ_BINARY;
	}

	// flags
	public static final byte RESUME = 0x08; // 8
	public static final byte REQUEST = 0x10; // 16
	public static final byte START_PROTOCOL = 0x20; // 32
	public static final byte HAS_MORE_FRAGMENTS = 0x40; // 64
	public static final byte ERROR = (byte) 0x80; // -128

	// data
	private int header = 0;
	private int sequenceNumber = 0;
	private byte[] payload = new byte[0];

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
		} else if (input[0] == CLA_INS_P1_P2[0] && input[1] == CLA_INS_P1_P2[1]) {
			// we got the initial handshake
			header = Type.AID_SELECTED.ordinal();
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

	public NfcMessage(Type messageType) {
		header = messageType.ordinal();
	}

	public Type type() {
		// type is encoded in the last 3 bits
		return Type.values()[header & 0x7];
	}

	public NfcMessage payload(byte[] payload) {
		this.payload = payload;
		return this;
	}

	public byte[] payload() {
		return payload;
	}

	public NfcMessage sequenceNumber(NfcMessage previousMessage) {
		if (previousMessage == null) {
			sequenceNumber = 0;
		} else {
			sequenceNumber = (previousMessage.sequenceNumber + 1) % 255;
		}
		return this;
	}

	public int sequenceNumber() {
		return sequenceNumber;
	}

	public boolean check(NfcMessage previousMessage) {
		if (previousMessage == null) {
			return sequenceNumber == 0;
		}
		return sequenceNumber == (previousMessage.sequenceNumber + 1) % 255;
	}
	
	public boolean repeatLast(NfcMessage previousMessage) {
		if (previousMessage == null) {
			return false;
		}
		return sequenceNumber == previousMessage.sequenceNumber;
	}

	// flags
	public boolean isStartProtocol() {
		return (header & START_PROTOCOL) != 0;
	}

	public NfcMessage startProtocol(boolean startProtocol) {
		if (startProtocol) {
			header = header | START_PROTOCOL;
		} else {
			header = header & ~START_PROTOCOL;
		}
		return this;
	}

	public NfcMessage startProtocol() {
		startProtocol(true);
		return this;
	}

	public boolean hasMoreFragments() {
		return (header & HAS_MORE_FRAGMENTS) != 0;
	}

	public NfcMessage hasMoreFragments(boolean hasMoreFragments) {
		if (hasMoreFragments) {
			header = header | HAS_MORE_FRAGMENTS;
		} else {
			header = header & ~HAS_MORE_FRAGMENTS;
		}
		return this;
	}

	public NfcMessage setMoreFragments() {
		hasMoreFragments(true);
		return this;
	}

	public boolean isError() {
		return (header & ERROR) != 0;
	}

	public NfcMessage error(boolean error) {
		if (error) {
			header = header | ERROR;
		} else {
			header = header & ~ERROR;
		}
		return this;
	}

	public NfcMessage error() {
		error(true);
		return this;
	}

	public boolean isRequest() {
		return (header & REQUEST) != 0;
	}

	public NfcMessage request(boolean request) {
		if (request) {
			header = header | REQUEST;
		} else {
			header = header & ~REQUEST;
		}
		return this;
	}
	
	public NfcMessage request() {
		request(true);
		return this;
	}
	
	public boolean isResponse() {
		return !isRequest();
	}

	public NfcMessage response() {
		request(false);
		return this;
	}

	public NfcMessage response(boolean response) {
		return request(!response);
	}
	
	public boolean isResume() {
		return (header & RESUME) != 0;
	}

	public NfcMessage resume(boolean resume) {
		if (resume) {
			header = header | RESUME;
		} else {
			header = header & ~RESUME;
		}
		return this;
	}
	
	public NfcMessage resume() {
		resume(true);
		return this;
	}

	public boolean isReadBinary() {
		return type() == Type.READ_BINARY;
	}

	public boolean isSelectAidApdu() {
		// does not matter if request or response
		return type() == Type.AID_SELECTED;
	}

	// serialization
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

	public boolean isEmpty() {
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
			sb.append("head: ").append(Integer.toHexString(header));
			sb.append("/").append(sequenceNumber);
			sb.append(",len:").append(payload.length);
			sb.append(",res:").append(isResume());
			sb.append(",req:").append(isRequest());
		}
		return sb.toString();
	}
}
