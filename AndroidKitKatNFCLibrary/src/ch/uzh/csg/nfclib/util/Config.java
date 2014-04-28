package ch.uzh.csg.nfclib.util;

public class Config {
	
	/*
	 * Indicates the amount of retransmissions requested or sent when an error
	 * occurred. 1 means that a message will be retransmitted at most 1 time if
	 * the first write failed for some reason (i.e., we got not the sequence
	 * number we expected)
	 */
	public static final int MAX_RETRANSMITS = 1;
	
	public static final long SESSION_RESUME_THRESHOLD = 300; //in ms

}
