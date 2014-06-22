package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;
import static nl.vu.cs.cn.TcpSegment.TCP_HEADER_LENGTH;
import static nl.vu.cs.cn.TcpSegment.TCP_MAX_DATA_LENGTH;
import static nl.vu.cs.cn.util.Preconditions.checkArgument;
import static nl.vu.cs.cn.util.Preconditions.checkNotNull;
import static nl.vu.cs.cn.util.Preconditions.checkState;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.IP.Packet;
import android.util.Log;

/**
 * This class represents a TCP stack. It should be built on top of the IP stack
 * which is bound to a given IP address.
 */
public class TCP {
	
	private static final String TAG = TCP.class.getSimpleName();
	
	/** The underlying IP stack for this TCP stack. */
	protected IP ip;
	
	private static final short DEFAULT_PORT = 1453;
	private static short CURRENT_PORT = DEFAULT_PORT;
	
	/* package */ static final int RECV_WAIT_TIMEOUT_SECONDS = 1;
	/* package */ static final int MAX_RESEND_TRIALS = 10;
	
	private static final boolean SEND_RECEIVE_LOGGING_ENABLED = true; 
	
	/**
	 * This class represents a TCP socket.
	 * 
	 */
	public static final class Socket {
		
		private final String TAG;

		/**
		 * Construct a socket bound to the given local port.
		 * 
		 * @param port
		 *            the local port to use
		 */
		private Socket(IP ip, short port) {
			this.ip = ip;
			int localAddressLittleEndian = ip.getLocalAddress().getAddress();
			localAddress = Integer.reverseBytes(localAddressLittleEndian);
			sentPacket.data = new byte[TCP_HEADER_LENGTH + TCP_MAX_DATA_LENGTH];
			receivedPacket.data = new byte[TCP_HEADER_LENGTH + TCP_MAX_DATA_LENGTH];
			localPort = (short) port;
			TAG = "Socket (" + ip.getLocalAddress() + ":" + localPort + ")"; 
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
			Log.i(TAG, "Connecting to address: " + dst + "; port: " + port);
			if (state != ConnectionState.CLOSED) {
				return false;
			}

			localSequenceNumber = TCP.getInitSequenceNumber();
			remoteAddress = Integer.reverseBytes(dst.getAddress());
			remotePort = (short) port;
			if (!deliverSynSegment()) {
				return false;
			}

			Log.i(TAG, "SYN sent and acknowledged");
			++localSequenceNumber;
			if (!sendAckSegment(sentSegment, receivedSegment.getSeq() + 1)) {
				return false;
			}

			state = ConnectionState.ESTABLISHED;
			Log.i(TAG, "Connected");
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
			Log.i(TAG, "Listening on port " + localPort);
			
			for (;;) {
				receiveSynSegment(receivedSegment);

				localSequenceNumber = TCP.getInitSequenceNumber();
				remotePort = receivedSegment.getFromPort();
				remoteSequenceNumber = receivedSegment.getSeq() + 1;
				
				String addr = IpAddress.htoa(Integer.reverseBytes(remoteAddress));
				Log.i(TAG, "Received SYN segment from address: " + addr + "; port: " + remotePort);

				if (deliverSynAckSegment()) {
					++localSequenceNumber;
					break;
				}
				Log.i(TAG, "SYN ACK sent and acknowledged");
			}

			state = ConnectionState.ESTABLISHED;
			int addrHostOrder = Integer.reverseBytes(remoteAddress);
			Log.i(TAG, "Connection established. Remote address: " + IpAddress.htoa(addrHostOrder) + "; remote port: " + remotePort);
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

			Log.i(TAG, "Reading " + maxlen + " bytes...");
			int currentOffset = offset;
			int trials = TCP.MAX_RESEND_TRIALS;
			while (currentOffset - offset < maxlen) {
				int maxChunkSize = maxlen - (currentOffset - offset);
				Log.i(TAG, "maxlen: " + maxlen + "; currOff: " + currentOffset + "; off: " + offset);
				if (receiveDataSegment(receivedSegment, buf, currentOffset, maxChunkSize)) {
					int recvLen = Math.min(receivedSegment.dataLength, maxChunkSize);
					int newOffset = currentOffset + recvLen
							+ receivedSegment.getSeq() - remoteSequenceNumber;
					Log.i(TAG, "" + (newOffset - currentOffset) + " new bytes received.");
					currentOffset = newOffset;
					
					int toAcknowledge = receivedSegment.getSeq()
							+ recvLen;
					if (!sendAckSegment(sentSegment, toAcknowledge)) {
						return currentOffset - offset;
					}
					trials = TCP.MAX_RESEND_TRIALS;
				} else if (state == ConnectionState.WRITE_ONLY
						|| state == ConnectionState.CLOSED) {
					// the opposite site just closed the connection
					break;
				} else if (--trials > 0) {
					continue;
				} else {
					return currentOffset - offset;
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

			Log.i(TAG, "Writing message: \"" + new String(buf, offset, len) + "\"");
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

				Log.i(TAG, "" + dataLength + " sent; " + acknowledged + " acknowledged; " + dataLeft + " left");
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

			Log.i(TAG, "Closing the connection...");
			deliverFinSegment();
			++localSequenceNumber;

			if (state == ConnectionState.ESTABLISHED) {
				state = ConnectionState.READ_ONLY;
			} else if (state == ConnectionState.WRITE_ONLY) {
				state = ConnectionState.CLOSED;
				remoteAddress = 0;
				remotePort = 0;
				remoteEstablished = false;
			}
			Log.i(TAG, "Current connection state: " + state);
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
				Log.i(TAG, "Received segment is too short: " + segment.length);
				result = false;
			}

			short checksum = checksumFor(segment);
			if (checksum != 0) {
				Log.i(TAG, "Received segment has invalid checksum: " + checksum);
				Log.i(TAG, "" + segment);
				result = false;
			}

			return result;
		}

		/* package */Packet packetFrom(Packet packet, TcpSegment segment) {
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
			segment.setAck(0);
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
			sentSegment.setAck(remoteSequenceNumber);
			sentSegment.setFlags((byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG));
			if (deliverSegment(sentSegment) != -1) {
				remoteEstablished = true;
				return true;
			} else {
				return false;
			}
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
			int ackOffset = segment.dataLength + 1;
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

		private boolean sendAckSegment(TcpSegment segment, int acknowledged) {
			fillBasicSegmentData(segment);
			segment.setAck(acknowledged);
			segment.setFlags((byte) (ACK_FLAG | PUSH_FLAG));
			if (sendSegment(segment)) {
				oldRemoteSequenceNumber = Math.min(acknowledged,
						remoteSequenceNumber); // was it resent or new segment?
				remoteSequenceNumber = acknowledged;
				return true;
			}
			return false;
		}

		// package to simplify testing
		/* package */boolean sendSegment(TcpSegment segment) {
			try {
				// sometimes we are resending, no need to calculate it again
				if (segment.getChecksum() == 0) { 
					segment.setChecksum(checksumFor(segment));
				}
				if (SEND_RECEIVE_LOGGING_ENABLED) {
					Log.i(TAG, "Sending segment " + segment);
				}
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
			
			for (;;) { // receiving valid segment should not cause failure even if it is not ack
				if (!receiveSegmentWithTimeout(segment, TCP.RECV_WAIT_TIMEOUT_SECONDS)) {
					return false;
				} else if (isValidFin(segment)) {
					onFinReceived(segment.getSeq());
					continue;
				} else if (!remoteEstablished && isValidDelayedSynAck(segment)) {
					onDelayedSynAckReceived(segment.getSeq());
					continue;
				}
				break;
			}
			if (segment.hasFlags(allOf, noneOf)
				&& segment.getAck() >= ackLowerBound
				&& segment.getAck() <= ackLowerBound + ackOffset) {
				if (!actuallySynAck) {
					remoteEstablished = true;
				}
				return true;
			} else {
				return false;
			}
		}
		
		private boolean receiveDataSegment(TcpSegment segment, byte[] dst, int offset, int maxlen) {
			for (;;) { // receiving valid segment should not cause failure even if it is not data
				if (!receiveSegmentWithTimeout(segment, TCP.RECV_WAIT_TIMEOUT_SECONDS)) {
					return false;
				} else if (isValidFin(segment)) {
					onFinReceived(segment.getSeq());
					continue;
				} else if (!remoteEstablished && isValidDelayedSynAck(segment)) {
					onDelayedSynAckReceived(segment.getSeq());
					continue;
				}
				break;
			}
			if ((segment.getSeq() == oldRemoteSequenceNumber || segment.getSeq() == remoteSequenceNumber)
				&& segment.hasFlags(0, SYN_FLAG | ACK_FLAG | FIN_FLAG)
		 		&& segment.getSeq() + segment.dataLength - 1 - remoteSequenceNumber >= 0) {
				segment.getData(dst, offset, maxlen);
				remoteEstablished = true;
				return true;
			} else {
				return false;
			}
		}
		
		private boolean isValidDelayedSynAck(TcpSegment segment) {
			return segment.hasFlags(SYN_FLAG | ACK_FLAG, FIN_FLAG)
					&& segment.getAck() == localSequenceNumber;
		}

		private boolean isValidFin(TcpSegment segment) {
			return (segment.getSeq() == oldRemoteSequenceNumber || segment.getSeq() == remoteSequenceNumber)
					&& segment.hasFlags(FIN_FLAG, SYN_FLAG | ACK_FLAG);
		}
		
		private void onDelayedSynAckReceived(int synAckSeq) {
			Log.i(TAG, "Received delayed SYN ACK segment. Acknowledging...");
			sendAckSegment(receivedSegment, synAckSeq + 1);
		}
		
		private void onFinReceived(int remoteSeqNumber) {
			Log.i(TAG, "Received FIN segment. Acknowledging...");
			sendAckSegment(receivedSegment, remoteSeqNumber);
			
			if (state == ConnectionState.ESTABLISHED) {
				state = ConnectionState.WRITE_ONLY;
			} else if (state == ConnectionState.READ_ONLY) {
				state = ConnectionState.CLOSED;
				remoteAddress = 0;
				remotePort = 0;
				remoteEstablished = false;
			}
			Log.i(TAG, "Connection state is now " + state);
		}
		// @formatter:on

		// package to simplify testing
		/* package */boolean receiveSegment(TcpSegment segment) {
			return receiveSegmentWithTimeout(segment, 0);
		}

		/* package */boolean receiveSegmentWithTimeout(TcpSegment segment,
				int timeoutSeconds) {
			try {
				for (;;) {
					ip.ip_receive_timeout(receivedPacket, timeoutSeconds);
					if (receivedPacket.protocol != IP.TCP_PROTOCOL) {
						Log.i(TAG, "Received packet with invalid protocol number: " + receivedPacket.protocol);
						continue;
					}
					int remoteAddressHost = Integer.reverseBytes(remoteAddress);
					if (remoteAddress != 0 && remoteAddressHost != receivedPacket.source) {
						Log.i(TAG, "Received packet from invalid host: " + receivedPacket.source);
						continue;
					}
					segment = segmentFrom(receivedPacket, segment);
					if (remotePort != 0 && segment.getFromPort() != remotePort) {
						Log.i(TAG, "Received packet from invalid port " + segment.getFromPort());
						continue;
					}
					if (remoteAddress == 0) {
						remoteAddress = Integer.reverseBytes(receivedPacket.source);
					}
					if (!isValid(segment)) {
						return false;
					} else {
						if (SEND_RECEIVE_LOGGING_ENABLED) {
							Log.i(TAG, "Received segment " + segment);
						}
						return true;
					}
				}
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

		/* package */int oldRemoteSequenceNumber;

		/* package */Packet sentPacket = new Packet();

		/* package */TcpSegment sentSegment = new TcpSegment();

		/* package */Packet receivedPacket = new Packet();

		/* package */TcpSegment receivedSegment = new TcpSegment();

		/* package */boolean remoteEstablished = false;
	}
	
	/**
	 * Constructs a TCP stack for the given virtual address. The virtual address
	 * for this TCP stack is then 192.168.1.address.
	 * 
	 * @param address
	 *            The last octet of the virtual IP address 1-254.
	 * @throws IOException
	 *             if the IP stack fails to initialize.
	 */
	public TCP(int address) throws IOException {
		ip = new IP(address);
	}
	
	/*
	 * for testing purposes
	 */
	protected TCP() { }

	/**
	 * @return a new socket for this stack
	 */
	public Socket socket() {
		return new Socket(ip, CURRENT_PORT++);
	}

	/**
	 * @return a new server socket for this stack bound to the given port
	 * @param port
	 *            the port to bind the socket to.
	 */
	public Socket socket(int port) {
		checkArgument(0 < port && port <= 65545);
		return new Socket(ip, (short) port);
	}
	
	/* package */ static int getInitSequenceNumber() {
		return 20051498;
	}
	
}
