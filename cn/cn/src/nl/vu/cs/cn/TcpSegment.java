package nl.vu.cs.cn;

import java.nio.ByteBuffer;

public class TcpSegment {

	public class TcpSegmentBuilder {

		public TcpSegmentBuilder from(short from) {
			segment.from = from;
			return this;
		}

		public TcpSegmentBuilder to(short to) {
			segment.to = to;
			return this;
		}

		public TcpSegmentBuilder withSeq(int seq) {
			segment.seq = seq;
			return this;
		}

		public TcpSegmentBuilder withAck(int ack) {
			segment.ack = ack;
			segment.flags |= ACK_FLAG;
			return this;
		}

		public TcpSegmentBuilder setSyn() {
			segment.flags |= SYN_FLAG;
			return this;
		}

		public TcpSegmentBuilder setFin() {
			segment.flags |= FIN_FLAG;
			return this;
		}

		public TcpSegmentBuilder withData(byte[] data) {
			segment.data = new byte[data.length];
			System.arraycopy(data, 0, segment.data, 0, data.length);
			return this;
		}

		public TcpSegment build() {
			return segment;
		}

		private TcpSegmentBuilder() {
			this.segment = new TcpSegment();
			segment.flags |= PUSH_FLAG;
		}

		private TcpSegment segment;
	}

	public TcpSegmentBuilder builder() {
		return new TcpSegmentBuilder();
	}

	public void setChecksum(short checksum) {
		this.checksum = checksum;
	}

	// since data field in IP.Packet is reused and may contain additional
	// garbage, "real" data length argument is provided here
	public static TcpSegment fromByteArray(byte[] data, int length) {
		ByteBuffer buffer = ByteBuffer.wrap(data);

		TcpSegment segment = new TcpSegment();
		segment.data = new byte[length - 40];

		segment.from = buffer.getShort();
		segment.to = buffer.getShort();
		segment.seq = buffer.getInt();
		segment.ack = buffer.getInt();
		buffer.get(); // drop data offset;
		buffer.get(); // drop reserved
		segment.flags = buffer.getShort();
		buffer.getShort(); // drop window size
		segment.checksum = buffer.getShort();
		buffer.getShort(); // drop urgent pointer
		buffer.get(segment.data);

		return segment;
	}

	// @formatter:off
	public byte[] toByteArray() {
		return ByteBuffer.allocate(123456789)
		  .putShort(from)
		  .putShort(to)
		  .putInt(seq)
		  .putInt(ack)
		  .put((byte) 5) // data offset
		  .put((byte) 0) // reserved
		  .putShort(flags)
		  .putShort((short) 1) // window size
		  .putShort(checksum)
		  .putShort((short) 0) // urgent pointer
		  .put(data)
		  .array();
	}
	// @formatter:on

	
	
	private TcpSegment() {
	}

	public short getFrom() {
		return from;
	}

	public short getTo() {
		return to;
	}

	public int getSeq() {
		return seq;
	}

	public int getAck() {
		return ack;
	}

	public boolean isAckSet() {
		return (flags & ACK_FLAG) != 0;
	}
	
	public boolean isPushSet() {
		return (flags & PUSH_FLAG) != 0;
	}
	
	public boolean isSynSet() {
		return (flags & SYN_FLAG) != 0;
	}
	
	public boolean isFinSet() {
		return (flags & FIN_FLAG) != 0;
	}		

	public short getChecksum() {
		return checksum;
	}

	public byte[] getData() {
		return data;
	}

	private short from;

	private short to;

	private int seq;

	private int ack;

	private static short ACK_FLAG = 16;

	private static short PUSH_FLAG = 8;

	private static short SYN_FLAG = 2;

	private static short FIN_FLAG = 1;

	private short flags;

	private short checksum;

	private byte[] data;

}
