package ch.uzh.csg.nfclib;

import android.os.Handler;

//TODO: javadoc
public abstract class NfcEventHandler extends Handler {
	
	public abstract void handleMessage(NfcEvent event, Object object);

}
