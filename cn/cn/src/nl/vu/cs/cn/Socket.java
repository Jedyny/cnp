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
	public boolean connect(IpAddress dst, int port) {

		return false;
	}

	/**
	 * Accept a connection on this socket. This call blocks until a connection
	 * is made.
	 */
	public void accept() throws IOException {

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
	
	private short checksumFor(TcpSegment segment) {
		// TODO: Method stub
		return 0;
	}
	
	private boolean isValid(TcpSegment segment) {
		return checksumFor(segment) == segment.checksum;
	}
	
	private Packet packetFrom(TcpSegment segment) {
		packet.source = localAddress;
		packet.destination = remoteAddress;
		packet.protocol = IP.TCP_PROTOCOL;
		packet.id = 1;
		packet.data = segment.toByteArray();
		packet.length = packet.data.length;
		return packet;
	}
	
	private TcpSegment segmentFrom(Packet packet) {
		return TcpSegment.fromByteArray(packet.data, packet.length);
	}
	
	private void sendSegment(TcpSegment segment) throws IOException {
		segment.checksum = checksumFor(segment);
		ip.ip_send(packetFrom(segment));
	}
	
	private void receiveSegment(TcpSegment segment) throws IOException {
		do {
			ip.ip_receive(packet);
			remoteAddress = packet.source;
			segment = segmentFrom(packet);
		} while (!isValid(segment));
	}

	private IP ip;

	private ConnectionState state;

	private int localAddress;

	private int remoteAddress;

	private int localPort;

	private int remotePort;
	
	private Packet packet;
	
	private TcpSegment segment;
}