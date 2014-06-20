package ch.uzh.csg.nfclib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.transceiver.NfcTransceiver;
import ch.uzh.csg.nfclib.util.Config;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class CustomHostApduServiceTest {
	
	private static int THREAD_SLEEP_TIME = 1000;
	
	private boolean nfcInitialized = false;
	private long userIdReceived = 0;
	private boolean nfcCommunicationError = false;
	private String nfcCommunicationErrorMessage = null;
	private boolean nfcMessageReceived = false;
	private boolean nfcMessageReturned = false;
	
	private byte[] received1;
	private byte[] received2;
	private byte[] response1;
	private byte[] response2;
	private int count = 0;
	
	private Activity hostActivity = mock(Activity.class);
	
	@Before
	public void before() {
		PowerMockito.mockStatic(Log.class);
		PowerMockito.when(Log.d(Mockito.anyString(), Mockito.anyString())).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println(Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
	}
	
	private NfcEventInterface eventHandler = new NfcEventInterface() {
		@Override
		public void handleMessage(NfcEvent event, Object object) {
			
			switch (event) {
			case CONNECTION_LOST:
				break;
			case FATAL_ERROR:
				nfcCommunicationError = true;
				if (object != null) {
					nfcCommunicationErrorMessage = (String) object;
				}
				break;
			case INITIALIZED:
				nfcInitialized = true;
				userIdReceived = ((Long) object).longValue();
				break;
			case INIT_FAILED:
				break;
			case MESSAGE_RECEIVED:
				nfcMessageReceived = true;
				break;
			case MESSAGE_RETURNED:
				nfcMessageReturned = true;
				break;
			case MESSAGE_SENT:
				break;
			}
		}
	};
	
	
	
	@Test
	public void testProcessCommandApdu_NullActivity() throws Exception {	
		/*
		 * This will fail because the init method is not called (there is no host activity).
		 */
		reset();
		CustomHostApduService hceService = new CustomHostApduService(null,null,null);
		byte[] processCommandApdu = hceService.processCommandApdu(null, null);
		NfcMessage response = new NfcMessage().bytes(processCommandApdu);
		NfcMessage expectedResponse = new NfcMessage().type(NfcMessage.ERROR);
		compare(response, expectedResponse);
		
	}
	
	private static void compare(NfcMessage msg1, NfcMessage msg2) {
		assertEquals(msg1.type(), msg2.type());
		assertEquals(msg1.payload(), msg2.payload());
		assertEquals(msg1.bytes(), msg2.bytes());
		assertEquals(msg1.sequenceNumber(), msg2.sequenceNumber());
	}
	
	@Test
	public void testProcessCommandApdu_SelectAid() throws InterruptedException {
		reset();
		CustomHostApduService hceService = getHCEService(null);
		byte[] processCommandApdu = hceService.processCommandApdu(NfcMessage.CLA_INS_P1_P2_AID_MBPS, null);
		NfcMessage response = new NfcMessage().bytes(processCommandApdu);
		assertTrue(response.isSelectAidApdu());
	}
	
	@Test
	public void testProcessCommandApdu_Reassembly() throws InterruptedException {
		/*
		 * Scenario: HCE receives 3 fragments which need to be reassembled and
		 * returns a message which needs to be split into 3 fragments (see the
		 * IMessageHandler implementation above).
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(200));
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null);
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(1, response.getSequenceNumber());
		assertEquals(0, response.getPayloadLength());
		
		//HCE receives 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		assertEquals(0, response.getPayloadLength());
		
		//HCE receives 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testProcessCommandApdu_ReassemblyConsecutive() throws InterruptedException {
		/*
		 * Scenario: HCE receives first 3 fragments which need to be reassembled
		 * and returns a message which needs to be split into 3 fragments (see
		 * the IMessageHandler implementation above). HCE then receives a second
		 * message consisting of 2 fragments and returns 2 fragments.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				if (count == 0) {
					received1 = message;
					//This will produce 3 NfcMessages (3 fragments)
					response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
					count++;
					return response1;
				} else {
					received2 = message;
					//This will produce 2 NfcMessages (2 fragments)
					response2 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
					return response2;
				}
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		/*
		 * Simulates sending one big message and receiving one big response.
		 */
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(200));
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null);
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		
		nfcMessageReceived = nfcMessageReturned = false;
		
		
		/*
		 * Simulates sending the second big message and receiving again a big response.
		 */
		NfcMessage received1_2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(200));
		NfcMessage received2_2 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage received3_2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null);
		
		NfcMessage response_2;
		byte[] processCommandApdu_2;
		
		//HCE receives 1st fragment
		processCommandApdu_2 = hceService.processCommandApdu(received1_2.getData(), null);
		response_2 = new NfcMessage(processCommandApdu_2);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response_2.getStatus());
		assertEquals(0, response_2.getPayloadLength());
		assertEquals(1, response_2.getSequenceNumber());
		
		//HCE receives 2nd fragment
		processCommandApdu_2 = hceService.processCommandApdu(received2_2.getData(), null);
		response_2 = new NfcMessage(processCommandApdu_2);
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response_2.getStatus());
		int payload1_2 = response_2.getPayloadLength();
		assertEquals(2, response_2.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		//HCE receives 3rd fragment
		processCommandApdu_2 = hceService.processCommandApdu(received3_2.getData(), null);
		response_2 = new NfcMessage(processCommandApdu_2);
		assertEquals(NfcMessage.DEFAULT, response_2.getStatus());
		int payload2_2 = response_2.getPayloadLength();
		assertEquals(3, response_2.getSequenceNumber());
		
		assertNotNull(this.received2);
		assertEquals(received1_2.getPayloadLength()+received2_2.getPayloadLength(), this.received2.length);
		assertNotNull(this.response2);
		assertEquals(payload1_2+payload2_2, this.response2.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testProcessCommandApdu_NfcError() throws InterruptedException {
		reset();
		CustomHostApduService hceService = getHCEService(null);
		
		/*
		 * Simulate sending a corrupted message (only 1 byte)
		 */
		byte[] bytes = new byte[] { 0x00 };
		byte[] response = hceService.processCommandApdu(bytes, null);
		NfcMessage nfcResponse = new NfcMessage(response);
		
		assertEquals(NfcMessage.RETRANSMIT, nfcResponse.getStatus());
		assertEquals(1, nfcResponse.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
	}
	
	@Test
	public void testProcessCommandApdu_NfcErrorNull() throws InterruptedException {
		reset();
		CustomHostApduService hceService = getHCEService(null);
		
		/*
		 * Simulate sending a corrupted message (null)
		 */
		byte[] response = hceService.processCommandApdu(null, null);
		NfcMessage nfcResponse = new NfcMessage(response);
		
		assertEquals(NfcMessage.RETRANSMIT, nfcResponse.getStatus());
		assertEquals(1, nfcResponse.getSequenceNumber());

		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
	}
	
	@Test
	public void testRetransmit_retransmissionRequested() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 2 fragments (1 message) and gets 3
		 * fragments back. After receiving the second fragment, IsoDep
		 * requests a retransmission for any reason.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		/*
		 * Simulates sending one big message and receiving one big response.
		 */
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(200));
		NfcMessage received2 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage received3 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage received4 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null);
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - returns 1st fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		//HCE retransmits 2nd fragment 
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2_2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		assertEquals(payload2, payload2_2);
		
		//HCE returns 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_invalidSqNr() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 3
		 * fragments back. After receiving the second fragment, HCE recognizes
		 * an invalid sequence number and requests IsoDep to retransmit the last
		 * fragment.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(200));
		//this message will fail because of the invalid sq nr
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage received2_2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(200));
		
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null);
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - invalid sq nr
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.RETRANSMIT, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - correct one
		processCommandApdu = hceService.processCommandApdu(received2_2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		//HCE returns 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(6, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received2.getPayloadLength(), received2_2.getPayloadLength());
		assertEquals(received1.getPayloadLength()+received2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 2
		 * fragments back. After receiving the second fragment, HCE
		 * recognizes a corrupt message and requests the HCE to retransmit the
		 * last message.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 2 NfcMessages (2 fragments)
				response1 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		//this message will fail because of the invalid message 
		NfcMessage received2 = new NfcMessage(null);
		NfcMessage received2_2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(150));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(150));
		
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - invalid message
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.RETRANSMIT, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - correct one
		processCommandApdu = hceService.processCommandApdu(received2_2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2_2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse2() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 2
		 * fragments back. After receiving the second fragment, HCE
		 * recognizes a corrupt message and requests the HCE to retransmit the
		 * last message.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 2 NfcMessages (2 fragments)
				response1 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		//this message will fail because of the invalid message 
		NfcMessage received2 = new NfcMessage(new byte[] { 0x00 });
		NfcMessage received2_2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(150));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(150));
		
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - invalid message
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.RETRANSMIT, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - correct one
		processCommandApdu = hceService.processCommandApdu(received2_2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		Thread.sleep(200);
		assertTrue(nfcMessageReceived);
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2_2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2, this.response1.length);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_exceedMaxRetransmits() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (2 fragments) and gets 2
		 * fragments back. After receiving the second fragment, HCE recognizes a
		 * corrupt message and requests IsoDep to retransmit the last message.
		 * HCE requests a retransmission, but the response is still corrupt.
		 * Since MAX_RETRANSMITS is 1, HCE returns NfcMessage.ERROR.
		 */
		
		assertEquals(1, Config.MAX_RETRANSMITS);
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 2 NfcMessages (2 fragments)
				response1 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		//this message will fail because of the invalid message 
		NfcMessage received2 = new NfcMessage(new byte[] { 0x00 });
		NfcMessage received3 = new NfcMessage(new byte[] { 0x00 });
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - invalid message
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.RETRANSMIT, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - again invalid message
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.ERROR, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertTrue(nfcCommunicationError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcCommunicationErrorMessage);
		assertNull(this.received1);
		assertNull(this.response1);
	}
	
	@Test
	public void testRetransmit_retransmissionRequested_exceedMaxRetransmits() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) gets 2 fragments
		 * back. After transmitting the second fragment, IsoDep requests a
		 * retransmission for any reason. The retransmitted message is still
		 * corrupt. Since MAX_RETRANSMITS is 1, HCE returns NfcMessage.ERROR.
		 */
		
		assertEquals(1, Config.MAX_RETRANSMITS);
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 2 NfcMessages (2 fragments)
				response1 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		//this message will fail because of the invalid message 
		NfcMessage received2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x02, null);
		NfcMessage received3 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x03, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - invalid message
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment - again invalid message
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.ERROR, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertTrue(nfcCommunicationError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcCommunicationErrorMessage);
		assertNull(this.received1);
		assertNull(this.response1);
	}
	
	@Test
	public void testRetransmit_deadlock() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 2
		 * fragments back. After transmitting the second fragment, HCE
		 * recognizes a corrupt message and requests IsoDep to retransmit the
		 * last message. However, IsoDep has also lost the message and requests
		 * a retransmit. This results in a deadlock, since both parties requests
		 * the counterpart to retransmit the message. HCE returns
		 * NfcMessage.ERROR.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 2 NfcMessages (2 fragments)
				response1 = TestUtils.getRandomBytes(1*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(100));
		NfcMessage received2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x03, null);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives a retransmission request with an incorrect sq nr
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.ERROR, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertTrue(nfcCommunicationError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcCommunicationErrorMessage);
		assertNull(this.received1);
		assertNull(this.response1);
	}
	
	@Test
	public void testExceed255Messages_Return() throws InterruptedException {
		/*
		 * Scenario: IsoDep writes 1 messages (1 fragments). HCE then returns 1
		 * huge message (more than 256 messages). This tests that the sequence
		 * number is reset correctly.
		 */
		
		final int leastNofFragments = 260;
		int nofFragments = leastNofFragments * CustomHostApduService.MAX_WRITE_LENGTH / (CustomHostApduService.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (CustomHostApduService.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * CustomHostApduService.MAX_WRITE_LENGTH) {
			nofFragments++;
		}
		
		ArrayList<byte[]> receivedList = new ArrayList<byte[]>(nofFragments);
		int sqNr = 1;
		receivedList.add(new NfcMessage(NfcMessage.DEFAULT, (byte) sqNr, TestUtils.getRandomBytes(100)).getData());
		for (int i=0; i<nofFragments-1; i++) {
			if (i==254) {
				sqNr += 2;
			} else {
				sqNr++;
			}
			
			receivedList.add(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) sqNr, null).getData());
		}
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				response1 = TestUtils.getRandomBytes(leastNofFragments*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		int expectedSqNr = 0;
		for (int i=0; i<receivedList.size(); i++) {
			//HCE receives 1st fragment
			processCommandApdu = hceService.processCommandApdu(receivedList.get(i), null);
			response = new NfcMessage(processCommandApdu);
			
			if (i > 0 && i % 255 == 0) {
				expectedSqNr += 2;
			} else {
				expectedSqNr++;
			}
			
			if (i < receivedList.size()-1) {
				assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
				assertEquals((byte) (expectedSqNr), response.getSequenceNumber());
				assertEquals(CustomHostApduService.MAX_WRITE_LENGTH-NfcMessage.HEADER_LENGTH, response.getPayloadLength());
			} else {
				assertEquals(NfcMessage.DEFAULT, response.getStatus());
				assertEquals((byte) (expectedSqNr), response.getSequenceNumber());
				
				int x = leastNofFragments*CustomHostApduService.MAX_WRITE_LENGTH - (nofFragments-1)*(CustomHostApduService.MAX_WRITE_LENGTH-NfcMessage.HEADER_LENGTH);
				assertEquals(x, response.getPayloadLength());
			}
		}
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReceived);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertNotNull(this.received1);
		assertEquals(100, this.received1.length);
		assertNotNull(this.response1);
		assertEquals(leastNofFragments*CustomHostApduService.MAX_WRITE_LENGTH, this.response1.length);
	}

	@Test
	public void testExceed255Messages_Receive() throws InterruptedException {
		/*
		 * Scenario: IsoDep writes 1 huge messages (more than 256 messages). HCE
		 * then returns 1 message (1 fragment). This tests that the sequence
		 * number is reset correctly.
		 */
		
		final int leastNofFragments = 260;
		int nofFragments = leastNofFragments * CustomHostApduService.MAX_WRITE_LENGTH / (CustomHostApduService.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (CustomHostApduService.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * CustomHostApduService.MAX_WRITE_LENGTH) {
			nofFragments++;
		}
		
		ArrayList<byte[]> receivedList = new ArrayList<byte[]>(nofFragments);
		int sqNr = 0;
		for (int i=0; i<nofFragments; i++) {
			if (i==255) {
				sqNr += 2;
			} else {
				sqNr++;
			}
			
			if (i == nofFragments-1) {
				receivedList.add(new NfcMessage(NfcMessage.DEFAULT, (byte) sqNr, TestUtils.getRandomBytes(100)).getData());
			} else {
				receivedList.add(new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) sqNr, TestUtils.getRandomBytes(100)).getData());
			}
		}
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				response1 = TestUtils.getRandomBytes(100);
				return response1;
			}
		};
		
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		NfcMessage response;
		byte[] processCommandApdu;
		
		int expectedSqNr = 0;
		for (int i=0; i<receivedList.size(); i++) {
			//HCE receives 1st fragment
			processCommandApdu = hceService.processCommandApdu(receivedList.get(i), null);
			response = new NfcMessage(processCommandApdu);
			if (i < receivedList.size()-1) {
				assertEquals(0, response.getPayloadLength());
				assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
			} else {
				assertEquals(100, response.getPayloadLength());
				assertEquals(NfcMessage.DEFAULT, response.getStatus());
			}
			
			if (i > 0 && i % 255 == 0) {
				expectedSqNr += 2;
			} else {
				expectedSqNr++;
			}
			assertEquals((byte) (expectedSqNr), response.getSequenceNumber());
		}
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageReceived);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertNotNull(this.received1);
		assertEquals(nofFragments*100, this.received1.length);
		assertNotNull(this.response1);
		assertEquals(100, this.response1.length);
	}
	
	@Test
	public void testResumeSession_sameId_thresholdOk() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 1
		 * message (3 fragments) back. After HCE receives the second fragment, the
		 * nfc connection is lost. But before the resume threshold exceeds,
		 * there is a new connection with the same user/userid.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		

		/*
		 * Send the command apdu first, so that HCE can decide on the user id if
		 * a new session has to be started or the previous session to be
		 * resumed.
		 */
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		byte[] processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		NfcMessage response = new NfcMessage(processCommandApdu);
		
		assertEquals((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), response.getStatus());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitialized);
		assertEquals(InternalNfcTransceiverTest.userId, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		
		nfcInitialized = false;
		userIdReceived = 0;
		
		/*
		 * Start sending the messages.
		 */
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(150));
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(100));
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, TestUtils.getRandomBytes(150));
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null);
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		//call onDeactivated to simulate a link loss
		hceService.onDeactivated(0);
		
		//instantiate new HCE Service, this is what Android does on a new nfc connection
		hceService = new CustomHostApduService(null,null,null);
		
		//send command apdu
		processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.AID_SELECTED, response.getStatus());
		
		//HCE receives 2nd fragment again, simulating that the NfcTransceiver did not get the response and retransmits
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(4, response.getSequenceNumber());
		
		//HCE returns 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(5, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		
		assertNotNull(this.received1);
		assertEquals(received1.getPayloadLength()+received2.getPayloadLength()+received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		assertTrue(nfcMessageReceived);
		assertTrue(nfcMessageReturned);
		assertFalse(nfcInitialized);
		assertEquals(0, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testResumeSession_sameId_thresholdNotOk() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 1
		 * message (3 fragments) back. After HCE receives the second fragment,
		 * the nfc connection is lost. Before a new connection is established
		 * (with the same user/userid), the resume threshold exceeds.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		

		/*
		 * Send the command apdu first, so that HCE can decide on the user id if
		 * a new session has to be started or the previous session to be
		 * resumed.
		 */
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		byte[] processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		NfcMessage response = new NfcMessage(processCommandApdu);
		
		assertEquals((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), response.getStatus());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitialized);
		assertEquals(InternalNfcTransceiverTest.userId, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		
		nfcInitialized = false;
		userIdReceived = 0;
		
		/*
		 * Start sending the messages.
		 */
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(150));
		
		//reset the sequence numbers, because HCE expects new session
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x01, TestUtils.getRandomBytes(100));
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, TestUtils.getRandomBytes(150));
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null);
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		//call onDeactivated to simulate a link loss
		hceService.onDeactivated(0);
		
		//instantiate new HCE Service, this is what Android does on a new nfc connection
		hceService = new CustomHostApduService(null,null,null);
		
		Thread.sleep(Config.SESSION_RESUME_THRESHOLD);
		
		//send command apdu
		processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		response = new NfcMessage(processCommandApdu);
		assertEquals((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), response.getStatus());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE returns 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		
		assertNotNull(this.received1);
		assertEquals(received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		assertTrue(nfcMessageReceived);
		assertTrue(nfcMessageReturned);
		assertTrue(nfcInitialized);
		assertEquals(InternalNfcTransceiverTest.userId, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	@Test
	public void testResumeSession_differentId_thresholdOk() throws InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message (3 fragments) and gets 1
		 * message (3 fragments) back. After HCE receives the second fragment, the
		 * nfc connection is lost. But before the resume threshold exceeds,
		 * there is a new connection with the same user/userid.
		 */
		
		reset();
		
		IMessageHandler messageHandler = new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				received1 = message;
				//This will produce 3 NfcMessages (3 fragments)
				response1 = TestUtils.getRandomBytes(2*CustomHostApduService.MAX_WRITE_LENGTH);
				return response1;
			}
		};
		

		/*
		 * Send the command apdu first, so that HCE can decide on the user id if
		 * a new session has to be started or the previous session to be
		 * resumed.
		 */
		CustomHostApduService hceService = getHCEService(messageHandler);
		
		byte[] processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		NfcMessage response = new NfcMessage(processCommandApdu);
		
		assertEquals((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), response.getStatus());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitialized);
		assertEquals(InternalNfcTransceiverTest.userId, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageReturned);
		
		nfcInitialized = false;
		userIdReceived = 0;
		
		/*
		 * Start sending the messages.
		 */
		NfcMessage received1 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		NfcMessage received2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(150));
		
		NfcMessage received3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x01, TestUtils.getRandomBytes(100));
		NfcMessage received4 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, TestUtils.getRandomBytes(150));
		NfcMessage received5 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null);
		
		//HCE receives 1st fragment
		processCommandApdu = hceService.processCommandApdu(received1.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE receives 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received2.getData(), null);
		response = new NfcMessage(processCommandApdu);
		assertEquals(NfcMessage.GET_NEXT_FRAGMENT, response.getStatus());
		assertEquals(0, response.getPayloadLength());
		assertEquals(2, response.getSequenceNumber());
		
		//call onDeactivated to simulate a link loss
		hceService.onDeactivated(0);
		
		//instantiate new HCE Service, this is what Android does on a new nfc connection
		hceService = new CustomHostApduService(null,null,null);
		
		long newUserId = System.currentTimeMillis();
		
		//send command apdu
		processCommandApdu = hceService.processCommandApdu(Constants.CLA_INS_P1_P2_AID_MBPS, null);
		response = new NfcMessage(processCommandApdu);
		assertEquals((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), response.getStatus());
		
		//HCE receives 3rd fragment - returns 1st
		processCommandApdu = hceService.processCommandApdu(received3.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload1 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(1, response.getSequenceNumber());
		
		//HCE returns 2nd fragment
		processCommandApdu = hceService.processCommandApdu(received4.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload2 = response.getPayloadLength();
		assertEquals(NfcMessage.HAS_MORE_FRAGMENTS, response.getStatus());
		assertEquals(2, response.getSequenceNumber());
		
		//HCE returns 3rd fragment
		processCommandApdu = hceService.processCommandApdu(received5.getData(), null);
		response = new NfcMessage(processCommandApdu);
		int payload3 = response.getPayloadLength();
		assertEquals(NfcMessage.DEFAULT, response.getStatus());
		assertEquals(3, response.getSequenceNumber());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		
		assertNotNull(this.received1);
		assertEquals(received3.getPayloadLength(), this.received1.length);
		assertNotNull(this.response1);
		assertEquals(payload1+payload2+payload3, this.response1.length);
		
		assertTrue(nfcMessageReceived);
		assertTrue(nfcMessageReturned);
		assertTrue(nfcInitialized);
		assertEquals(newUserId, userIdReceived);
		assertFalse(nfcCommunicationError);
		assertNull(nfcCommunicationErrorMessage);
	}
	
	private void reset() {
		/*
		 * Since we are dealing with static fields, we need to reset them before
		 * starting each test case.
		 */
		nfcInitialized = false;
		userIdReceived = 0;
		nfcCommunicationError = false;
		nfcCommunicationErrorMessage = null;
		nfcMessageReceived = false;
		nfcMessageReturned = false;
		
		count = 0;
		
		received1 = null;
		received2 = null;
		response1 = null;
		response2 = null;
		
	}
	
	private CustomHostApduService getHCEService(IMessageHandler messageHandler) {
		CustomHostApduService hceService = new CustomHostApduService(hostActivity, eventHandler, messageHandler);
		return hceService;
	}
	
}
