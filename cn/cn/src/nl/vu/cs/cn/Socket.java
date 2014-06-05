package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import static nl.vu.cs.cn.util.Preconditions.checkNotNull;
import static nl.vu.cs.cn.util.Preconditions.checkState;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.IP.Packet;
import nl.vu.cs.cn.util.Bits;

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
		if (state != ConnectionState.CLOSED) {
			return false;
		}

		localSequenceNumber = TCP.getInitSequenceNumber();
		remoteAddress = Bits.reverseOrder(dst.getAddress());
		remotePort = (short) port;
		if (!sendSynSegment(segment)) {
			return false;
		}

		if (!receiveSynAckSegment(segment)) {
			return false;
		}

		remoteSequenceNumber = segment.getSeq();
		if (!sendAckSegment(segment)) {
			return false;
		}

		state = ConnectionState.ESTABLISHED;
		return true;
	}

	// take into account that this method don't have timeout after receiving
	// first syn
	// so it'll block on half-open connection
	/**
	 * Accept a connection on this socket. This call blocks until a connection
	 * is made.
	 */
	public void accept() {
		checkState(state == ConnectionState.CLOSED);

		for (;;) {
			receiveSynSegment(segment);

			localSequenceNumber = TCP.getInitSequenceNumber();
			remotePort = segment.getFromPort();
			remoteSequenceNumber = segment.getSeq();
			if (!sendSynAckSegment(segment)) {
				continue;
			}

			++localSequenceNumber;
			if (!receiveAckSegment(segment)) {
				continue;
			}
			break;
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
		checkState(state == ConnectionState.ESTABLISHED || state == ConnectionState.READ_ONLY);

		int currentOffset = offset;
		while (currentOffset - offset < maxlen) { 
			if (receiveDataSegment(segment, buf, currentOffset)) {
				currentOffset += segment.dataLength + segment.getSeq() - 1 - remoteSequenceNumber;  
				
				if (!sendAckSegment(segment)) {
					return -1;
				}
			} else if (state == ConnectionState.WRITE_ONLY) {
				// the opposite site just closed the connection
				break;
			} else {
				return -1;
			}
		}
		return currentOffset - offset;
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
		checkState(state == ConnectionState.ESTABLISHED || state == ConnectionState.WRITE_ONLY);

		int dataLeft = len;
		int currOffset = offset;
		while (dataLeft > 0) {
			int dataLength = Math.min(dataLeft, TcpSegment.TCP_MAX_DATA_LENGTH);
			dataLeft -= dataLength;
			
			if (!sendDataSegment(segment, buf, currOffset, dataLength)) {
				return -1;
			}
			
			if (!receiveAckSegment(segment)) {
				return -1;
			}
			
			int acknowledged = segment.getAck() - localSequenceNumber;
			currOffset += acknowledged;
			dataLeft -= acknowledged;
			localSequenceNumber = segment.getAck();
		}
		return len;
	}

	/**
	 * Closes the connection for this socket. Blocks until the connection is
	 * closed.
	 * 
	 * @return true unless no connection was open.
	 */
	public boolean close() {
		if (state == ConnectionState.CLOSED) {
			return false;
		}

		segment.setFlags((short) (TcpSegment.FIN_FLAG | TcpSegment.PUSH_FLAG));
		sendSegment(segment);

		receiveSegment(segment);
		if (segment.getFromPort() == remotePort && segment.hasAckFlag()
				&& segment.getAck() == localSequenceNumber + 1) {
			return false;
		}

		return true;
	}

	private void onFinReceived() {

	}

	// package to simplify testing
	/* package */short checksumFor(TcpSegment segment) {
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
		if (segment.length < TcpSegment.TCP_HEADER_LENGTH) {
			return false;
		}
		
		if (checksumFor(segment) != 0) {
			return false;
		}
		
		return true;
	}

	private Packet packetFrom(TcpSegment segment) {
		packet.source = Bits.reverseOrder(localAddress);
		packet.destination = Bits.reverseOrder(remoteAddress);
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

	private void fillBasicSegmentData(TcpSegment segment) {
		segment.setFromPort(localPort);
		segment.setToPort(remotePort);
		segment.setSeq(localSequenceNumber);
		segment.setWindowSize((short) 1);
	}

	private boolean sendSynSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setFlags((short) (SYN_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}

	private boolean sendAckSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setAck(remoteSequenceNumber + 1);
		segment.setFlags((short) (ACK_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}

	private boolean sendSynAckSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setAck(remoteSequenceNumber + 1);
		segment.setFlags((short) (SYN_FLAG | ACK_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}
	
	private boolean sendDataSegment(TcpSegment segment, byte[] src, int offset, int len) {
		fillBasicSegmentData(segment);
		segment.setData(src, offset, len);
		return sendSegment(segment);
	}

	// package to simplify testing
	/* package */boolean sendSegment(TcpSegment segment) {
		try {
			segment.setChecksum(checksumFor(segment));
			ip.ip_send(packetFrom(segment));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// @formatter:off
	private boolean receiveSynSegment(TcpSegment segment) {
		do {
			receiveSegment(segment);
		} while (!segment.hasSynFlag() 
				|| segment.hasAckFlag()
				|| segment.hasFinFlag());
		return true;
	}
	
	private boolean receiveAckSegment(TcpSegment segment) {
		do {
			receiveSegment(segment);
		} while (segment.getFromPort() != remotePort 
				|| segment.hasSynFlag()
				|| !segment.hasAckFlag() 
				|| segment.hasFinFlag()
				|| segment.getAck() < localSequenceNumber + 1);
		return true;
	}
	
	private boolean receiveSynAckSegment(TcpSegment segment) {
		do {
			receiveSegment(segment);
		} while (segment.getFromPort() != remotePort
				|| !segment.hasAckFlag()
				|| !segment.hasSynFlag()
				|| segment.hasFinFlag()
				|| segment.getAck() != localSequenceNumber + 1);
		return true;
	}
	
	private boolean receiveDataSegment(TcpSegment segment, byte[] dst, int offset) {
		do { 
			receiveSegment(segment);
		} while (segment.getFromPort() != remotePort
		 		|| segment.hasAckFlag()
		 		|| segment.hasSynFlag()
		 		|| segment.hasFinFlag()
		 		|| segment.getSeq() + segment.dataLength - 1 - remoteSequenceNumber  >= 0);
		return true;
	}
	// @formatter:on

	// package to simplify testing
	/* package */boolean receiveSegment(TcpSegment segment) {
		try {
			do {
				ip.ip_receive(packet);
				segment = segmentFrom(packet);
			} while (!isValid(segment));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private IP ip;

	private ConnectionState state;

	private int localAddress; // in big-endian

	private int remoteAddress; // in big-endian

	private short localPort;

	private short remotePort;

	private int localSequenceNumber;

	private int remoteSequenceNumber;

	private Packet packet = new Packet();

	private TcpSegment segment;
}