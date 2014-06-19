package ch.uzh.csg.nfclib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import ch.uzh.csg.nfclib.util.Constants;

public class CommandApduTest {

	@Test
	public void testGetCommandApdu() {
		long testId = System.currentTimeMillis();
		
		byte[] commandApdu = CommandApdu.getCommandApdu(testId);
		
		byte[] cla = Arrays.copyOfRange(commandApdu, 0, Constants.CLA_INS_P1_P2.length);
		byte[] aid = Arrays.copyOfRange(commandApdu, Constants.CLA_INS_P1_P2.length+1, Constants.CLA_INS_P1_P2.length+1+Constants.AID_MBPS.length);
		
		assertTrue(Arrays.equals(Constants.CLA_INS_P1_P2, cla));
		assertEquals(Constants.AID_MBPS.length, commandApdu[Constants.CLA_INS_P1_P2.length]);
		
		assertTrue(Arrays.equals(Constants.AID_MBPS, aid));
		assertEquals(2, commandApdu[commandApdu.length-1]);
	}

	@Test
	public void testGetUserId() {
		long testId = System.currentTimeMillis();
		
		byte[] commandApdu = CommandApdu.getCommandApdu(testId);
		long userId = CommandApdu.getUserId(commandApdu);
		
		assertEquals(testId, userId);
	}
	
	@Test
	public void testGetUserId_Consecutively() {
		long testId = System.currentTimeMillis();
		
		byte[] commandApdu = CommandApdu.getCommandApdu(testId);
		long userId = CommandApdu.getUserId(commandApdu);
		
		assertEquals(testId, userId);
		
		commandApdu = CommandApdu.getCommandApdu(testId);
		userId = CommandApdu.getUserId(commandApdu);
		
		assertEquals(testId, userId);
	}

}
