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
	public static final byte EMPTY = 0;
	public static final byte DEFAULT = 1;
	public static final byte AID_SELECTED = 2;
	public static final byte RETRANSMIT = 3;
	public static final byte GET_NEXT_FRAGMENT = 4;
	public static final byte WAIT_FOR_ANSWER = 5;
	public static final byte USER_ID = 6;
	public static final byte UNUSED_2 = 7;

	// flags
	public static final byte UNUSED_3 = 0x08; // 8
	public static final byte UNUSED_4 = 0x10; // 16
	public static final byte START_PROTOCOL = 0x20; // 32
	public static final byte HAS_MORE_FRAGMENTS = 0x40; // 64
	public static final byte ERROR = (byte) 0x80; // -128

	// data
	private int header = 0;
	private int sequenceNumber = 0;
	private byte[] payload = new byte[0];
	private boolean readBinary = false;
	private boolean selectAidApdu = false;

	public NfcMessage type(byte messageType) {
		if (messageType > UNUSED_2) {
			throw new IllegalArgumentException("largest message type is " + UNUSED_2);
		}
		// preserve only the flags
		header = header & 0xF8;
		header = header | messageType;
		return this;
	}

	public int type() {
		// return the last 3 bits
		return header & 0x7;
	}

	public NfcMessage payload(byte[] payload) {
		this.payload = payload;
		return this;
	}

	public byte[] payload() {
		return payload;
	}

	public NfcMessage sequenceNumber(NfcMessage previousMessage) {
		if(previousMessage == null) {
			this.sequenceNumber = 0;
		} else {
			this.sequenceNumber = (previousMessage.sequenceNumber + 1) % 255;
		}
		return this;
	}

	public int sequenceNumber() {
		return sequenceNumber;
	}

	public boolean check(NfcMessage futureMessage) {
		return (this.sequenceNumber + 1) % 255 == futureMessage.sequenceNumber;
	}

	// flags
	public boolean isStartProtocol() {
		return (header & START_PROTOCOL) > 0;
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
		return (header & HAS_MORE_FRAGMENTS) > 0;
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
		return (header & ERROR) > 0;
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
	
	public boolean isReadBinary() {
		return readBinary;
	}

	public NfcMessage readBinary(boolean readBinary) {
		this.readBinary = readBinary;
		return this;
	}

	public NfcMessage readBinary() {
		readBinary(true);
		return this;
	}
	
	public boolean isSelectAidApdu() {
		return selectAidApdu || type() == AID_SELECTED;
	}

	public NfcMessage selectAidApdu(boolean selectAidApdu) {
		this.selectAidApdu = selectAidApdu;
		return this;
	}

	public NfcMessage selectAidApdu() {
		selectAidApdu(true);
		return this;
	}

	// serialization
	public byte[] bytes() {
		if(selectAidApdu) {
			return CLA_INS_P1_P2_AID_MBPS;
		} else if (readBinary) {
			return new byte[]{0x00};
		}
		
		final int len = payload.length;
		byte[] output = new byte[HEADER_LENGTH + len];
		output[0] = (byte) header;
		output[1] = (byte) sequenceNumber;
		System.arraycopy(payload, 0, output, HEADER_LENGTH, len);
		return output;
	}

	public boolean isEmpty() {
		return header == 0 && sequenceNumber == 0 && payload.length == 0 && !readBinary &&  !selectAidApdu;
	}

	public NfcMessage bytes(byte[] input) {
		final int len = input.length;
		if (!isEmpty() || input == null || len < HEADER_LENGTH) {
			throw new IllegalArgumentException("Message is empty, no input, or not enough data");
		}
		if(Arrays.equals(input, READ_BINARY)) {
			/*
			 * Based on the reported issue in
			 * https://code.google.com/p/android/issues/detail?id=58773, there is a
			 * failure in the Android NFC protocol. The IsoDep might transceive a
			 * READ BINARY, if the communication with the tag (or HCE) has been idle
			 * for a given time (125ms as mentioned on the issue report). This idle
			 * time can be changed with the EXTRA_READER_PRESENCE_CHECK_DELAY
			 * option.
			 */
			readBinary = true;
		} else if (input[0] == CLA_INS_P1_P2[0] && input[1] == CLA_INS_P1_P2[1]) {
			//we got the initial handshake
			selectAidApdu = true;
		} else {
			//this is now a custom message
			header = input[0];
			sequenceNumber = input[1];
		
			if (len > HEADER_LENGTH) {
				final int payloadLen = len - HEADER_LENGTH;
				payload = new byte[payloadLen];
				System.arraycopy(input, HEADER_LENGTH, payload, 0, payloadLen);
			}
		}
		return this;
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
		}
		return sb.toString();
	}

}
