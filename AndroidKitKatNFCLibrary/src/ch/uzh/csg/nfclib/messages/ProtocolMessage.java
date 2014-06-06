package ch.uzh.csg.nfclib.messages;

import java.util.Arrays;

/**
 * This is the abstract base type of the protocol messages send via NFC to
 * accomplish a transaction. The ProtocolMessage consists of a header and an
 * arbitrary long payload.
 * 
 * @author Jeton Memeti
 * 
 */
public abstract class ProtocolMessage {
	
	/*
	 * The data consists of the header plus payload
	 */
	private byte[] data;
	private int headerLength;
	
	/**
	 * Returns the status code contained in the first byte of the header. Each
	 * {@link ProtocolMessage} has in its first byte of the header some kind of
	 * status information of that message. If the data is null, then 0x00 is
	 * returned.
	 */
	public byte getStatus() {
		if (data == null || data.length == 0)
			return 0x00;
		else
			return data[0];
	}
	
	/**
	 * Sets the status of this ProtocolMessage.
	 */
	public void setStatus(byte status) {
		if (data != null && data.length > 0)
			data[0] = status;
	}
	
	/**
	 * Sets the header length, i.e., how many bytes in the data belong to the
	 * header.
	 * 
	 * @param length
	 *            the length of the header
	 */
	public void setHeaderLength(int length) {
		headerLength = length;
	}
	
	/**
	 * Returns the complete data (header plus payload).
	 */
	public byte[] getData() {
		return data;
	}
	
	/**
	 * Sets the data, consisting of the header plus payload.
	 */
	public void setData(byte[] data) {
		this.data = data;
	}
	
	/**
	 * Returns the payload only (excluding the header) or null.
	 */
	public byte[] getPayload() {
		if (data == null || data.length <= headerLength)
			return null;
		else
			return Arrays.copyOfRange(data, headerLength, data.length);
	}
	
	/**
	 * Returns the length of the payload.
	 */
	public int getPayloadLength() {
		if (data == null || data.length <= headerLength)
			return 0;
		else
			return data.length-headerLength; 
	}
	
}
