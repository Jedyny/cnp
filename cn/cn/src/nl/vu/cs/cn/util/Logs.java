package nl.vu.cs.cn.util;


public class Logs {
	
	// source: http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes, int offset, int length) {
	    char[] hexChars = new char[length * 3 - 1];
	    for (int j = offset; j < offset + length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 3] = hexArray[v >>> 4];
	        hexChars[j * 3 + 1] = hexArray[v & 0x0F];
	        if (j < offset + length - 1) {
	        	hexChars[j * 3 + 2] = ' ';
	        }
	    }
	    return new String(hexChars);
	}
	
	// forbid creation
	private Logs() { }

}
