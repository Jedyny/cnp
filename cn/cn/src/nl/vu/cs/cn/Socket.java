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

import static nl.vu.cs.cn.util.Preconditions.checkNotNull;
import static nl.vu.cs.cn.util.Preconditions.checkState;

/**
 * This class represents a TCP socket.
 * 
 */
public final class Socket {
	
	/**
	 * Construct a socket bound to the given local port.
	 * 
	 * @param port
	 *            the local port to use
	 */
	/* package */ Socket(IP ip, short port) {
		this.ip = ip;
		int localAddressLittleEndian = ip.getLocalAddress().getAddress();
		localAddress = Integer.reverseBytes(localAddressLittleEndian); 
		packet.data = new byte[TcpSegment.TCP_HEADER_LENGTH + TcpSegment.TCP_MAX_DATA_LENGTH];
		localPort = (short) port;
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
		remoteAddress = Integer.reverseBytes(dst.getAddress());
		remotePort = (short) port;
		if (!sendSynSegment(segment)) {
			return false;
		}

		if (!receiveSynAckSegment(segment)) {
			return false;
		}

		remoteSequenceNumber = segment.getSeq();
		++localSequenceNumber;
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

			if (!receiveAckSegment(segment)) {
				continue;
			}
			++localSequenceNumber;
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
				
				remoteSequenceNumber += segment.dataLength;
				if (!sendAckSegment(segment)) {
					return currentOffset - offset;
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

		if (!sendFinSegment(segment)) {
			return false;
		}

		// we closed the connection and we do not care if we receive it or not
		receiveAckSegment(segment);
		return true;
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
		while (sum > 65535) {
			sum = (sum >>> 16) + (sum & 0xffff);
		}

		// one's complement
		sum = ~sum & 0xffff;
		return (short) sum;
	}

	private boolean isValid(TcpSegment segment) {
		boolean result = true;
		if (segment.length < TcpSegment.TCP_HEADER_LENGTH) {
			result = false;
		}
		
		short checksum = checksumFor(segment);
		if (checksum != 0) {
			result = false;
		}
		
		return result;
	}

	private Packet packetFrom(TcpSegment segment) {
		packet.source = Integer.reverseBytes(localAddress);
		packet.destination = Integer.reverseBytes(remoteAddress);
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
		segment.setChecksum((short) 0); // clear it
		segment.setWindowSize((short) 1);
		segment.length = TcpSegment.TCP_HEADER_LENGTH; // clear data
		segment.dataLength = 0;
	}

	private boolean sendSynSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setFlags((byte) (SYN_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}

	private boolean sendAckSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setAck(remoteSequenceNumber + 1);
		segment.setFlags((byte) (ACK_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}

	private boolean sendSynAckSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setAck(remoteSequenceNumber + 1);
		segment.setFlags((byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}
	
	private boolean sendDataSegment(TcpSegment segment, byte[] src, int offset, int len) {
		fillBasicSegmentData(segment);
		segment.setFlags((byte) (PUSH_FLAG));
		segment.setData(src, offset, len);
		segment.setFlags((byte) PUSH_FLAG);
		return sendSegment(segment);
	}
	
	private boolean sendFinSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setFlags((byte) (FIN_FLAG | PUSH_FLAG));
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
			if (isValidFin(segment)) {
				onFinReceived();
				return false;
			}
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
			
			if (isValidFin(segment)) {
				onFinReceived();
				return false;
			}
			segment.getData(dst, offset);
		} while (segment.getFromPort() != remotePort
		 		|| segment.hasAckFlag()
		 		|| segment.hasSynFlag()
		 		|| segment.hasFinFlag()
		 		|| segment.getSeq() + segment.dataLength - 1 - remoteSequenceNumber  < 0);
		return true;
	}
	
	private boolean isValidFin(TcpSegment segment) {
		return segment.getFromPort() == remotePort
				&& !segment.hasAckFlag()
				&& !segment.hasSynFlag()
				&& segment.hasFinFlag();
	}
	
	private void onFinReceived() {
		sendAckSegment(segment);
		if (state == ConnectionState.ESTABLISHED) {
			state = ConnectionState.WRITE_ONLY;
		} else if (state == ConnectionState.READ_ONLY) {
			state = ConnectionState.CLOSED;
		}
	}
	// @formatter:on

	// package to simplify testing
	/* package */boolean receiveSegment(TcpSegment segment) {
		try {
			do {
				ip.ip_receive(packet);
				segment = segmentFrom(packet);
				int lala=5;
				if (remoteAddress == 0) {
					remoteAddress = Integer.reverseBytes(packet.source);
				}
			} while (!isValid(segment));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/* package */ IP ip;

	/* package */ ConnectionState state = ConnectionState.CLOSED;

	/* package */ int localAddress; // in big-endian
	
	/* package */ int remoteAddress; // in big-endian

	/* package */ short localPort;

	/* package */ short remotePort;

	/* package */ int localSequenceNumber;

	/* package */ int remoteSequenceNumber;
	
	/* package */ Packet packet = new Packet();
	
	/* package */ TcpSegment segment = new TcpSegment();
}