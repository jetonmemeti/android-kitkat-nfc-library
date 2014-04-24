package ch.uzh.csg.nfclib;

import android.os.Handler;

public abstract class NfcEventHandler extends Handler {
	
	public abstract void handleMessage(NfcEvent event, Object object);

}
