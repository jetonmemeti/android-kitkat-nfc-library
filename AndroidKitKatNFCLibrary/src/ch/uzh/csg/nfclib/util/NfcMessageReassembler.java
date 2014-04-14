package ch.uzh.csg.nfclib.util;

import ch.uzh.csg.nfclib.messages.NfcMessage;

/**
 * This class is responsible for reassembling NFC messages which have been
 * fragmented in order to be send.
 */
public class NfcMessageReassembler {

private byte[] responseData;
	
	/**
	 * Handles an incoming NFC message. If this is not the first NFC message, the
	 * payload is appended to the temporal internal buffer.
	 * 
	 * @param nfcMessage
	 *            the incoming NFC message
	 */
	public void handleReassembly(NfcMessage nfcMessage) {
		if (responseData == null || responseData.length == 0) {
			responseData = nfcMessage.getPayload();
		} else {
			byte[] temp = new byte[responseData.length+nfcMessage.getPayloadLength()];
			System.arraycopy(responseData, 0, temp, 0, responseData.length);
			System.arraycopy(nfcMessage.getPayload(), 0, temp, responseData.length, nfcMessage.getPayloadLength());
			responseData = temp;
		}
	}
	
	/**
	 * Clears the internal buffer.
	 */
	public void clear() {
		this.responseData = null;
	}
	
	/**
	 * Returns the buffer, which is the sum of the concatenated NFC messages.
	 */
	public byte[] getData() {
		return responseData;
	}
	
}
