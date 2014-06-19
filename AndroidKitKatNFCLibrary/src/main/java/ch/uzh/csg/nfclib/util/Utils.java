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
	
	public static long byteArrayToLong(byte[] array, int offset) {
	    return
	      ((long)(array[offset]   & 0xff) << 56) |
	      ((long)(array[offset+1] & 0xff) << 48) |
	      ((long)(array[offset+2] & 0xff) << 40) |
	      ((long)(array[offset+3] & 0xff) << 32) |
	      ((long)(array[offset+4] & 0xff) << 24) |
	      ((long)(array[offset+5] & 0xff) << 16) |
	      ((long)(array[offset+6] & 0xff) << 8) |
	      ((long)(array[offset+7] & 0xff));
	  }

}
