package ch.uzh.csg.nfclib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import ch.uzh.csg.nfclib.NfcMessage;
import ch.uzh.csg.nfclib.NfcMessageSplitter;

public class NfcMessageSplitterTest  {
	
	@Test
	public void testGetFragments1() {
		byte[] payload = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
		NfcMessageSplitter splitter = new NfcMessageSplitter();
		splitter.maxTransceiveLength(5);
		ArrayList<NfcMessage> fragments = splitter.getFragments(payload);
		
		assertEquals(3, fragments.size());
		
		NfcMessage nfcMessage1 = fragments.get(0);
		assertTrue(nfcMessage1.hasMoreFragments());
		assertEquals(3, nfcMessage1.payload().length);
		assertTrue(Arrays.equals(new byte[] { 0x01, 0x02, 0x03 }, nfcMessage1.payload()));
		
		NfcMessage nfcMessage2 = fragments.get(1);
		assertTrue(nfcMessage2.hasMoreFragments());
		assertEquals(3, nfcMessage2.payload().length);
		assertTrue(Arrays.equals(new byte[] { 0x04, 0x05, 0x06 }, nfcMessage2.payload()));
		
		NfcMessage nfcMessage3 = fragments.get(2);
		assertFalse(nfcMessage3.hasMoreFragments());
		assertEquals(2, nfcMessage3.payload().length);
		assertTrue(Arrays.equals(new byte[] { 0x07, 0x08 }, nfcMessage3.payload()));
	}
	
	@Test
	public void testGetFragments2() {
		byte[] payload = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
		NfcMessageSplitter splitter = new NfcMessageSplitter();
		splitter.maxTransceiveLength(6);
		ArrayList<NfcMessage> fragments = splitter.getFragments(payload);
		
		assertEquals(2, fragments.size());
		
		NfcMessage nfcMessage1 = fragments.get(0);
		assertTrue(nfcMessage1.hasMoreFragments());
		assertEquals(4, nfcMessage1.payload().length);
		assertTrue(Arrays.equals(new byte[] { 1, 2, 3, 4}, nfcMessage1.payload()));
		
		NfcMessage nfcMessage3 = fragments.get(1);
		assertFalse(nfcMessage3.hasMoreFragments());
		assertEquals(4, nfcMessage3.payload().length);
		assertTrue(Arrays.equals(new byte[] { 5, 6, 7, 8 }, nfcMessage3.payload()));
	}
	
	@Test
	public void testGetFragments3() {
		byte[] payload = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
		NfcMessageSplitter splitter = new NfcMessageSplitter();
		splitter.maxTransceiveLength(10);
		ArrayList<NfcMessage> fragments = splitter.getFragments(payload);
		
		assertEquals(1, fragments.size());
		
		NfcMessage nfcMessage3 = fragments.get(0);
		assertFalse(nfcMessage3.hasMoreFragments());
		assertEquals(8, nfcMessage3.payload().length);
		assertTrue(Arrays.equals(payload, nfcMessage3.payload()));
	}
	
	@Test
	public void testGetFragments4() {
		byte[] payload = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
		NfcMessageSplitter splitter = new NfcMessageSplitter();
		splitter.maxTransceiveLength(30);
		ArrayList<NfcMessage> fragments = splitter.getFragments(payload);
		
		assertEquals(1, fragments.size());
		
		NfcMessage nfcMessage3 = fragments.get(0);
		assertFalse(nfcMessage3.hasMoreFragments());
		assertEquals(8, nfcMessage3.payload().length);
		assertTrue(Arrays.equals(payload, nfcMessage3.payload()));
	}

}
