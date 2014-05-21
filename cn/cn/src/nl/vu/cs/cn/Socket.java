package nl.vu.cs.cn;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.IP.Packet;

import static nl.vu.cs.cn.util.Preconditions.checkNotNull;
import static nl.vu.cs.cn.util.Preconditions.checkState;

/**
 * This class represents a TCP socket.
 * 
 */
public final class Socket {

	/**
	 * Construct a client socket.
	 */
	/* package */Socket(IP ip) {
		// TODO: Method stub.
	}

	/**
	 * Construct a server socket bound to the given local port.
	 * 
	 * @param port
	 *            the local port to use
	 */
	/* package */Socket(IP ip, int port) {
		// TODO: Method stub.
	}

	/**
	 * Connect this socket to the specified destination and port.
	 * 
	 * @param dst
	 *            the destination to connect to
	 * @param port
	 *            the port to connect to
	 * @return true if the connect succeeded.
	 */
	public boolean connect(IpAddress dst, int port) throws IOException {
		checkState(state == ConnectionState.CLOSED);
		
		localSequenceNumber = TCP.getNextSeq();
		remoteAddress = dst.getAddress(); // TODO: Should be in big-endian!
		remotePort = (short) port;
		
		segment.setFrom(localPort);
		segment.setTo(remotePort);
		segment.setSeq(localSequenceNumber);
		segment.setFlags((short) TcpSegment.SYN_FLAG);
		segment.setWindowSize((short) 1);
		sendSegment(segment);
		
		receiveSegment(segment); // TODO: Receive with timeout
		
		if (segment.getFrom() == remotePort && segment.hasSynFlag() && segment.hasAckFlag() && segment.getAck() == localSequenceNumber + 1) {
			remoteSequenceNumber = segment.getSeq();
			
			segment.setFrom(localPort);
			segment.setTo(remotePort);
			segment.setSeq(localSequenceNumber);
			segment.setAck(remoteSequenceNumber + 1);
			segment.setFlags((short) TcpSegment.ACK_FLAG);
			segment.setWindowSize((short) 1);
			sendSegment(segment);
			
			state = ConnectionState.ESTABLISHED;
			return true;
		}
		
		return false;
	}

	// take into account that this method don't have timeout after receiving first syn
	// so it'll block on half-open connection
	/**
	 * Accept a connection on this socket. This call blocks until a connection
	 * is made.
	 */
	public void accept() throws IOException {
		checkState(state == ConnectionState.CLOSED);
		
		for (;;) {
			do {
				receiveSegment(segment);
			} while (!segment.hasSynFlag());
	
			localSequenceNumber = TCP.getNextSeq();
			remotePort = segment.getFrom();
			remoteSequenceNumber = segment.getSeq();
			
			segment.setFrom(localPort);
			segment.setTo(remotePort);
			segment.setSeq(localSequenceNumber);
			segment.setAck(remoteSequenceNumber + 1);
			segment.setFlags((short) (TcpSegment.SYN_FLAG | TcpSegment.ACK_FLAG));
			segment.setWindowSize((short) 1);
			sendSegment(segment);
			
			++localSequenceNumber;
			
			receiveSegment(segment); // <-- should receive with timeout
			
			if (segment.getFrom() == remotePort && !segment.hasSynFlag() && segment.hasAckFlag() && segment.getAck() == localSequenceNumber + 1) {
				break;
			}
		}
		
		state = ConnectionState.ESTABLISHED;
	}

	/**
	 * Reads bytes from the socket into the buffer. This call is not required to
	 * return maxlen bytes every time it returns.
	 * 
	 * @param buf
	 *            the buffer to read into
	 * @param offset
	 *            the offset to begin reading data into
	 * @param maxlen
	 *            the maximum number of bytes to read
	 * @return the number of bytes read, or -1 if an error occurs.
	 */
	public int read(byte[] buf, int offset, int maxlen) {
		checkNotNull(buf);
		checkState(state == ConnectionState.ESTABLISHED);
		
		// Read from the socket here.

		return -1;
	}

	/**
	 * Writes to the socket from the buffer.
	 * 
	 * @param buf
	 *            the buffer to
	 * @param offset
	 *            the offset to begin writing data from
	 * @param len
	 *            the number of bytes to write
	 * @return the number of bytes written or -1 if an error occurs.
	 */
	public int write(byte[] buf, int offset, int len) {
		checkNotNull(buf);
		checkState(state == ConnectionState.ESTABLISHED);

		// Write to the socket here.

		return -1;
	}

	/**
	 * Closes the connection for this socket. Blocks until the connection is
	 * closed.
	 * 
	 * @return true unless no connection was open.
	 */
	public boolean close() {
		checkState(state == ConnectionState.ESTABLISHED);
		
		// Close the socket cleanly here.

		return false;
	}
	
	// package to simplify testing
	/* package */ short checksumFor(TcpSegment segment) {
		long sum = 0;
		
		// ip pseudoheader
		sum += (localAddress >>> 16) + localAddress & 0xffff; 
		sum += (remoteAddress >>> 16) + remoteAddress & 0xffff;
		sum += IP.TCP_PROTOCOL;
		sum += (segment.length >>> 16) + segment.length & 0xffff;
		
		// real tcp
		for (int i = 0; i < segment.length - 1; i += 2) {
			sum += (segment.buffer.getShort(i) & 0xffff);
		}
		
		if (segment.length % 2 != 0) {
			sum += (segment.buffer.get(segment.length - 1) & 0xffff) << 8; 
		}
		
		// remainder
		sum += (sum >>> 16);
		
		// one's complement
		sum = ~sum & 0xffff;
		return (short) sum;
	}
	
	private boolean isValid(TcpSegment segment) {
		return segment.length < TcpSegment.TCP_HEADER_LENGTH && checksumFor(segment) == 0;
	}
	
	private Packet packetFrom(TcpSegment segment) {
		packet.source = localAddress;
		packet.destination = remoteAddress;
		packet.protocol = IP.TCP_PROTOCOL;
		packet.id = 1;
		packet.data = segment.toByteArray();
		packet.length = segment.length;
		return packet;
	}
	
	private TcpSegment segmentFrom(Packet packet) {
		segment.fromByteArray(packet.data, packet.length);
		return segment;
	}
	
	// package to simplify testing
	/* package */ void sendSegment(TcpSegment segment) throws IOException {
		segment.setChecksum(checksumFor(segment));
		ip.ip_send(packetFrom(segment));
	}
	
	// package to simplify testing
	/* package */ void receiveSegment(TcpSegment segment) throws IOException {
		do {
			ip.ip_receive(packet);
			segment = segmentFrom(packet);
		} while (!isValid(segment));
	}
	
	private IP ip;

	private ConnectionState state;

	private int localAddress;

	private int remoteAddress;

	private short localPort;

	private short remotePort;
	
	private int localSequenceNumber;
	
	private int remoteSequenceNumber;
	
	private Packet packet;
	
	private TcpSegment segment;
}