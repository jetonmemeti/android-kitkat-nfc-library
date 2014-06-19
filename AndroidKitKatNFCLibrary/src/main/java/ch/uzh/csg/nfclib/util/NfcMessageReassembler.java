package ch.uzh.csg.nfclib.util;

import ch.uzh.csg.nfclib.NfcMessage;

/**
 * This class is responsible for reassembling NFC messages which have been
 * fragmented in order to be send.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcMessageReassembler {

	private byte[] data = null;
	private int sequenceNumber = -1;

	/**
	 * Handles an incoming NFC message. If this is not the first NFC message,
	 * the payload is appended to the temporal internal buffer.
	 * 
	 * @param nfcMessage
	 *            the incoming NFC message
	 */
	public void handleReassembly(NfcMessage nfcMessage) {
		if (sequenceNumber == -1) {
			sequenceNumber = nfcMessage.sequenceNumber();
		} else {
			if ((sequenceNumber + 1) % 255 != nfcMessage.sequenceNumber()) {
				throw new IllegalStateException("wrong sequence number: " + nfcMessage + ", expected: "
				        + ((sequenceNumber + 1) % 255));
			} else {
				sequenceNumber = nfcMessage.sequenceNumber();
			}
		}

		if (data == null || data.length == 0) {
			data = nfcMessage.payload();
		} else {
			byte[] temp = new byte[data.length + nfcMessage.payload().length];
			System.arraycopy(data, 0, temp, 0, data.length);
			System.arraycopy(nfcMessage.payload(), 0, temp, data.length, nfcMessage.payload().length);
			data = temp;
		}
	}

	/**
	 * Clears the internal buffer.
	 */
	public void clear() {
		this.data = null;
		sequenceNumber = -1;
	}

	/**
	 * Returns the buffer, which is the sum of the concatenated NFC messages.
	 */
	public byte[] data() {
		return data;
	}

}
