package nl.vu.cs.cn;

import java.nio.ByteBuffer;

/* package */ final class TcpSegment {

	public void fromByteArray(byte[] data, int length) {
		if (data != buffer.array()) {
			buffer.put(data, 0, 0);
		}
		this.length = length;
	}

	public byte[] toByteArray() {
		return buffer.array();
	}
	
	public short getFrom() {
		return buffer.getShort(FROM_IX);
	}
	
	public short getTo() {
		return buffer.getShort(TO_IX);
	}
	
	public int getSeq() {
		return buffer.getInt(SEQ_IX);
	}
	
	public int getAck() {
		return buffer.getInt(ACK_IX);
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
	
	public void setFrom(short from) {
		buffer.putShort(FROM_IX, from);
	}
	
	public void setTo(short to) {
		buffer.putShort(TO_IX, to);
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
	
	/* package */ static int TCP_HEADER_LENGTH = 20;
	
	/* package */ static int TCP_MAX_LENGTH = 40;
	
	/* package */ static short ACK_FLAG = 16;

	/* package */ static short PUSH_FLAG = 8;

	/* package */ static short SYN_FLAG = 2;

	/* package */ static short FIN_FLAG = 1;
	
	/* package */ ByteBuffer buffer = ByteBuffer.allocate(TCP_MAX_LENGTH);
	
	/* package */ int length;
	
	private static int FROM_IX = 0;
	
	private static int TO_IX = 2;
	
	private static int SEQ_IX = 4;
	
	private static int ACK_IX = 8;
	
	private static int FLAGS_IX = 13;
	
	private static int WINDOW_IX = 14;
	
	private static int CHECKSUM_IX = 16;

	private static int DATA_IX = 20;
}
