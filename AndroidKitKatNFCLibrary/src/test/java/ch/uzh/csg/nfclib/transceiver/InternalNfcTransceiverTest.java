package ch.uzh.csg.nfclib.transceiver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ReturnsElementsOf;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventInterface;
import ch.uzh.csg.nfclib.exceptions.TransceiveException;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.testutil.TestUtils;
import ch.uzh.csg.nfclib.util.Config;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class InternalNfcTransceiverTest {
	
	public static long userId = 1234567891011121314L;
	
	private static int THREAD_SLEEP_TIME = 1000;

	private boolean nfcInitialized = false;
	private boolean nfcInitFailed = false;
	private boolean nfcError = false;
	private boolean nfcMessageSend = false;
	private boolean nfcMessageReceived = false;
	private boolean nfcConnectionLost = false;
	private byte[] response = null;
	private String nfcErrorMessage = null;
	
	private NfcEventInterface eventHandler = new NfcEventInterface() {
		
		@Override
		public synchronized void handleMessage(NfcEvent event, Object object) {
			switch (event) {
			case CONNECTION_LOST:
				nfcConnectionLost = true;
				break;
			case FATAL_ERROR:
				nfcError = true;
				if (object != null) {
					nfcErrorMessage = (String) object;
				}
				break;
			case INITIALIZED:
				nfcInitialized = true;
				break;
			case INIT_FAILED:
				nfcInitFailed = true;
				break;
			case MESSAGE_RECEIVED:
				nfcMessageReceived = true;
				if (object != null && object instanceof byte[]) {
					response = (byte[]) object;
				}
				break;
			case MESSAGE_RETURNED:
				break;
			case MESSAGE_SENT:
				nfcMessageSend = true;
				break;
			}
		}
	};
	
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
	
	@Test
	public void testInitNfc() throws IOException, InterruptedException {
		reset();
		
		byte status = NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL;
		byte[] mockResponse = new NfcMessage(status, (byte) 0x00, null).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		long userId = System.currentTimeMillis();
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(isoDep).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitialized);
		assertFalse(nfcInitFailed);
		assertFalse(nfcError);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageSend);
		assertNull(response);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testInitNfc_WrongAid() throws IOException, InterruptedException {
		reset();
		
		byte[] mockResponse = new NfcMessage(NfcMessage.ERROR, (byte) (0x00), null).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(isoDep).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageSend);
		assertNull(response);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testInitNfc_Fail() throws IOException, InterruptedException {
		reset();
		
		byte[] mockResponse = new NfcMessage(new byte[] { 0x00 }).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(isoDep).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageSend);
		assertNull(response);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_Reassembly() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep writes 3 messages (3 fragments) and gets 3 fragments
		 * back which need to be reassembled.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(200));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(200));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(isoDep, times(5)).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(600, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceiveConsecutive() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 2 messages. First message needs to be
		 * fragmented into 2 parts and gets two fragments back. Second message
		 * fits into 1 fragment and gets 2 fragments back.
		 */
		
		reset();

		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage response3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(200));
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(150));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x02, TestUtils.getRandomBytes(150));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(1*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(isoDep, times(3)).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(400, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		
		reset();
		
		transceiver.transceive(TestUtils.getRandomBytes(InternalNfcTransceiver.MAX_WRITE_LENGTH-50));
		
		verify(isoDep, times(5)).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(response.length, 300);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_IllegalArgument() throws IOException, InterruptedException {
		reset();
		
		byte[] mockResponse = new NfcMessage(NfcMessage.DEFAULT, (byte) (0x01), TestUtils.getRandomBytes(100)).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		boolean exceptionThrown = false;
		String msg = null;
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		try {
			transceiver.transceive(new byte[0]);
		} catch (IllegalArgumentException e) {
			exceptionThrown = true;
			msg = e.getMessage();
		}
		
		assertTrue(exceptionThrown);
		assertEquals(InternalNfcTransceiver.NULL_ARGUMENT, msg);
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_TransceiveException_NotEnabled() throws IOException, InterruptedException {
		reset();
		
		byte[] mockResponse = new NfcMessage(NfcMessage.DEFAULT, (byte) (0x01), TestUtils.getRandomBytes(100)).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(InternalNfcTransceiver.NFCTRANSCEIVER_NOT_CONNECTED, nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_TransceiveException_IOException() throws IOException, InterruptedException {
		reset();
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.transceive(any(byte[].class))).thenThrow(new IOException());
		when(isoDep.getMaxTransceiveLength()).thenReturn(1000);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertTrue(nfcConnectionLost);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_TransceiveException_NoValidResponse() throws IOException, InterruptedException {
		reset();
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.transceive(any(byte[].class))).thenReturn(new byte[] {0x00});
		when(isoDep.getMaxTransceiveLength()).thenReturn(1000);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(InternalNfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_NfcErrorMessage() throws InterruptedException, IOException {
		reset();
		
		//returning NfcMessage.ERROR results in TransceiveException
		byte[] mockResponse = new NfcMessage(NfcMessage.ERROR, (byte) (0x01), TestUtils.getRandomBytes(100)).getData();
		CustomIsoDep isoDep = createIsoDepMock(mockResponse);
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		verify(isoDep).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(InternalNfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_retransmissionRequested() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 3 parts and gets 2 fragments back. After transmitting the second
		 * fragment, the HCE requests a retransmission for any reason.
		 */
		
		reset();

		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(200));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(190));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		byte[] randomBytes = TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(isoDep).transceive(fragments.get(0).getData());
		verify(isoDep, times(2)).transceive(fragments.get(1).getData());
		verify(isoDep).transceive(fragments.get(2).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null).getData());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(390, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_invalidSqNr() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, IsoDep recognizes an invalid sequence number and requests
		 * the HCE to retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		byte[] randomBytes = TestUtils.getRandomBytes(180);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, randomBytes);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, randomBytes);
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(100));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*InternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(isoDep).transceive(fragments.get(0).getData());
		verify(isoDep).transceive(fragments.get(1).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(480, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, IsoDep recognizes a corrupt message and requests the HCE to
		 * retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage response3 = new NfcMessage(null);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(180));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(100));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*InternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(isoDep).transceive(fragments.get(0).getData());
		verify(isoDep).transceive(fragments.get(1).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(480, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse2() throws IOException, IllegalArgumentException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, IsoDep recognizes a corrupt message and requests the HCE to
		 * retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		byte[] randomBytes = TestUtils.getRandomBytes(180);
		NfcMessage response3 = new NfcMessage(new byte[] { 0x00 });
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, randomBytes);
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(100));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*InternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(isoDep).transceive(fragments.get(0).getData());
		verify(isoDep).transceive(fragments.get(1).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData());
		verify(isoDep).transceive(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(480, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_exceedMaxRetransmits() throws IOException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, IsoDep recognizes a corrupt message and requests the HCE to
		 * retransmit the last message. The IsoDep requests a retransmission,
		 * but the response is still corrupt. Since MAX_RETRANSMITS is 1, this
		 * results in a TransceiveExeption.
		 */
		
		reset();
		
		assertEquals(1, Config.MAX_RETRANSMITS);
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(200));
		NfcMessage response3 = new NfcMessage(new byte[] { 0x00 });
		NfcMessage response4 = new NfcMessage(new byte[] { 0x00 });
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(1*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_retransmissionRequested_exceedMaxRetransmits() throws IOException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 3 parts and gets 2 fragments back. After transmitting the second
		 * fragment, the HCE requests a retransmission for any reason. The
		 * retransmitted message is still corrupt. Since MAX_RETRANSMITS is 1,
		 * this results in a TransceiveExeption.
		 */
		
		reset();
		
		assertEquals(1, Config.MAX_RETRANSMITS);
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x03, null);
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
	
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_deadlock() throws IOException, InterruptedException {
		/*
		 * Scenario: IsoDep transceives 1 message which needs to be fragmented
		 * into 3 parts and gets 2 fragments back. After transmitting the second
		 * fragment, IsoDep recognizes a corrupt message and requests the HCE to
		 * retransmit the last message. However, HCE has also lost the message
		 * and requests a retransmit. This results in a deadlock, since both
		 * parties requests the counterpart to retransmit the message and
		 * results in a TransceiveExeption.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x01, null);
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageSend);
		assertNull(response);
	}
	
	@Test
	public void testExceed255Messages_Send() throws IOException, InterruptedException, IllegalArgumentException, TransceiveException {
		/*
		 * Scenario: IsoDep writes 1 huge messages (more than 256 fragments).
		 * This tests that the sequence number is reset correctly.
		 */
		
		reset();
		
		int leastNofFragments = 260;
		int nofFragments = leastNofFragments * InternalNfcTransceiver.MAX_WRITE_LENGTH / (InternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (InternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * InternalNfcTransceiver.MAX_WRITE_LENGTH) {
			nofFragments++;
		}
		
		ArrayList<byte[]> responses2 = new ArrayList<byte[]>(nofFragments);
		int sqNr = 0;
		for (int i=0; i<nofFragments; i++) {
			if (i==255) {
				sqNr += 2;
			} else {
				sqNr++;
			}
			
			if (i == nofFragments-1) {
				responses2.add(new NfcMessage(NfcMessage.DEFAULT, (byte) sqNr, new byte[] { 0x01, 0x02 }).getData());
			} else {
				responses2.add(new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) sqNr, null).getData());
			}
		}
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.then(new ReturnsElementsOf(responses2));
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		transceiver.transceive(TestUtils.getRandomBytes(leastNofFragments*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(isoDep, times(nofFragments)).transceive(any(byte[].class));

		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(2, response.length);
	}
	
	@Test
	public void testExceed255Messages_Return() throws IOException, IllegalArgumentException, TransceiveException, InterruptedException {
		/*
		 * Scenario: IsoDep writes 1 messages and gets one huge message back
		 * (more than 256 fragments). This tests that the sequence number is
		 * reset correctly.
		 */
		
		reset();
		
		int leastNofFragments = 260;
		int nofFragments = leastNofFragments * InternalNfcTransceiver.MAX_WRITE_LENGTH / (InternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (InternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * InternalNfcTransceiver.MAX_WRITE_LENGTH) {
			nofFragments++;
		}
		
		ArrayList<byte[]> responses2 = new ArrayList<byte[]>(nofFragments);
		int sqNr = 0;
		for (int i=0; i<nofFragments; i++) {
			if (i==255) {
				sqNr += 2;
			} else {
				sqNr++;
			}
			
			if (i == nofFragments-1) {
				responses2.add(new NfcMessage(NfcMessage.DEFAULT, (byte) sqNr, TestUtils.getRandomBytes(100)).getData());
			} else {
				responses2.add(new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) sqNr, TestUtils.getRandomBytes(100)).getData());
			}
		}
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class))).then(new ReturnsElementsOf(responses2));
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		NfcMessage toSend = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x01, new byte[] { 0x00, 0x01 });
		transceiver.transceive(toSend.getData());
		
		verify(isoDep, times(nofFragments)).transceive(any(byte[].class));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(nofFragments*100, response.length);
	}
	
	@Test
	public void testResumeSession_IOException_whileSending() throws InterruptedException, IOException {
		/*
		 * Scenario: IsoDep writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After sending the first fragment (and before sending
		 * the second), CustomIsoDep throws an IOException, which simulates a
		 * connection lost. IsoDep then calls initNfc() again (which would be
		 * called by the onTagDiscovered()). This tests that the session is
		 * resumed correctly.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x00, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(150));
		NfcMessage response5 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(150));
		NfcMessage response6 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(100));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenThrow(new IOException())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData())
			.thenReturn(response6.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		transceiver.initNfc();
		
		verify(isoDep, times(7)).transceive(any(byte[].class));
	
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(400, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testResumeSession_IOException_whileReceiving() throws InterruptedException, IOException {
		/*
		 * Scenario: IsoDep writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before requesting
		 * the second one), CustomIsoDep throws an IOException, which simulates a
		 * connection lost. IsoDep then calls initNfc() again (which would be
		 * called by the onTagDiscovered()). This tests that the session is
		 * resumed correctly.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(175));
		NfcMessage response4 = new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x00, null);
		NfcMessage response5 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(175));
		NfcMessage response6 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(100));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenThrow(new IOException())
			.thenReturn(response4.getData())
			.thenReturn(response5.getData())
			.thenReturn(response6.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		transceiver.initNfc();
		
		verify(isoDep, times(7)).transceive(any(byte[].class));
	
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(450, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testResumeSession_exceedThreshold() throws InterruptedException, IOException {
		/*
		 * Scenario: IsoDep writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before
		 * requesting the second one), CustomIsoDep throws an IOException, which
		 * simulates a connection lost. IsoDep then calls initNfc() again (which
		 * would be called by the onTagDiscovered()), but waits too long to do
		 * so. HCE interprets this as a new session. This tests that the session
		 * is resumed correctly.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(175));
		NfcMessage response4 = new NfcMessage((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), (byte) 0x00, null);
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenThrow(new IOException())
			.thenReturn(response4.getData());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		/*
		 * This has no effect on the test case, because the START_PROTOCOL flag
		 * has been returned from the HCE.
		 */
		Thread.sleep(Config.SESSION_RESUME_THRESHOLD);
		
		transceiver.initNfc();
		
		verify(isoDep, times(5)).transceive(any(byte[].class));
	
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertTrue(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testResumeSession_exceedThreshold_errorMsg() throws InterruptedException, IOException {
		/*
		 * Scenario: IsoDep writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before
		 * requesting the second one), CustomIsoDep throws an IOException, which
		 * simulates a connection lost. Since there is no initNfc() called
		 * (which would be called by the onTagDiscovered()), IsoDep propagates
		 * the error message after the threshold time is exceeded. This tests
		 * that the session resume thread waits the correct amount of time
		 * before shutting down.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(175));
		
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.getMaxTransceiveLength()).thenReturn(InternalNfcTransceiver.MAX_WRITE_LENGTH);
		when(isoDep.transceive(any(byte[].class)))
			.thenReturn(response1.getData())
			.thenReturn(response2.getData())
			.thenReturn(response3.getData())
			.thenThrow(new IOException());
		
		InternalNfcTransceiver transceiver = new InternalNfcTransceiver(eventHandler, userId, isoDep);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*InternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(isoDep, times(4)).transceive(any(byte[].class));
	
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertTrue(nfcConnectionLost);
		assertNull(nfcErrorMessage);
	}
	
	private CustomIsoDep createIsoDepMock(byte[] mockAnswer) throws IOException {
		CustomIsoDep isoDep = mock(CustomIsoDep.class);
		when(isoDep.isConnected()).thenReturn(true);
		when(isoDep.transceive(any(byte[].class))).thenReturn(mockAnswer);
		when(isoDep.getMaxTransceiveLength()).thenReturn(1000);
		return isoDep;
	}
	
	private void reset() {
		nfcInitialized = false;
		nfcInitFailed = false;
		nfcError = false;
		nfcMessageSend = false;
		nfcMessageReceived = false;
		nfcConnectionLost = false;
		response = null;
		nfcErrorMessage = null;
	}
	
}
