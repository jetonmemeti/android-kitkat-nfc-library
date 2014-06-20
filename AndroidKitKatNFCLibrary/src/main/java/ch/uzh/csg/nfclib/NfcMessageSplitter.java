package ch.uzh.csg.nfclib;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Is responsible for NfcMessage (or byte buffer) fragmentation in order to not
 * exceed the maximum allowed message length by the underlying NFC technology.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class NfcMessageSplitter {

	private int payloadLength = Integer.MAX_VALUE;

	/**
	 * Returns a new NfcMessageSplitter to handle the fragmentation of
	 * NfcMessages.
	 * 
	 * @param isoDepMaxTransceiveLength
	 *            the maximum number of bytes which can be send at once by the
	 *            underlying NFC technology
	 */
	public NfcMessageSplitter maxTransceiveLength(int maxTransceiveLength) {
		payloadLength = maxTransceiveLength - NfcMessage.HEADER_LENGTH;
		return this;
	}
	
	public boolean hasFragments(byte[] payload) {
		final int len = payload.length;
		final int fragments = (len + payloadLength - 1) / payloadLength;
		return fragments > 1;
	}

	/**
	 * Fragments the payload into a number of NfcMessages so that no NfcMessage
	 * exceeds the isoDepMaxTransceiveLength. If no fragmentation is needed
	 * (because the payload does not reach the threshold), then a list
	 * containing only one NfcMessage is returned.
	 * 
	 * The sequence number of the NfcMessages is not set here (all messages in
	 * the list have the sequence number 0)! It must be set appropriately
	 * elsewhere.
	 * 
	 * @param payload
	 *            the whole message or byte array to be send by NFC
	 * @return an ArrayList of NfcMessages containing the fragmented payload
	 */
	public ArrayList<NfcMessage> getFragments(byte[] payload) {
		final int len = payload.length;
		// Returns the number of fragments the whole message needs to be split
		// into (taking into account protocol headers etc.).
		final int fragments = (len + payloadLength - 1) / payloadLength;
		ArrayList<NfcMessage> list = new ArrayList<NfcMessage>(fragments);

		NfcMessage nfcMessage = null;
		for (int i = 0; i < fragments; i++) {
			int start = i * payloadLength;
			boolean last = start + payloadLength >= payload.length;
			int end = last ? payload.length : (start + payloadLength);

			byte[] temp = Arrays.copyOfRange(payload, start, end);
			nfcMessage = new NfcMessage().payload(temp).type(NfcMessage.DEFAULT);
			if (!last) {
				nfcMessage.setMoreFragments();
			}
			list.add(nfcMessage);
		}

		return list;
	}

}
