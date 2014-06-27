package ch.uzh.csg.nfclib;

import java.util.Arrays;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

//TODO: javadoc
public class CustomHostApduService2 extends HostApduService {

	public static final String TAG = "##NFC## CustomHostApduService2";

	private static NfcResponder nfcResponder;
	
	//private Object lock = new Object();
	private byte[] data = null;
	
	private final ISendLater sendLater = new ISendLater() {
		
		@Override
		public void sendLater(byte[] bytes) {
			if(data == null) {
				throw new IllegalArgumentException("cannot be null");
			}
			CustomHostApduService2.this.data = data;
		}
	};
	
	public static void init(NfcResponder nfcResponder2) {
		nfcResponder = nfcResponder2;
	}

	@Override
	public byte[] processCommandApdu(byte[] bytes, Bundle extras) {
		long start = System.currentTimeMillis();
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return null;
		}
		
		if(data != null) {
			NfcMessage first = NfcResponder.fragmentData(data);
			
			//TODO: fix, this -> ugly
			NfcResponder.lastMessageReceived = first;
			
			
			data = NfcResponder.prepareWrite(first);
//			Log.d(TAG, "return from polling: "+Arrays.toString(data));
			byte[] tmp2 = data;
			data = null;
			return tmp2;
		}
		
		byte[] tmp1 = nfcResponder.processCommandApdu(bytes, sendLater);
		
		
		//if payment lib returns null, intial polling
		if(tmp1 == null) {
			NfcMessage polling = new NfcMessage(NfcMessage.Type.POLLING).request();
			Log.d(TAG, "polling send");
			byte[] tmp = NfcResponder.prepareWrite(polling);
			return tmp;
		}
		
		Log.d(TAG, "about to return "+Arrays.toString(data));
		Log.e(TAG, "time to respond: "+(System.currentTimeMillis() - start));
		return tmp1;
	}

	@Override
	public void onDeactivated(int reason) {
		if (nfcResponder == null) {
			Log.w(TAG, "no CustomHostApduService set");
			return;
		}
		nfcResponder.onDeactivated(reason);
	}
}
