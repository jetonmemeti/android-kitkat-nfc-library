package ch.uzh.csg.nfclib.util;


/**
 * This is a class for miscellaneous functions.
 * 
 * @author Jeton Memeti
 * @author Thomas Bocek
 * 
 */
public class Utils {
	
	/**
	 * Returns a long as a byte array.
	 */
	public static byte[] longToByteArray(long value) {
	    return new byte[] {
	        (byte) (value >> 56),
	        (byte) (value >> 48),
	        (byte) (value >> 40),
	        (byte) (value >> 32),
	        (byte) (value >> 24),
	        (byte) (value >> 16),
	        (byte) (value >> 8),
	        (byte) value
	    };
	}

}
