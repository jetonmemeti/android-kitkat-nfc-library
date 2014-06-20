package ch.uzh.csg.nfclib;


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

	/**
	 * Handles an incoming NFC message. If this is not the first NFC message,
	 * the payload is appended to the temporal internal buffer.
	 * 
	 * @param nfcMessage
	 *            the incoming NFC message
	 */
	public void handleReassembly(NfcMessage nfcMessage) {
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
	}

	/**
	 * Returns the buffer, which is the sum of the concatenated NFC messages.
	 */
	public byte[] data() {
		return data;
	}
}
