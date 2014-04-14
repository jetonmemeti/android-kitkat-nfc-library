package ch.uzh.csg.nfclib;

import android.os.Handler;
import android.os.Message;

public abstract class NfcEventHandler extends Handler {
	
	public abstract void handleMessage(NfcEvent event, Message msg);

}
