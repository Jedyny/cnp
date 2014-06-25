package nl.vu.cs.cn.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

// a ByteBuffer implementation that increases its size when needed
public class InfiniteByteBuffer {
	
	// returns a new instance of this class with the given initial size
	public static InfiniteByteBuffer withCapacity(int initialSize) {
		InfiniteByteBuffer product = new InfiniteByteBuffer();
		product.buffer = ByteBuffer.allocate(initialSize);
		product.length = initialSize;
		return product;
	}
	
	public byte[] array() {
		return buffer.array();
	}
	
	public byte get(int index) {
		return buffer.get(index);
	}
	
	public short getShort(int index) {
		return buffer.getShort(index);
	}
	
	public int getInt(int index) {
		return buffer.getInt(index);
	}
	
	public void getArray(int index, byte[] dst, int dstOffset, int dstLength) {
		System.arraycopy(buffer.array(), index, dst, dstOffset, dstLength);
	}
	
	public void put(int index, byte value) {
		buffer.put(index, value);
	}
	
	public void putShort(int index, short value) {
		buffer.putShort(index, value);
	}
	
	public void putInt(int index, int value) {
		buffer.putInt(index, value);
	}
	
	public void putArray(int index, byte[] src, int srcOffset, int srcLength) {
		if (index + srcLength >= length) {
			for (; index + srcLength >= length; length *= 2);
			buffer = ByteBuffer.wrap(Arrays.copyOf(buffer.array(), length));
		}
		System.arraycopy(src, srcOffset, buffer.array(), index, srcLength);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("[");
		builder.append("length = ").append(length).append("; ");
		builder.append("data = ").append(Arrays.toString(buffer.array())).append("]; ");
		return builder.toString();
	}
	
	private int length;
	
	private ByteBuffer buffer;
	
	private InfiniteByteBuffer() {
		/* use factory methods instead */
	}

}
