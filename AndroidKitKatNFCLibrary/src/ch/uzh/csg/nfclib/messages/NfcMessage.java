package ch.uzh.csg.nfclib.messages;


/**
 * This is the lower layer protocol message. It is responsible for sending
 * Messages between two devices via NFC. It is build to allow message
 * fragmentation and reassembly, based on the communication device's NFC message
 * size capabilities. The status values in the header can be combined (OR, AND,
 * etc.) to transmit more than one status to the counterpart. The header
 * contains also a sequence number in order to detect multiple transmission of
 * the same messages or the lost of a message.
 */
public class NfcMessage extends ProtocolMessage {
	
	public static final byte DEFAULT = 0x01;
	public static final byte AID_SELECTED =	0x02;
	public static final byte START_PROTOCOL = 0x04;
	public static final byte RETRANSMIT = 0x08;
	public static final byte HAS_MORE_FRAGMENTS = 0x10;
	public static final byte GET_NEXT_FRAGMENT = 0x20;
	public static final byte WAIT_FOR_ANSWER = 0x40;
	public static final byte ERROR = (byte) 0x80; //-128
	
	public static final int HEADER_LENGTH = 2;
	
	public NfcMessage(byte[] data) {
		setHeaderLength(HEADER_LENGTH);
		setData(data);
	}
	
	/**
	 * Creates a new NfcMessage.
	 * 
	 * @param status
	 *            the status to be contained in the header
	 * @param sequenceNumber
	 *            the sequence number of this message to be contained in the
	 *            header
	 * @param payload
	 *            the payload of this message
	 */
	public NfcMessage(byte status, byte sequenceNumber, byte[] payload) {
		byte[] data;
		if (payload != null && payload.length > 0) {
			data = new byte[payload.length+HEADER_LENGTH];
			System.arraycopy(payload, 0, data, HEADER_LENGTH, payload.length);
		} else {
			data = new byte[HEADER_LENGTH];
		}
		
		data[0] = status;
		data[1] = sequenceNumber;
		
		setHeaderLength(HEADER_LENGTH);
		setData(data);
	}
	
	/**
	 * Returns the sequence number of this NfcMessage. If the data is null, then
	 * 0x00 is returned.
	 */
	public byte getSequenceNumber() {
		if (getData() == null || getData().length < 2)
			return 0x00;
		else
			return getData()[1];
	}
	
	/**
	 * Sets the sequence number of this NfcMessage to the given value (only if
	 * data is not null and is at least as long as the header length).
	 * 
	 * @param sqNr
	 *            the new sequence number of this NfcMessage
	 */
	public void setSequenceNumber(byte sqNr) {
		byte[] data = getData();
		if (data != null && data.length > 1) {
			data[1] = sqNr;
		}
	}
	
	/**
	 * Returns if this NfcMessage is requesting the next fragment. (Returns if
	 * the header is equals to NfcMessage.GET_NEXT_FRAGMENT).
	 */
	public boolean requestsNextFragment() {
		return (getStatus() & NfcMessage.GET_NEXT_FRAGMENT) == NfcMessage.GET_NEXT_FRAGMENT;
	}
	
	/**
	 * Returns if this NfcMessage is followed by other other messages which need
	 * to be reassembled. (Returns if the header is equals to
	 * NfcMessage.HAS_MORE_FRAGMENTS).
	 */
	public boolean hasMoreFragments() {
		return (getStatus() & NfcMessage.HAS_MORE_FRAGMENTS) == NfcMessage.HAS_MORE_FRAGMENTS;
	}
	
	/**
	 * Returns if this NfcMessage is requesting a retransmission of the last
	 * message. (Returns if the header is equals to NfcMessage.RETRANSMIT).
	 */
	public boolean requestsRetransmission() {
		return (getStatus() & NfcMessage.RETRANSMIT) == NfcMessage.RETRANSMIT;
	}
	
	/**
	 * Returns if this NfcMessage requests starting a new session. If this is
	 * not the case, than the old session has to be resumed. (Returns if the
	 * corresponding bit (see NfcMessage.START_PROTOCOL) in the header is set to
	 * 1 ).
	 */
	public boolean isStartProtocol() {
		return (getStatus() & NfcMessage.START_PROTOCOL) == NfcMessage.START_PROTOCOL;
	}
	
	/**
	 * Returns if this NfcMessage contains the AID_SELECTED bit.
	 */
	public boolean aidSelected() {
		return (getStatus() & NfcMessage.AID_SELECTED) == NfcMessage.AID_SELECTED;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("NfcMessage: ");
		if (getData() == null || getData().length < 2) {
			sb.append("corrupt message!");
			return sb.toString();
		} else {
			sb.append("head: ");
			sb.append(", status: ").append(Integer.toHexString(getData()[0] & 0xFF));
			sb.append(", sequence: ").append(Integer.valueOf(getData()[1] & 0xFF));
			sb.append("/ payload length:").append(getData().length-HEADER_LENGTH);
			return sb.toString();
		}
	}
	
}
