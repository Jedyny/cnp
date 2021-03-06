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
import java.util.BitSet;
import java.util.Random;

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
	
	/* package */ static final int RECV_WAIT_TIMEOUT_SECONDS = 1;
	/* package */ static final int MAX_RESEND_TRIALS = 10;

	// when this variable is set true, every send and received packet will be logged
	private static final boolean SEND_RECEIVE_LOGGING_ENABLED = true; 
	
	/**
	 * This class represents a TCP socket.
	 * 
	 */
	public class Socket {
		
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
			packet.data = new byte[TCP_HEADER_LENGTH + TCP_MAX_DATA_LENGTH];
			packet.data = new byte[TCP_HEADER_LENGTH + TCP_MAX_DATA_LENGTH];
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
			if (state != ConnectionState.CLOSED && !closed) {
				return false;
			}

			localSequenceNumber = getInitSequenceNumber();
			remoteAddress = Integer.reverseBytes(dst.getAddress());
			remotePort = (short) port;
			if (!deliverSynSegment()) {
				return false;
			}

			Log.i(TAG, "SYN sent and acknowledged");
			++localSequenceNumber;
			if (!sendAckSegment(segment, segment.getSeq() + 1)) {
				return false;
			}

			state = ConnectionState.ESTABLISHED;
			Log.i(TAG, "Connected");
			return true;
		}

		/**
		 * Accept a connection on this socket. This call blocks until a connection
		 * is made.
		 */
		public void accept() {
			checkState(state == ConnectionState.CLOSED && !closed);
			Log.i(TAG, "Listening on port " + localPort);
			
			for (;;) {
				receiveSynSegment(segment);

				localSequenceNumber = getInitSequenceNumber();
				remotePort = segment.getFromPort();
				remoteSequenceNumber = segment.getSeq() + 1;
				
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
				if (receiveDataSegment(segment, buf, currentOffset, maxChunkSize)) {
					int recvLen = Math.min(segment.dataLength, maxChunkSize);
					int newOffset = currentOffset + recvLen
							+ segment.getSeq() - remoteSequenceNumber;
					Log.i(TAG, "" + (newOffset - currentOffset) + " new bytes received.");
					currentOffset = newOffset;
					
					int toAcknowledge = segment.getSeq()
							+ recvLen;
					if (!sendAckSegment(segment, toAcknowledge)) {
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
				if (ack == -1) { // delivery failed
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
				realClose();
			}
			Log.i(TAG, "Current connection state: " + state);
			return true;
		}
		
		// cleans this socket state when the both sides of a connection are closed
		private void realClose() {
			state = ConnectionState.CLOSED;
			closed = true;
			remoteAddress = 0;
			remotePort = 0;
			remoteEstablished = false;
			freePort(localPort);
		}

		// calculates a checksum for the given segment
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

		// checks if this segment has valid length and checksum
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

		// wraps the given segment in the preallocated packet
		/* package */Packet packetFrom(Packet packet, TcpSegment segment) {
			packet.source = Integer.reverseBytes(localAddress);
			packet.destination = Integer.reverseBytes(remoteAddress);
			packet.protocol = IP.TCP_PROTOCOL;
			packet.id = 1;
			packet.data = segment.toByteArray();
			packet.length = segment.length;
			return packet;
		}

		// extracts the data into the preallocated segment
		private TcpSegment segmentFrom(Packet packet, TcpSegment segment) {
			segment.fromByteArray(packet.data, packet.length);
			return segment;
		}

		private void fillBasicSegmentData(TcpSegment segment) {
			segment.setFromPort(localPort);
			segment.setToPort(remotePort);
			segment.setSeq(localSequenceNumber);
			segment.setAck(remoteSequenceNumber);
			segment.setChecksum((short) 0); // clear it
			segment.setDataOffset();
			segment.setWindowSize((short) 1);
			segment.length = TcpSegment.TCP_HEADER_LENGTH; // clear data
			segment.dataLength = 0;
		}

		// send a SYN segment and wait for an acknowledgment
		// resend if necessary
		private boolean deliverSynSegment() {
			return deliverSegment((byte) (SYN_FLAG | PUSH_FLAG), true) != -1;
		}

		// send a SYN-ACK segment and wait for an acknowledgment
		// resend if necessary
		private boolean deliverSynAckSegment() {
			if (deliverSegment((byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG)) != -1) {
				remoteEstablished = true;
				return true;
			} else {
				return false;
			}
		}

		// wrap the given data into a segment, send it and wait for an
		// acknowledgment; resend if necessary
		private int deliverDataSegment(byte[] src, int offset, int len) {
			return deliverSegment((byte) (ACK_FLAG | PUSH_FLAG), src, offset, len);
		}

		// send a FIN segment and wait for an acknowledgment; 
		// resend if necessary
		private boolean deliverFinSegment() {
			return deliverSegment((byte) (FIN_FLAG | PUSH_FLAG)) != -1;
		}

		/* returns last acked byte or -1 in case of failure */
		private int deliverSegment(byte flags) {
			return deliverSegment(flags, null, 0, 0, false);
		}
		
		private int deliverSegment(byte flags, boolean maybeSynAck) {
			return deliverSegment(flags, null, 0, 0, maybeSynAck);
		}
		
		private int deliverSegment(byte flags, byte[] src, int offset, int len) {
			return deliverSegment(flags, src, offset, len, false);
		}

		// sends a segment and waits for the appropriate ack
		// resends if necessary 
		private int deliverSegment(byte flags, byte[] src, int offset, int len, boolean maybeSynAck) {
			int trialsLeft = TCP.MAX_RESEND_TRIALS;
			for (; trialsLeft > 0; --trialsLeft) {
				fillBasicSegmentData(segment);
				segment.setFlags(flags);
				if (src != null) {
					segment.setData(src, offset, len);
				}
				int ackOffset = segment.dataLength + 1;
				if (!sendSegment(segment)) {
					continue;
				}

				if (receiveAckSegment(segment, localSequenceNumber,
						ackOffset, maybeSynAck)) {
					break;
				}
			}

			return (trialsLeft > 0 ? segment.getAck() : -1);
		}

		// sends an ACK segment
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

		// calculates a checksum for this segment and sends it
		/* package */boolean sendSegment(TcpSegment segment) {
			try {
				segment.setChecksum(checksumFor(segment));
				packetFrom(packet, segment);
				if (SEND_RECEIVE_LOGGING_ENABLED) {
					Log.i(TAG, "Sending segment " + segment);
					Log.i(TAG, "As packet: " + packet);
				}
				ip.ip_send(packetFrom(packet, segment));
				return true;
			} catch (IOException e) {
				return false;
			}
		}

		// waits until a valid SYN segment arrives
		private boolean receiveSynSegment(TcpSegment segment) {
			do {
				receiveSegment(segment);
			} while (!segment.hasFlags(SYN_FLAG, ACK_FLAG | FIN_FLAG));
			return true;
		}
		
		// waits until a valid ACK segment arrives or time expires
		// handles FIN and resent SYN-ACK segments
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
		// waits until a valid data segment arrives or time expires
		// handles FIN and resent SYN-ACK segments
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
				&& segment.hasFlags(0, SYN_FLAG | FIN_FLAG)
		 		&& segment.getSeq() + segment.dataLength - 1 - remoteSequenceNumber >= 0) {
				segment.getData(dst, offset, maxlen);
				// when we got segment other than syn-ack we are sure that other party established a connection
				remoteEstablished = true; 
				return true;
			} else {
				return false;
			}
		}

		// checks if this segment is valid SYN-ACK
		private boolean isValidDelayedSynAck(TcpSegment segment) {
			return segment.hasFlags(SYN_FLAG | ACK_FLAG, FIN_FLAG)
					&& segment.getAck() == localSequenceNumber;
		}

		// checks if this segment is valid FIN
		private boolean isValidFin(TcpSegment segment) {
			return (segment.getSeq() == oldRemoteSequenceNumber || segment.getSeq() == remoteSequenceNumber)
					&& segment.hasFlags(FIN_FLAG, SYN_FLAG | ACK_FLAG);
		}
		
		// sometimes the ack we send after syn-ack is lost, and syn-ack is resend
		// we handle this situation by sending ack again
		private void onDelayedSynAckReceived(int synAckSeq) {
			Log.i(TAG, "Received delayed SYN ACK segment. Acknowledging...");
			sendAckSegment(segment, synAckSeq + 1);
		}
		
		// sometimes we still wait for data but fin arrives, we just acknowledge
		// it and stop reading
		private void onFinReceived(int remoteSeqNumber) {
			Log.i(TAG, "Received FIN segment. Acknowledging...");
			sendAckSegment(segment, remoteSeqNumber);
			
			if (state == ConnectionState.ESTABLISHED) {
				state = ConnectionState.WRITE_ONLY;
			} else if (state == ConnectionState.READ_ONLY) {
				realClose();
			}
			Log.i(TAG, "Connection state is now " + state);
		}

		// waits for a segment infinitely
		/* package */boolean receiveSegment(TcpSegment segment) {
			return receiveSegmentWithTimeout(segment, 0);
		}

		// waits for a segment timeoutSeconds
		// segments from wrong host or with invalid checksum are dropped
		/* package */boolean receiveSegmentWithTimeout(TcpSegment segment,
				int timeoutSeconds) {
			try {
				for (;;) {
					ip.ip_receive_timeout(packet, timeoutSeconds);
					if (SEND_RECEIVE_LOGGING_ENABLED) {
						Log.i(TAG, "Received packet: " + packet);
					}
					if (packet.protocol != IP.TCP_PROTOCOL) {
						Log.i(TAG, "Received packet with invalid protocol number: " + packet.protocol);
						continue;
					}
					int remoteAddressHost = Integer.reverseBytes(remoteAddress);
					if (remoteAddress != 0 && remoteAddressHost != packet.source) {
						Log.i(TAG, "Received packet from invalid host: " + packet.source);
						continue;
					}
					segment = segmentFrom(packet, segment);
					if (remotePort != 0 && segment.getFromPort() != remotePort) {
						Log.i(TAG, "Received packet from invalid port " + segment.getFromPort());
						continue;
					}
					if (remoteAddress == 0) {
						remoteAddress = Integer.reverseBytes(packet.source);
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
				Log.i(TAG, "Failed to receive packet - timeout expired.");
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

		// the sequence number we expect the next packet to have
		/* package */int remoteSequenceNumber;

		// this number is used to determine if the received seq is in valid range
		/* package */int oldRemoteSequenceNumber;

		/* package */Packet packet = new Packet();

		/* package */TcpSegment segment = new TcpSegment();

		// true when we are sure that our remote partner has state == established
		/* package */boolean remoteEstablished = false;
		
		// when closed, Socket cannot be used again
		/* package */boolean closed = false;
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
		this();
		ip = new IP(address);
	}
	
	/*
	 * for testing purposes; 
	 * we want to set our version of ip in subclasses of this class
	 */
	protected TCP() { 
		usedPorts.clear();
		usedPorts.set(0, 1024); // well-known ports
	}
	
	private static final int PORT_RANGE = 65535;
	
	private BitSet usedPorts = new BitSet(PORT_RANGE);
	
	/**
	 * @return a new socket for this stack
	 */
	public Socket socket() {
		int port = usedPorts.nextClearBit(0);
		usedPorts.set(port);
		return new Socket(ip, (short) port);
	}

	/**
	 * @return a new server socket for this stack bound to the given port
	 * @param port
	 *            the port to bind the socket to.
	 */
	public Socket socket(int port) {
		checkArgument(0 < port && port <= 65545 && !usedPorts.get(port));
		return new Socket(ip, (short) port);
	}
	
	// frees the given port
	private void freePort(int port) {
		usedPorts.clear(port);
	}
	
	private Random rnd = new Random();
	
	// generates a new random initial sequence number
	/* package */ int getInitSequenceNumber() {
		return rnd.nextInt();
	}
	
}
