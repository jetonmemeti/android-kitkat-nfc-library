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
	
	public static final byte DEFAULT = 0x00;
	public static final byte AID_SELECTED =	0x01;
	public static final byte START_PROTOCOL = 0x02;
	public static final byte RETRANSMIT = 0x03;
	public static final byte HAS_MORE_FRAGMENTS = 0x08;
	public static final byte GET_NEXT_FRAGMENT = 0x10;
	public static final byte WAIT_FOR_ANSWER = 0x20;
	public static final byte ERROR = 0x40;
	
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
	 * 0xFF is returned.
	 */
	public byte getSequenceNumber() {
		if (getData() == null || getData().length == 0)
			return (byte) 0xFF;
		else
			return getData()[1];
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("NfcMessage: ");
		sb.append("head: ");
		sb.append(", status: ").append(Integer.toHexString(getData()[0] & 0xFF));
		sb.append(", sequence: ").append(Integer.valueOf(getData()[1] & 0xFF));
		sb.append("/ payload length:").append(getData().length-HEADER_LENGTH);
		return sb.toString();
	}
	
}
