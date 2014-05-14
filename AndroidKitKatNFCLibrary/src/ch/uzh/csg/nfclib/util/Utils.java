package ch.uzh.csg.nfclib.util;

import java.nio.ByteBuffer;

//TODO: javadoc
public class Utils {
	
	public static byte[] getLongAsBytes(long l) {
		return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(l).array();
	}
	
	public static long getBytesAsLong(byte[] b) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
		buffer.put(b);
		buffer.flip();
		return buffer.getLong();
	}

}
