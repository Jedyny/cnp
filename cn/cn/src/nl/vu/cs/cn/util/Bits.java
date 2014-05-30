package nl.vu.cs.cn.util;

public class Bits {
	
	public static int reverseOrder(int unsigned) {
		return (unsigned >>> 24)
				| (unsigned >>> 16 & 0xff00)
			    | (unsigned << 8 & 0xff0000)
				| (unsigned << 24 & 0xff000000);
	}
	
	private Bits() {
		
	}

}
