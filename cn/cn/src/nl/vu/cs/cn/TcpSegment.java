package nl.vu.cs.cn;

import java.nio.ByteBuffer;

/* package */ final class TcpSegment {

	/* package */ static int TCP_MAX_DATA_LENGTH = 40;
	
	public void fromByteArray(byte[] data, int length) {
		if (data != buffer.array()) {
			System.arraycopy(data, 0, buffer.array(), 0, length);
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
	
	public void getData(byte[] dst, int offset) {
		System.arraycopy(buffer.array(), DATA_IX, dst, offset, dataLength);
	}
	
	public boolean hasSynFlag() {
		return (buffer.getShort(FLAGS_IX) & SYN_FLAG) != 0;
	}
	
	public boolean hasAckFlag() {
		return (buffer.getShort(FLAGS_IX) & ACK_FLAG) != 0;
	}
	
	public boolean hasFinFlag() {
		return (buffer.getShort(FLAGS_IX) & FIN_FLAG) != 0;
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
	
	public void setFlags(short flags) {
		buffer.putShort(FLAGS_IX, flags);
	}
	
	public void setWindowSize(short size) {
		buffer.putShort(WINDOW_IX, size);
	}
	
	public void setChecksum(short checksum) {
		buffer.putShort(CHECKSUM_IX, checksum);
	}
	
	public void setData(byte[] src, int offset, int length) {
		System.arraycopy(src, offset, buffer.array(), DATA_IX, length);
		this.length = length + TCP_MAX_DATA_LENGTH;
		this.dataLength = length;
	}
	
	/* package */ static int TCP_HEADER_LENGTH = 20;
	
	/* package */ static short ACK_FLAG = 16;

	/* package */ static short PUSH_FLAG = 8;

	/* package */ static short SYN_FLAG = 2;

	/* package */ static short FIN_FLAG = 1;
	
	/* package */ ByteBuffer buffer = ByteBuffer.allocate(TCP_HEADER_LENGTH + TCP_MAX_DATA_LENGTH);
	
	/* package */ int length;
	
	/* package */ int dataLength;
	
	private static int FROM_PORT_IX = 0;
	
	private static int TO_PORT_IX = 2;
	
	private static int SEQ_IX = 4;
	
	private static int ACK_IX = 8;
	
	private static int FLAGS_IX = 13;
	
	private static int WINDOW_IX = 14;
	
	private static int CHECKSUM_IX = 16;

	private static int DATA_IX = 20;
}
