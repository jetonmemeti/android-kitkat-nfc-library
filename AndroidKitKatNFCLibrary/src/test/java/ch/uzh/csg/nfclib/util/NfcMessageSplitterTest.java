package ch.uzh.csg.nfclib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import ch.uzh.csg.nfclib.messages.NfcMessage;

public class NfcMessageSplitterTest  {
	
	@Test
	public void testGetFragments() {
		byte[] payload = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
		NfcMessageSplitter splitter = new NfcMessageSplitter(5);
		ArrayList<NfcMessage> fragments = splitter.getFragments(payload);
		
		assertEquals(3, fragments.size());
		
		NfcMessage nfcMessage1 = fragments.get(0);
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, nfcMessage1.getStatus());
		assertEquals(3, nfcMessage1.getPayloadLength());
		assertTrue(Arrays.equals(new byte[] { 0x01, 0x02, 0x03 }, nfcMessage1.getPayload()));
		
		NfcMessage nfcMessage2 = fragments.get(1);
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, nfcMessage2.getStatus());
		assertEquals(3, nfcMessage2.getPayloadLength());
		assertTrue(Arrays.equals(new byte[] { 0x04, 0x05, 0x06 }, nfcMessage2.getPayload()));
		
		NfcMessage nfcMessage3 = fragments.get(2);
		assertEquals(NfcMessage.DEFAULT, nfcMessage3.getStatus());
		assertEquals(2, nfcMessage3.getPayloadLength());
		assertTrue(Arrays.equals(new byte[] { 0x07, 0x08 }, nfcMessage3.getPayload()));
	}

}
