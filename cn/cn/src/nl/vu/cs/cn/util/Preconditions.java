package nl.vu.cs.cn.util;

/*
 * Simple Preconditions library similar to one found in Guava.
 */
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
