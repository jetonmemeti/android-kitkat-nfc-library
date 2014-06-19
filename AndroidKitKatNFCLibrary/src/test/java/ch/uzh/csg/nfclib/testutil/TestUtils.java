package ch.uzh.csg.nfclib.testutil;

import java.util.Random;

public class TestUtils {
	
	public static byte[] getRandomBytes(int size) {
		byte[] bytes = new byte[size];
		new Random().nextBytes(bytes);
		return bytes;
	}

}
