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
	/* package */Socket(IP ip, short port) {
		this.ip = ip;
		int localAddressLittleEndian = ip.getLocalAddress().getAddress();
		localAddress = Integer.reverseBytes(localAddressLittleEndian);
		sentPacket.data = new byte[TcpSegment.TCP_HEADER_LENGTH
				+ TcpSegment.TCP_MAX_DATA_LENGTH];
		receivedPacket.data = new byte[TcpSegment.TCP_HEADER_LENGTH
				+ TcpSegment.TCP_MAX_DATA_LENGTH];
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
		deliverSynSegment();

		remoteSequenceNumber = receivedSegment.getSeq();
		++localSequenceNumber;
		if (!sendAckSegment(sentSegment)) {
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
			receiveSynSegment(receivedSegment);

			localSequenceNumber = TCP.getInitSequenceNumber();
			remotePort = receivedSegment.getFromPort();
			remoteSequenceNumber = receivedSegment.getSeq();

			if (deliverSynAckSegment()) {
				++localSequenceNumber;
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
		checkState(state == ConnectionState.ESTABLISHED
				|| state == ConnectionState.READ_ONLY);

		int currentOffset = offset;
		while (currentOffset - offset < maxlen) {
			if (receiveDataSegment(receivedSegment, buf, currentOffset)) {
				currentOffset += receivedSegment.dataLength
						+ receivedSegment.getSeq() - 1 - remoteSequenceNumber;

				remoteSequenceNumber += receivedSegment.dataLength;
				if (!sendAckSegment(receivedSegment)) {
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
		checkState(state == ConnectionState.ESTABLISHED
				|| state == ConnectionState.WRITE_ONLY);

		int dataLeft = len;
		int currOffset = offset;
		while (dataLeft > 0) {
			int dataLength = Math.min(dataLeft, TcpSegment.TCP_MAX_DATA_LENGTH);

			int ack = deliverDataSegment(buf, currOffset, dataLength);
			if (ack == -1) {
				return len - dataLeft;
			}

			int acknowledged = ack - localSequenceNumber;
			currOffset += acknowledged;
			dataLeft -= acknowledged;
			localSequenceNumber = ack;
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

		deliverFinSegment();

		if (state == ConnectionState.ESTABLISHED) {
			state = ConnectionState.READ_ONLY;
		} else if (state == ConnectionState.WRITE_ONLY) {
			state = ConnectionState.CLOSED;
		}
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

	private Packet packetFrom(Packet packet, TcpSegment segment) {
		packet.source = Integer.reverseBytes(localAddress);
		packet.destination = Integer.reverseBytes(remoteAddress);
		packet.protocol = IP.TCP_PROTOCOL;
		packet.id = 1;
		packet.data = segment.toByteArray();
		packet.length = segment.length;
		return packet;
	}

	private TcpSegment segmentFrom(Packet packet, TcpSegment segment) {
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

	private boolean deliverSynSegment() {
		fillBasicSegmentData(sentSegment);
		sentSegment.setFlags((byte) (SYN_FLAG | PUSH_FLAG));
		return deliverSegment(sentSegment, true) != -1;
	}

	private boolean deliverSynAckSegment() {
		fillBasicSegmentData(sentSegment);
		sentSegment.setAck(remoteSequenceNumber + 1);
		sentSegment.setFlags((byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG));
		return deliverSegment(sentSegment) != -1;
	}

	private int deliverDataSegment(byte[] src, int offset, int len) {
		fillBasicSegmentData(sentSegment);
		sentSegment.setData(src, offset, len);
		sentSegment.setFlags((byte) PUSH_FLAG);
		return deliverSegment(sentSegment);
	}

	private boolean deliverFinSegment() {
		fillBasicSegmentData(sentSegment);
		sentSegment.setFlags((byte) (FIN_FLAG | PUSH_FLAG));
		return deliverSegment(sentSegment) != -1;
	}

	/* returns last acked byte or -1 in case of failure */
	private int deliverSegment(TcpSegment segment) {
		return deliverSegment(segment, false);
	}

	private int deliverSegment(TcpSegment segment, boolean maybeSynAck) {
		int ackOffset;
		if (segment.hasSynFlag() | segment.hasFinFlag()) {
			ackOffset = 1;
		} else {
			ackOffset = segment.dataLength;
		}
		int trialsLeft = TCP.MAX_RESEND_TRIALS;
		for (; trialsLeft > 0; --trialsLeft) {
			if (!sendSegment(segment)) {
				continue;
			}

			if (receiveAckSegment(receivedSegment, localSequenceNumber,
					ackOffset, maybeSynAck)) {
				break;
			}
		}

		return (trialsLeft > 0 ? receivedSegment.getAck() : -1);
	}

	private boolean sendAckSegment(TcpSegment segment) {
		fillBasicSegmentData(segment);
		segment.setAck(remoteSequenceNumber + 1);
		segment.setFlags((byte) (ACK_FLAG | PUSH_FLAG));
		return sendSegment(segment);
	}

	// package to simplify testing
	/* package */boolean sendSegment(TcpSegment segment) {
		try {
			segment.setChecksum(checksumFor(segment));
			ip.ip_send(packetFrom(sentPacket, segment));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// @formatter:off
	private boolean receiveSynSegment(TcpSegment segment) {
		do {
			receiveSegment(segment);
		} while (!segment.hasFlags(SYN_FLAG, ACK_FLAG | FIN_FLAG));
		return true;
	}
	
	private boolean receiveAckSegment(TcpSegment segment, int ackLowerBound, int ackOffset, boolean actuallySynAck) {
		int allOf, noneOf;
		allOf = (actuallySynAck ? ACK_FLAG | SYN_FLAG : ACK_FLAG);
		noneOf= (actuallySynAck ? FIN_FLAG : SYN_FLAG | FIN_FLAG);
			
		if (!receiveSegmentWithTimeout(segment, TCP.RECV_WAIT_TIMEOUT_SECONDS)) {
			return false;
		} else if (isValidFin(segment)) {
			onFinReceived();
			return false;
		} else {
			return segment.getFromPort() == remotePort 
				&& segment.hasFlags(allOf, noneOf)
				&& segment.getAck() >= ackLowerBound
				&& segment.getAck() <= ackLowerBound + ackOffset;
		}
	}
	
	private boolean receiveDataSegment(TcpSegment segment, byte[] dst, int offset) {
		if (!receiveSegmentWithTimeout(segment, TCP.RECV_WAIT_TIMEOUT_SECONDS)) {
			return false;
		} else if (isValidFin(segment)) {
			onFinReceived();
			return false;
		} else if (!remoteEstablished && isValidDelayedSynAck(segment)) {
			onDelayedSynAckReceived();
			return false;
		} else if (segment.getFromPort() == remotePort
			&& segment.hasFlags(0, SYN_FLAG | ACK_FLAG | FIN_FLAG)
	 		&& segment.getSeq() + segment.dataLength - 1 - remoteSequenceNumber >= 0) {
			segment.getData(dst, offset);
			remoteEstablished = true;
			return true;
		} else {
			return false;
		}
	}
	
	private boolean isValidDelayedSynAck(TcpSegment segment) {
		return segment.getFromPort() == remotePort
				&& segment.hasFlags(SYN_FLAG | ACK_FLAG, FIN_FLAG)
				&& segment.getAck() == localSequenceNumber;
	}

	private boolean isValidFin(TcpSegment segment) {
		return segment.getFromPort() == remotePort
				&& segment.hasFlags(FIN_FLAG, SYN_FLAG | ACK_FLAG);
	}
	
	private void onDelayedSynAckReceived() {
		sendAckSegment(receivedSegment);
	}
	
	private void onFinReceived() {
		sendAckSegment(receivedSegment);
		if (state == ConnectionState.ESTABLISHED) {
			state = ConnectionState.WRITE_ONLY;
		} else if (state == ConnectionState.READ_ONLY) {
			state = ConnectionState.CLOSED;
		}
	}
	// @formatter:on

	// package to simplify testing
	/* package */boolean receiveSegment(TcpSegment segment) {
		return receiveSegmentWithTimeout(segment, 0);
	}

	/* package */boolean receiveSegmentWithTimeout(TcpSegment segment,
			int timeoutSeconds) {
		try {
			ip.ip_receive_timeout(receivedPacket, timeoutSeconds);
			segment = segmentFrom(receivedPacket, segment);
			if (remoteAddress == 0) {
				remoteAddress = Integer.reverseBytes(receivedPacket.source);
			}
			return isValid(segment);
		} catch (InterruptedException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	/* package */IP ip;

	/* package */ConnectionState state = ConnectionState.CLOSED;

	/* package */int localAddress; // in big-endian

	/* package */int remoteAddress; // in big-endian

	/* package */short localPort;

	/* package */short remotePort;

	/* package */int localSequenceNumber;

	/* package */int remoteSequenceNumber;

	/* package */Packet sentPacket = new Packet();

	/* package */TcpSegment sentSegment = new TcpSegment();

	/* package */Packet receivedPacket = new Packet();

	/* package */TcpSegment receivedSegment = new TcpSegment();

	/* package */boolean remoteEstablished;
}