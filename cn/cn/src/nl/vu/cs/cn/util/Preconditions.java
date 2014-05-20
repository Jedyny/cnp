package nl.vu.cs.cn.util;

public final class Preconditions {
	
	public static void checkArgument(boolean condition) {
		if (!condition) {
			throw new IllegalArgumentException();
		}
	}
	
	public static <T> T checkNotNull(T reference) {
		if (reference == null) {
			throw new NullPointerException();
		}
		return reference;
	}
	
	public static void checkState(boolean condition) {
		if (!condition) {
			throw new IllegalStateException();
		}
	}
	
	private Preconditions() {
		/* prevent creation */
	}
}
