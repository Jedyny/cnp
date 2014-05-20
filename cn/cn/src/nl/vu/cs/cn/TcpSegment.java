package nl.vu.cs.cn;

import java.nio.ByteBuffer;

/* package */ final class TcpSegment {

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

	/* package */ short from;

	/* package */ short to;

	/* package */ int seq;

	/* package */ int ack;

	/* package */ static short ACK_FLAG = 16;

	/* package */ static short PUSH_FLAG = 8;

	/* package */ static short SYN_FLAG = 2;

	/* package */ static short FIN_FLAG = 1;

	/* package */ short flags;

	/* package */ short checksum;

	/* package */ byte[] data;

}
