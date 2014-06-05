package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import junit.framework.TestCase;

public class SendReceiveTest extends TestCase {

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
		
		segment = receiver.segment;
		assertEquals(segment.getFromPort(), SENDER_PORT);
		assertEquals(segment.getToPort(), RECEIVER_PORT);
		assertEquals(segment.getSeq(), sender.localSequenceNumber);
		assertEquals(segment.getAck(), 12345);
		assertTrue(segment.hasAckFlag());
		assertTrue(segment.hasSynFlag());
		assertTrue(segment.hasPushFlag());
		assertFalse(segment.hasFinFlag());
		assertEquals(segment.length, TcpSegment.TCP_HEADER_LENGTH);
		assertEquals(segment.dataLength, 0);
	}
	
	public void testOneSendWithData() {
		String hamlet = "To be or not to be";
		byte[] byteHamlet = hamlet.getBytes();
		byte[] byteOphelia = new byte[byteHamlet.length];
		
		TcpSegment segment = newSegment(sender, 12345, (byte) (PUSH_FLAG));
		segment.setData(byteHamlet, 0, byteHamlet.length);
		sender.sendSegment(segment);
		receiver.receiveSegment(receiver.segment);
		
		segment = receiver.segment;
		assertEquals(segment.getFromPort(), SENDER_PORT);
		assertEquals(segment.getToPort(), RECEIVER_PORT);
		assertEquals(segment.getSeq(), sender.localSequenceNumber);
		assertEquals(segment.getAck(), 12345);
		assertFalse(segment.hasAckFlag());
		assertFalse(segment.hasSynFlag());
		assertTrue(segment.hasPushFlag());
		assertFalse(segment.hasFinFlag());
		assertEquals(segment.length, TcpSegment.TCP_HEADER_LENGTH + byteHamlet.length);
		assertEquals(segment.dataLength, byteHamlet.length);
		
		segment.getData(byteOphelia, 0);
		assertEquals(new String(byteOphelia), hamlet);
	}
	
	public void testFewSendsWithData() {
		String witch1 = "When shall we three meet again? " +
				"In thunder, lightning or in rain?";
		String witch2 = "When the hurlyburly's done. " +
				"When the battle's lost and won.";
		String witch3 = "That will be ere the set of sun.";
		byte[] byteWitch1 = witch1.getBytes();
		byte[] byteWitch2 = witch2.getBytes();
		byte[] byteWitch3 = witch3.getBytes();
		
		byte[] receiverWitch1 = new byte[byteWitch1.length];
		byte[] receiverWitch2 = new byte[byteWitch2.length];
		byte[] receiverWitch3 = new byte[byteWitch3.length];
		
		TcpSegment segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		segment.setData(byteWitch1, 0, byteWitch1.length);
		sender.sendSegment(segment);
		receiver.receiveSegment(receiver.segment);
		segment = receiver.segment;
		assertEquals(segment.length, TcpSegment.TCP_HEADER_LENGTH + byteWitch1.length);
		assertEquals(segment.dataLength, byteWitch1.length);
		segment.getData(receiverWitch1, 0);
		assertEquals(new String(receiverWitch1), witch1);
		
		segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		segment.setData(byteWitch2, 0, byteWitch2.length);
		sender.sendSegment(segment);
		receiver.receiveSegment(receiver.segment);
		segment = receiver.segment;
		assertEquals(segment.length, TcpSegment.TCP_HEADER_LENGTH + byteWitch2.length);
		assertEquals(segment.dataLength, byteWitch2.length);
		segment.getData(receiverWitch2, 0);
		assertEquals(new String(receiverWitch2), witch2);
		
		segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		segment.setData(byteWitch3, 0, byteWitch3.length);
		sender.sendSegment(segment);
		receiver.receiveSegment(receiver.segment);
		segment = receiver.segment;
		assertEquals(segment.length, TcpSegment.TCP_HEADER_LENGTH + byteWitch3.length);
		assertEquals(segment.dataLength, byteWitch3.length);
		segment.getData(receiverWitch3, 0);
		assertEquals(new String(receiverWitch3), witch3);
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
