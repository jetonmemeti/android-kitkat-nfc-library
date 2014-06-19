package ch.uzh.csg.nfclib.transceiver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcEventInterface;
import ch.uzh.csg.nfclib.messages.NfcMessage;
import ch.uzh.csg.nfclib.testutil.TestUtils;
import ch.uzh.csg.nfclib.util.Config;
import ch.uzh.csg.nfclib.util.NfcMessageSplitter;

import com.acs.smartcard.Reader;
import com.acs.smartcard.ReaderException;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class ExternalNfcTransceiverTest {
	
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
	public void testInitNfc() throws IOException, InterruptedException, ReaderException {
		reset();
		
		byte[] mockResponse = new NfcMessage((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), (byte) 0x00, null).getData();
		
		List<byte[]> mockResponses = Arrays.asList(mockResponse);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(reader).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
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
	public void testInitNfc_WrongAid() throws IOException, InterruptedException, ReaderException {
		reset();
		
		byte[] mockResponse = new NfcMessage(NfcMessage.ERROR, (byte) (0x00), null).getData();
		
		List<byte[]> mockResponses = Arrays.asList(mockResponse);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(reader).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
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
	public void testInitNfc_Fail() throws IOException, InterruptedException, ReaderException {
		reset();
		
		byte[] mockResponse = new NfcMessage(new byte[] { 0x00 }).getData();
		
		List<byte[]> mockResponses = Arrays.asList(mockResponse);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		transceiver.initNfc();
		
		verify(reader).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
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
	public void testTransceive_Reassembly() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader writes 3 messages (3 fragments) and gets 3 fragments
		 * back which need to be reassembled.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(50));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(40));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(reader, times(5)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(140, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceiveConsecutive() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 2 messages. First message needs to be
		 * fragmented into 2 parts and gets two fragments back. Second message
		 * fits into 1 fragment and gets 2 fragments back.
		 */
		
		reset();

		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(50));
		NfcMessage response3 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x03, TestUtils.getRandomBytes(40));
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x01, TestUtils.getRandomBytes(50));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x02, TestUtils.getRandomBytes(30));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(1*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(reader, times(3)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(90, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		
		reset();
		
		transceiver.transceive(TestUtils.getRandomBytes(ExternalNfcTransceiver.MAX_WRITE_LENGTH-10));
		
		verify(reader, times(5)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(80, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_IllegalArgument() throws IOException, InterruptedException, ReaderException {
		reset();
		
		Reader reader = mock(Reader.class);
		when(reader.isOpened()).thenReturn(true);
		when(reader.transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt())).thenThrow(new ReaderException());
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		
		boolean exceptionThrown = false;
		String msg = null;
		
		try {
			transceiver.transceive(new byte[0]);
		} catch (IllegalArgumentException e) {
			exceptionThrown = true;
			msg = e.getMessage();
		}
		
		assertTrue(exceptionThrown);
		assertEquals(ExternalNfcTransceiver.NULL_ARGUMENT, msg);
		
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
	public void testTransceive_TransceiveException_NotEnabled() throws IOException, InterruptedException, ReaderException {
		reset();
		
		Reader reader = mock(Reader.class);
		when(reader.isOpened()).thenReturn(true);
		when(reader.transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt())).thenThrow(new ReaderException());
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(ExternalNfcTransceiver.NFCTRANSCEIVER_NOT_CONNECTED, nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_TransceiveException_IOException() throws IOException, InterruptedException, ReaderException {
		reset();
		
		Reader reader = mock(Reader.class);
		when(reader.isOpened()).thenReturn(true);
		when(reader.transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt())).thenThrow(new ReaderException());
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		transceiver.transceive(TestUtils.getRandomBytes(50));
		
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
	public void testTransceive_TransceiveException_NoValidResponse() throws IOException, InterruptedException, ReaderException {
		reset();
		
		byte[] mockResponse = new NfcMessage(new byte[] { 0x00 }).getData();
		
		List<byte[]> mockResponses = Arrays.asList(mockResponse);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		transceiver.transceive(TestUtils.getRandomBytes(50));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(ExternalNfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testTransceive_NfcErrorMessage() throws InterruptedException, IOException, ReaderException {
		reset();
		
		//returning NfcMessage.ERROR results in TransceiveException
		byte[] mockResponse = new NfcMessage(NfcMessage.ERROR, (byte) (0x01), TestUtils.getRandomBytes(50)).getData();
		
		List<byte[]> mockResponses = Arrays.asList(mockResponse);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		transceiver.transceive(TestUtils.getRandomBytes(100));
		
		verify(reader).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcMessageSend);
		assertFalse(nfcMessageReceived);
		assertNull(response);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertTrue(nfcError);
		assertEquals(ExternalNfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_retransmissionRequested() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 3 parts and gets 2 fragments back. After transmitting the second
		 * fragment, the HCE requests a retransmission for any reason.
		 */
		
		reset();

		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(50));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(30));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		byte[] randomBytes = TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(reader).transmit(eq(0), aryEq(fragments.get(0).getData()), eq(fragments.get(0).getData().length), any(byte[].class), anyInt());
		verify(reader, times(2)).transmit(eq(0), aryEq(fragments.get(1).getData()), eq(fragments.get(1).getData().length), any(byte[].class), anyInt());
		verify(reader).transmit(eq(0), aryEq(fragments.get(2).getData()), eq(fragments.get(2).getData().length), any(byte[].class), anyInt());
		byte[] temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x04, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(80, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_invalidSqNr() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, Reader recognizes an invalid sequence number and requests
		 * the HCE to retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(50));
		byte[] randomBytes = TestUtils.getRandomBytes(50);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, randomBytes);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, randomBytes);
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(40));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(reader).transmit(eq(0), aryEq(fragments.get(0).getData()), eq(fragments.get(0).getData().length), any(byte[].class), anyInt());
		verify(reader).transmit(eq(0), aryEq(fragments.get(1).getData()), eq(fragments.get(1).getData().length), any(byte[].class), anyInt());
		byte[] temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(140, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, Reader recognizes a corrupt message and requests the HCE to
		 * retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(50));
		NfcMessage response3 = new NfcMessage(null);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(30));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		verify(reader).transmit(eq(0), aryEq(fragments.get(0).getData()), eq(fragments.get(0).getData().length), any(byte[].class), anyInt());
		verify(reader).transmit(eq(0), aryEq(fragments.get(1).getData()), eq(fragments.get(1).getData().length), any(byte[].class), anyInt());
		byte[] temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(130, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_corruptResponse2() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, Reader recognizes a corrupt message and requests the HCE to
		 * retransmit the last message.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(50));
		NfcMessage response3 = new NfcMessage(new byte[] { 0x00 });
		byte[] randomBytes = TestUtils.getRandomBytes(40);
		NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, randomBytes);
		NfcMessage response5 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x04, TestUtils.getRandomBytes(20));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		byte[] randomBytes2 = TestUtils.getRandomBytes(1*ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		transceiver.transceive(randomBytes2);
		
		NfcMessageSplitter splitter = new NfcMessageSplitter(ExternalNfcTransceiver.MAX_WRITE_LENGTH);
		ArrayList<NfcMessage> fragments = splitter.getFragments(randomBytes2);
		for (int i=0; i<fragments.size(); i++) {
			NfcMessage nfcMessage = fragments.get(i);
			nfcMessage.setSequenceNumber((byte) (i+1));
		}
		
		
		verify(reader).transmit(eq(0), aryEq(fragments.get(0).getData()), eq(fragments.get(0).getData().length), any(byte[].class), anyInt());
		verify(reader).transmit(eq(0), aryEq(fragments.get(1).getData()), eq(fragments.get(1).getData().length), any(byte[].class), anyInt());
		byte[] temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x03, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x04, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		temp = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x05, null).getData();
		verify(reader).transmit(eq(0), aryEq(temp), eq(temp.length), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(110, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testRetransmit_requestRetransmission_exceedMaxRetransmits() throws IOException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 2 parts and gets 3 fragments back. After receiving the second
		 * fragment, Reader recognizes a corrupt message and requests the HCE to
		 * retransmit the last message. The Reader requests a retransmission,
		 * but the response is still corrupt. Since MAX_RETRANSMITS is 1, this
		 * results in a TransceiveExeption.
		 */
		
		reset();
		
		assertEquals(1, Config.MAX_RETRANSMITS);
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x02, TestUtils.getRandomBytes(40));
		NfcMessage response3 = new NfcMessage(new byte[] { 0x00 });
		NfcMessage response4 = new NfcMessage(new byte[] { 0x00 });
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 1 results in 2 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(1*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
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
	public void testRetransmit_retransmissionRequested_exceedMaxRetransmits() throws IOException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
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
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
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
	public void testRetransmit_deadlock() throws IOException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader transceives 1 message which needs to be fragmented
		 * into 3 parts and gets 2 fragments back. After transmitting the second
		 * fragment, Reader recognizes a corrupt message and requests the HCE to
		 * retransmit the last message. However, HCE has also lost the message
		 * and requests a retransmit. This results in a deadlock, since both
		 * parties requests the counterpart to retransmit the message and
		 * results in a TransceiveExeption.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.RETRANSMIT, (byte) 0x01, null);
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData());
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcError);
		assertEquals(NfcTransceiver.UNEXPECTED_ERROR, nfcErrorMessage);
		assertFalse(nfcMessageReceived);
		assertFalse(nfcMessageSend);
		assertNull(response);
	}
	
	@Test
	public void testExceed255Messages_Send() throws IOException, InterruptedException, IllegalArgumentException, ReaderException {
		/*
		 * Scenario: Reader writes 1 huge messages (more than 256 fragments).
		 * This tests that the sequence number is reset correctly.
		 */
		
		reset();
		
		int leastNofFragments = 260;
		int nofFragments = leastNofFragments * ExternalNfcTransceiver.MAX_WRITE_LENGTH / (ExternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (ExternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * ExternalNfcTransceiver.MAX_WRITE_LENGTH) {
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
		
		List<byte[]> mockResponses = new ArrayList<byte[]>(responses2);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		transceiver.transceive(TestUtils.getRandomBytes(leastNofFragments*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(reader, times(nofFragments)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());

		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(2, response.length);
	}
	
	@Test
	public void testExceed255Messages_Return() throws IOException, IllegalArgumentException, InterruptedException, ReaderException {
		/*
		 * Scenario: Reader writes 1 messages and gets one huge message back
		 * (more than 256 fragments). This tests that the sequence number is
		 * reset correctly.
		 */
		
		reset();
		
		int leastNofFragments = 260;
		int nofFragments = leastNofFragments * ExternalNfcTransceiver.MAX_WRITE_LENGTH / (ExternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH);
		if (nofFragments * (ExternalNfcTransceiver.MAX_WRITE_LENGTH - NfcMessage.HEADER_LENGTH) < leastNofFragments * ExternalNfcTransceiver.MAX_WRITE_LENGTH) {
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
				responses2.add(new NfcMessage(NfcMessage.DEFAULT, (byte) sqNr, TestUtils.getRandomBytes(50)).getData());
			} else {
				responses2.add(new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) sqNr, TestUtils.getRandomBytes(50)).getData());
			}
		}
		
		List<byte[]> mockResponses = new ArrayList<byte[]>(responses2);
		Reader reader = createReaderMock(mockResponses, 0);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		NfcMessage toSend = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x01, new byte[] { 0x00, 0x01 });
		transceiver.transceive(toSend.getData());
		
		verify(reader, times(nofFragments)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(nofFragments*50, response.length);
	}
	
	@Test
	public void testResumeSession_IOException_whileSending() throws InterruptedException, IOException, ReaderException {
		/*
		 * Scenario: Reader writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After sending the first fragment (and before sending
		 * the second), Reader throws an IOException, which simulates a
		 * connection lost. Reader then calls initNfc() again (which would be
		 * called by the onTagDiscovered()). This tests that the session is
		 * resumed correctly.
		 */
		
		reset();
		
		final NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		final NfcMessage response2 = new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x00, null);
		final NfcMessage response3 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		final NfcMessage response4 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		final NfcMessage response5 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(50));
		final NfcMessage response6 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(50));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData(), response6.getData());
		Reader reader = createReaderMock(mockResponses, 2);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		transceiver.initNfc();
		
		verify(reader, times(7)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());

		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(150, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testResumeSession_IOException_whileReceiving() throws InterruptedException, IOException, ReaderException {
		/*
		 * Scenario: Reader writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before requesting
		 * the second one), Reader throws an IOException, which simulates a
		 * connection lost. Reader then calls initNfc() again (which would be
		 * called by the onTagDiscovered()). This tests that the session is
		 * resumed correctly.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		NfcMessage response4 = new NfcMessage(NfcMessage.AID_SELECTED, (byte) 0x00, null);
		NfcMessage response5 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x04, TestUtils.getRandomBytes(40));
		NfcMessage response6 = new NfcMessage(NfcMessage.DEFAULT, (byte) 0x05, TestUtils.getRandomBytes(30));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData(), response5.getData(), response6.getData());
		Reader reader = createReaderMock(mockResponses, 4);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		transceiver.initNfc();
		
		verify(reader, times(7)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
		Thread.sleep(THREAD_SLEEP_TIME);
		assertTrue(nfcMessageSend);
		assertTrue(nfcMessageReceived);
		assertNotNull(response);
		assertEquals(120, response.length);
		assertFalse(nfcInitFailed);
		assertFalse(nfcInitialized);
		assertFalse(nfcError);
		assertNull(nfcErrorMessage);
	}
	
	@Test
	public void testResumeSession_exceedThreshold() throws InterruptedException, IOException, ReaderException {
		/*
		 * Scenario: Reader writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before
		 * requesting the second one), Reader throws an IOException, which
		 * simulates a connection lost. Reader then calls initNfc() again (which
		 * would be called by the onTagDiscovered()), but waits too long to do
		 * so. HCE interprets this as a new session. This tests that the session
		 * is resumed correctly.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		NfcMessage response4 = new NfcMessage((byte) (NfcMessage.AID_SELECTED | NfcMessage.START_PROTOCOL), (byte) 0x00, null);
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData(), response4.getData());
		Reader reader = createReaderMock(mockResponses, 4);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		/*
		 * This has no effect on the test case, because the START_PROTOCOL flag
		 * has been returned from the HCE.
		 */
		Thread.sleep(Config.SESSION_RESUME_THRESHOLD);
		
		transceiver.initNfc();
		
		verify(reader, times(5)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
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
	public void testResumeSession_exceedThreshold_errorMsg() throws InterruptedException, IOException, ReaderException {
		/*
		 * Scenario: Reader writes 1 message (3 fragments) and gets 1 message (3
		 * fragments) back. After receiving the first fragment (and before
		 * requesting the second one), Reader throws an IOException, which
		 * simulates a connection lost. Since there is no initNfc() called
		 * (which would be called by the onTagDiscovered()), Reader propagates
		 * the error message after the threshold time is exceeded. This tests
		 * that the session resume thread waits the correct amount of time
		 * before shutting down.
		 */
		
		reset();
		
		NfcMessage response1 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x01, null);
		NfcMessage response2 = new NfcMessage(NfcMessage.GET_NEXT_FRAGMENT, (byte) 0x02, null);
		NfcMessage response3 = new NfcMessage(NfcMessage.HAS_MORE_FRAGMENTS, (byte) 0x03, TestUtils.getRandomBytes(50));
		
		List<byte[]> mockResponses = Arrays.asList(response1.getData(), response2.getData(), response3.getData());
		Reader reader = createReaderMock(mockResponses, 4);
		
		ExternalNfcTransceiver transceiver = new ExternalNfcTransceiver(eventHandler, userId, reader);
		transceiver.setEnabled(true);
		
		/*
		 * times 2 results in 3 fragments because of the headers
		 */
		transceiver.transceive(TestUtils.getRandomBytes(2*ExternalNfcTransceiver.MAX_WRITE_LENGTH));
		
		verify(reader, times(4)).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		
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
	
	/*
	 * Returns the Reader mock, which answers with one IOException at the
	 * desired place (beginning with 1).
	 */
	private Reader createReaderMock(final List<byte[]> mockResponses, int placeOfIOException) throws ReaderException {
		Reader reader = mock(Reader.class);
		when(reader.isOpened()).thenReturn(true);
		
		Stubber stubber = null;
		int index = 0;
		for (final byte[] bytes : mockResponses) {
			index++;
			Answer<Integer> a = null;
			if (index == placeOfIOException) {
				a = new Answer<Integer>() {
					@Override
					public Integer answer(InvocationOnMock invocation) throws Throwable {
						throw new ReaderException();
					}
				};
				
				if (stubber == null) {
					stubber = doAnswer(a);
				} else {
					stubber.doAnswer(a);
				}
			}
			
			a = new Answer<Integer>() {
				@Override
				public Integer answer(InvocationOnMock invocation) throws Throwable {
					Object[] arguments = invocation.getArguments();
					if (arguments != null && arguments.length > 0 && bytes != null) {
						byte[] temp = (byte[]) arguments[3];
						for (int i=0; i<bytes.length; i++) {
							temp[i] = bytes[i];
						}
						return bytes.length;
					} else {
						return 1;
					}
				}
			};
			
			if (stubber == null) {
				stubber = doAnswer(a);
			} else {
				stubber.doAnswer(a);
			}
		}
		
		if (placeOfIOException > index) {
			Answer<Integer> a = new Answer<Integer>() {
				
				@Override
				public Integer answer(InvocationOnMock invocation) throws Throwable {
					throw new ReaderException();
				}
			};
				
			if (stubber == null) {
				stubber = doAnswer(a);
			} else {
				stubber.doAnswer(a);
			}
		}
		
		stubber.when(reader).transmit(eq(0), any(byte[].class), anyInt(), any(byte[].class), anyInt());
		return reader;
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
