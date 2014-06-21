package ch.uzh.csg.nfclib;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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

import android.app.Activity;
import android.util.Log;
import ch.uzh.csg.nfclib.NfcEvent;
import ch.uzh.csg.nfclib.NfcMessage;
import ch.uzh.csg.nfclib.NfcMessage.Type;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class InternalNfcTransceiverTest {
	
	public static long userId = 1234567891011121314L;
	
	private Activity activity = mock(Activity.class);

	private static class State {
		NfcEvent.Type event;
		private byte[] response = null;
		private String nfcErrorMessage = null;
	}
	
	private List<State> states = new ArrayList<State>();
	
	private NfcEvent eventHandler = new NfcEvent() {
		@Override
		public synchronized void handleMessage(NfcEvent.Type event, Object object) {
			State state = new State();
			state.event = event;
			switch (event) {
			case FATAL_ERROR:
				if (object != null) {
					state.nfcErrorMessage = (String) object;
				}
				break;
			case MESSAGE_RECEIVED_HCE:
			case MESSAGE_RECEIVED:
				if (object != null && object instanceof byte[]) {
					state.response = (byte[]) object;
				}
				break;
			default:
				break;
			}
			states.add(state);
		}
	};
	
	private class MyNfcTransceiverImpl implements NfcTransceiverImpl {
		
		private boolean enabled = false;
		private final CustomHostApduService customHostApduService;
		
		public MyNfcTransceiverImpl(CustomHostApduService customHostApduService) {
	        this.customHostApduService = customHostApduService;
        }

		@Override
		public NfcMessage write(NfcMessage input) throws NfcLibException, IOException {
			byte[] repsonse = customHostApduService.processCommandApdu(input.bytes());
			return new NfcMessage(repsonse);
		}
		
		@Override
		public int maxLen() {
			return 30;
		}
		
		@Override
		public boolean isEnabled() {
			return enabled;
		}
		
		@Override
		public void enable(Activity activity) throws NfcLibException {
			enabled = true;
		}
		
		@Override
		public void disable(Activity activity) throws IOException {
			enabled = false;
		}
	};
	
	public NfcTransceiver createTransceiver() {
		return createTransceiver(( byte[])null);
	}
	
	public NfcTransceiver createTransceiver(final byte[] payload) {
		final CustomHostApduService customHostApduService = new CustomHostApduService(activity, eventHandler, new IMessageHandler() {
			
			@Override
			public byte[] handleMessage(byte[] message) {
				if(payload !=null) {
					return payload;
				} else {
					return new NfcMessage(Type.DEFAULT).bytes();
				}
				
			}
		});
		return createTransceiver(customHostApduService);
	}
	
	public NfcTransceiver createTransceiver(CustomHostApduService customHostApduService) {
		
		MyNfcTransceiverImpl myNfcTransceiverImpl = new MyNfcTransceiverImpl(customHostApduService);
		return new NfcTransceiver(eventHandler, activity, userId, myNfcTransceiverImpl);
	}
	
	@Before
	public void before() {
		PowerMockito.mockStatic(Log.class);
		PowerMockito.when(Log.d(Mockito.anyString(), Mockito.anyString())).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println("DBG:"+Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
		PowerMockito.when(Log.e(Mockito.anyString(), Mockito.anyString())).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println("ERR:"+Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
		PowerMockito.when(Log.w(Mockito.anyString(), Mockito.anyString())).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println("WRN:"+Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
		PowerMockito.when(Log.e(Mockito.anyString(), Mockito.anyString(), Mockito.any(Throwable.class))).then(new Answer<Integer>() {
			@Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
	            System.err.println("ERR:"+Arrays.toString(invocation.getArguments()));
	            return 0;
            }
		});
	}
	
	private void reset() {
		states.clear();
	}
	
	@Test
	public void testInitNfc() throws IOException, InterruptedException {
		reset();
		
		NfcTransceiver transceiver = createTransceiver();
		transceiver.initNfc();
		
		assertEquals(2, states.size());
		//for(State state:states) {
		//	System.err.println(state.event);
		//}
		
		assertEquals(NfcEvent.Type.INITIALIZED_HCE, states.get(0).event);
		assertEquals(NfcEvent.Type.INITIALIZED, states.get(1).event);
	}
	
	@Test
	public void testInitNfc_Fail() throws IOException, InterruptedException, NfcLibException {
		reset();
		
		CustomHostApduService c = mock(CustomHostApduService.class);
		//return error after first message
		when(c.processCommandApdu(any(byte[].class))).thenReturn(new NfcMessage(Type.DEFAULT).error().bytes());
		NfcTransceiver transceiver = createTransceiver(c);
		transceiver.initNfc();
		
		assertEquals(1, states.size());
		assertEquals(NfcEvent.Type.INIT_FAILED, states.get(0).event);
		
	}
	
	
	@Test
	public void testTransceive_Reassembly() throws IOException, IllegalArgumentException, InterruptedException {
		reset();
		
		NfcTransceiver transceiver = createTransceiver();
		transceiver.initNfc();
		
		byte[] me = TestUtils.getRandomBytes(200);
		transceiver.transceive(me);
		
		assertEquals(6 , states.size());
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED, states.get(5).event);
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED_HCE, states.get(2).event);
		assertTrue(Arrays.equals(me, states.get(2).response));
		
	}
	
	@Test
	public void testTransceiveConsecutive() throws IOException, IllegalArgumentException, InterruptedException {
		reset();
		
		NfcTransceiver transceiver = createTransceiver();
		transceiver.initNfc();
		
		byte[] me = TestUtils.getRandomBytes(200);
		transceiver.transceive(me);
		
		assertEquals(6 , states.size());
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED, states.get(5).event);
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED_HCE, states.get(2).event);
		assertTrue(Arrays.equals(me, states.get(2).response));
		
		reset();
		
		me = TestUtils.getRandomBytes(300);
		transceiver.transceive(me);
		
		assertEquals(4 , states.size());
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED, states.get(3).event);
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED_HCE, states.get(0).event);
		assertTrue(Arrays.equals(me, states.get(0).response));
		
	}
	
	@Test
	public void testTransceiveBigMessages() throws IOException, IllegalArgumentException, InterruptedException {
		reset();
		
		byte[] me1 = TestUtils.getRandomBytes(200);
		NfcTransceiver transceiver = createTransceiver(me1);
		transceiver.initNfc();
		
		byte[] me2 = TestUtils.getRandomBytes(200);
		transceiver.transceive(me2);
		
		for(State state:states) {
			System.err.println(state.event);
		}
		
		assertEquals(6 , states.size());
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED, states.get(5).event);
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED_HCE, states.get(2).event);
		assertTrue(Arrays.equals(me2, states.get(2).response));
		
		System.err.println(Arrays.toString(me1));
		System.err.println(Arrays.toString(states.get(5).response));
		
		assertTrue(Arrays.equals(me1, states.get(5).response));
		
		reset();
		
		me2 = TestUtils.getRandomBytes(300);
		transceiver.transceive(me2);
		
		assertEquals(4 , states.size());
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED, states.get(3).event);
		assertEquals(NfcEvent.Type.MESSAGE_RECEIVED_HCE, states.get(0).event);
		assertTrue(Arrays.equals(me2, states.get(0).response));
		assertTrue(Arrays.equals(me1, states.get(3).response));
		
		for(State state:states) {
			System.err.println(state.event);
		}
	}
	
	
}
