package ch.uzh.csg.nfclib;

public interface NfcEventInterface {

	public abstract void handleMessage(NfcEvent event, Object object);

}