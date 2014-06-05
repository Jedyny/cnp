package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import junit.framework.TestCase;

public class TCPTest extends TestCase {

	public static int SENDER_ADDR = 123;
	
	public static int RECEIVER_ADDR = 125;
	
	public static short SENDER_PORT = 1234;
	
	public static short RECEIVER_PORT = 4321;
	
	private Socket sender;
	
	private Socket receiver;

	@Override
	public void setUp() throws IOException {
		sender = new TCP(SENDER_ADDR).socket(SENDER_PORT);
		receiver = new TCP(RECEIVER_ADDR).socket(RECEIVER_PORT);
		
		int senderIpLittleEndian = IpAddress.getAddress("192.168.0." + SENDER_ADDR).getAddress();
		int receiverIpLittleEndian = IpAddress.getAddress("192.168.0." + RECEIVER_ADDR).getAddress();
		
		sender.remoteAddress = Integer.reverseBytes(receiverIpLittleEndian);
		receiver.remoteAddress = Integer.reverseBytes(senderIpLittleEndian);
		
		sender.localSequenceNumber = TCP.getInitSequenceNumber();
		receiver.localSequenceNumber = TCP.getInitSequenceNumber();
		
		sender.remotePort = RECEIVER_PORT;
		receiver.remotePort = SENDER_PORT;
	}
	
	public void testOneSendWithoutData() {
		TcpSegment segment = newSegment(sender, 12345, (byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG));
		sender.sendSegment(segment);
		receiver.receiveSegment(receiver.segment);
		
		assertEquals(receiver.segment.getFromPort(), SENDER_PORT);
		assertEquals(receiver.segment.getToPort(), RECEIVER_PORT);
		assertEquals(receiver.segment.getSeq(), sender.localSequenceNumber);
		assertEquals(receiver.segment.getAck(), 12345);
		assertTrue(receiver.segment.hasAckFlag());
		assertTrue(receiver.segment.hasSynFlag());
		assertTrue(receiver.segment.hasPushFlag());
		assertFalse(receiver.segment.hasFinFlag());
		assertEquals(receiver.segment.length, TcpSegment.TCP_HEADER_LENGTH);
		assertEquals(receiver.segment.dataLength, 0);
	}

	private TcpSegment newSegment(Socket socket, int ack,
			byte flags) {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort(socket.localPort);
		segment.setToPort(socket.remotePort);
		segment.setSeq(socket.localSequenceNumber);
		segment.setAck(ack);
		segment.setFlags(flags);
		segment.setWindowSize((short) 1);

		return segment;
	}
}
