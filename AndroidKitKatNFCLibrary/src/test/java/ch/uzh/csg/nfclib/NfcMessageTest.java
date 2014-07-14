package ch.uzh.csg.nfclib;

import static org.junit.Assert.*;

import org.junit.Test;

import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.messages.NfcMessage.Type;

public class NfcMessageTest {
	
	@Test
	public void testHeader() {
		NfcMessage m = new NfcMessage(Type.DEFAULT);
		assertEquals(0, m.version());
		
		byte header = (byte) 0xFD; // 11111101
		byte sqNr = 0x01;
		NfcMessage m2 = new NfcMessage(new byte[] { header, sqNr });
		assertTrue(m2.hasMoreFragments()); // bit 1
		assertTrue(m2.isRequest()); // bit 2
		assertTrue(m2.isResume()); // bit 3
		assertEquals(3, m2.version()); // bit 4+5
		assertTrue(m2.isReadBinary()); // bit 6-8
		
		header = (byte) 0x51; //01010001
		NfcMessage m3 = new NfcMessage(new byte[] { header, sqNr });
		assertFalse(m3.hasMoreFragments()); // bit 1
		assertTrue(m3.isRequest()); // bit 2
		assertFalse(m3.isResume()); // bit 3
		assertEquals(2, m3.version()); // bit 4+5
		assertTrue(m3.isError()); // bit 6-8
	}

}
