package ch.uzh.ch.nfclib;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class CustomHostApduService extends HostApduService {

	@Override
	public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onDeactivated(int reason) {
		// TODO Auto-generated method stub
		
	}

}
