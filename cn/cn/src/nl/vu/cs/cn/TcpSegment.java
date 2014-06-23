package nl.vu.cs.cn;

import nl.vu.cs.cn.util.InfiniteByteBuffer;

/* package */ final class TcpSegment {

	/* package */ static int TCP_SEGMENT_INIT_LENGTH = 32;
	
	/* package */ static int TCP_MAX_DATA_LENGTH = 8152;
	
	// wrap the given data inside this segment
	// the previous data is lost
	public void fromByteArray(byte[] data, int length) {
		if (data != buffer.array()) {
			buffer.putArray(0, data, 0, length);
		}
		this.length = length;
		this.dataLength = length - TCP_HEADER_LENGTH;
	}

	public byte[] toByteArray() {
		return buffer.array();
	}
	
	public short getFromPort() {
		return buffer.getShort(FROM_PORT_IX);
	}
	
	public short getToPort() {
		return buffer.getShort(TO_PORT_IX);
	}
	
	public int getSeq() {
		return buffer.getInt(SEQ_IX);
	}
	
	public int getAck() {
		return buffer.getInt(ACK_IX);
	}
	
	public short getWindow() {
		return buffer.getShort(WINDOW_IX);
	}
	
	public short getChecksum() {
		return buffer.getShort(CHECKSUM_IX);
	}
	
	// populate the given array with this segment data
	public void getData(byte[] dst, int dstOffset, int maxlen) {
		int toCopy = Math.min(maxlen, dataLength);
		buffer.getArray(DATA_IX, dst, dstOffset, toCopy);
	}
	
	// checks if this segment has all flags from allOfMask
	// and none of from noneOfMask
	public boolean hasFlags(int allOfMask, int noneOfMask) {
		int flags = buffer.get(FLAGS_IX);
		
		return (flags & allOfMask) == allOfMask 
				&& (flags | ~noneOfMask) == ~noneOfMask;
	}
	
	public boolean hasSynFlag() {
		return (buffer.get(FLAGS_IX) & SYN_FLAG) != 0;
	}
	
	public boolean hasAckFlag() {
		return (buffer.get(FLAGS_IX) & ACK_FLAG) != 0;
	}
	
	public boolean hasFinFlag() {
		return (buffer.get(FLAGS_IX) & FIN_FLAG) != 0;
	}
	
	public boolean hasPushFlag() {
		return (buffer.get(FLAGS_IX) & PUSH_FLAG) != 0;
	}
	
	public void setFromPort(short from) {
		buffer.putShort(FROM_PORT_IX, from);
	}
	
	public void setToPort(short to) {
		buffer.putShort(TO_PORT_IX, to);
	}
	
	public void setSeq(int seq) {
	
		buffer.putInt(SEQ_IX, seq);
	}
	
	public void setAck(int ack) {
		buffer.putInt(ACK_IX, ack);
	}
	
	// always 5
	// have to be moved, because offset field here is in reality
	// offset + reserved combined, since Java has not 4-bit type
	public void setDataOffset() {
		buffer.put(DATA_OFFSET_IX, (byte) 80);
	}
	
	public void setFlags(byte flags) {
		buffer.put(FLAGS_IX, flags);
	}
	
	public void setWindowSize(short size) {
		buffer.putShort(WINDOW_IX, size);
	}
	
	public void setChecksum(short checksum) {
		buffer.putShort(CHECKSUM_IX, checksum);
	}
	
	// populate this segment with the given data
	// internal buffer size is automatically increased when needed
	public void setData(byte[] src, int srcOffset, int length) {
		buffer.putArray(DATA_IX, src, srcOffset, length);
		this.length = length + TCP_HEADER_LENGTH;
		this.dataLength = length;
	}
	
	@Override
	public String toString() {
		byte[] data = new byte[dataLength];
		getData(data, 0, dataLength);
		
		StringBuilder builder = new StringBuilder("[");
		builder.append("from_port = ").append(getFromPort()).append("; ");
		builder.append("to_port = ").append(getToPort()).append("; ");
		builder.append("seq_number = ").append(getSeq()).append("; ");
		builder.append("ack_number = ").append(getAck()).append("; ");
		builder.append("syn_flag = ").append(hasSynFlag()).append("; ");
		builder.append("ack_flag = ").append(hasAckFlag()).append("; ");
		builder.append("fin_flag = ").append(hasFinFlag()).append("; ");
		builder.append("push_flag = ").append(hasPushFlag()).append("; ");
		builder.append("window_size = ").append(getWindow()).append("; ");
		builder.append("checksum = ").append(getChecksum()).append("; ");
		builder.append("data = \"").append(new String(data)).append("\"; ");
		builder.append("segment_length = ").append(length).append("; ");
		builder.append("data_length = ").append(dataLength).append("]");
		
		return builder.toString();
	}
	
	/* package */ static int TCP_HEADER_LENGTH = 20;
	
	/* package */ static short ACK_FLAG = 16;

	/* package */ static short PUSH_FLAG = 8;

	/* package */ static short SYN_FLAG = 2;

	/* package */ static short FIN_FLAG = 1;
	
	/* package */ InfiniteByteBuffer buffer = InfiniteByteBuffer.withCapacity(TCP_SEGMENT_INIT_LENGTH);
	
	/* package */ int length = TCP_HEADER_LENGTH;
	
	/* package */ int dataLength;
	
	private static int FROM_PORT_IX = 0;
	
	private static int TO_PORT_IX = 2;
	
	private static int SEQ_IX = 4;
	
	private static int ACK_IX = 8;
	
	private static int DATA_OFFSET_IX = 12;
	
	private static int FLAGS_IX = 13;
	
	private static int WINDOW_IX = 14;
	
	private static int CHECKSUM_IX = 16;

	private static int DATA_IX = 20;
}
