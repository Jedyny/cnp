package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.io.IOException;

import junit.framework.TestCase;
import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.IP.Packet;
import nl.vu.cs.cn.TCP.Socket;

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
		sendAndValidateMessage(sender, receiver, segment, "");
	}
	
	public void testOneSendWithData() {
		String hamlet = "To be or not to be";
		
		TcpSegment segment = newSegment(sender, 12345, (byte) (PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, hamlet);
	}
	
	public void testFewSendsWithData() {
		String witch1 = "When shall we three meet again? " +
				"In thunder, lightning or in rain?";
		String witch2 = "When the hurlyburly's done. " +
				"When the battle's lost and won.";
		String witch3 = "That will be ere the set of sun.";
		
		TcpSegment segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, witch1);
		
		segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, witch2);
		
		segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, witch3);
	}
	
	public void testMessagesExchange() {
		String romeo1 = "My lips, two blushing pilgrims, ready stand" +
				"To smooth that rough touch with a tender kiss.";
		String juliet1 = "Saints have hands that pilgrims' hands do touch," +
				"And palm to palm is holy palmers' kiss.";
		String romeo2 = "Have not saints lips, and holy palmers too?";
		String juliet2 = "Ay, pilgrim, lips that they must use in prayer.";
		String romeo3 = "O, then, dear saint, let lips do what hands do;" +
				"Thus from my lips, by yours, my sin is purged.";
		
		TcpSegment segment = newSegment(sender, 0, (byte) (PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, romeo1);
		
		segment = newSegment(receiver, 100, (byte) (ACK_FLAG | PUSH_FLAG));
		sendAndValidateMessage(receiver, sender, segment, juliet1);
		
		segment = newSegment(sender, 200, (byte) (ACK_FLAG | PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, romeo2);
		
		segment = newSegment(receiver, 101, (byte) (ACK_FLAG | PUSH_FLAG));
		sendAndValidateMessage(receiver, sender, segment, juliet2);
		
		segment = newSegment(sender, 201, (byte) (ACK_FLAG | PUSH_FLAG));
		sendAndValidateMessage(sender, receiver, segment, romeo3);
	}
	
	
	private void sendAndValidateMessage(Socket sender, Socket receiver, TcpSegment segment, String message) {
		byte[] data = message.getBytes();
		segment.setData(data, 0, data.length);
		sender.sendSegment(segment);
		
		receiver.receiveSegment(receiver.receivedSegment);
		TcpSegment receivedSegment = receiver.receivedSegment;
		
		assertEquals(receivedSegment.getFromPort(), sender.localPort);
		assertEquals(receivedSegment.getToPort(), receiver.localPort);
		assertEquals(receivedSegment.getSeq(), sender.localSequenceNumber);
		assertEquals(receivedSegment.getAck(), segment.getAck());
		assertEquals(receivedSegment.hasAckFlag(), segment.hasAckFlag());
		assertEquals(receivedSegment.hasSynFlag(), segment.hasSynFlag());
		assertEquals(receivedSegment.hasPushFlag(), segment.hasPushFlag());
		assertEquals(receivedSegment.hasFinFlag(), segment.hasFinFlag());
		assertEquals(receivedSegment.length, TcpSegment.TCP_HEADER_LENGTH + data.length);
		assertEquals(receivedSegment.dataLength, data.length);
		
		byte[] receivedData = new byte[data.length];
		receivedSegment.getData(receivedData, 0, receivedSegment.dataLength);
		assertEquals(new String(receivedData), message);
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
	
	public void testInvalidChecksumSegment() throws IOException{
		
		TcpSegment segment = newSegment(sender,0,(byte)(PUSH_FLAG));
		segment.setChecksum((short)21234);
		
		sender.ip.ip_send(sender.packetFrom(sender.sentPacket, segment));
		boolean check = receiver.receiveSegmentWithTimeout(receiver.receivedSegment, TCP.RECV_WAIT_TIMEOUT_SECONDS);
		
		assertEquals(check, false);
	}
	
	public void testInvalidProtocolPacket() throws IOException{
		
		Packet sentPacket = new Packet();
		TcpSegment segment = newSegment(sender,0,(byte)(PUSH_FLAG));
		
		sentPacket.protocol = IP.UDP_PROTOCOL;
		sentPacket.destination = Integer.reverseBytes(sender.remoteAddress);
		sentPacket.source = Integer.reverseBytes(receiver.remoteAddress);
		sentPacket.id = 1;
		sentPacket.data = segment.toByteArray();
		sentPacket.length = segment.length;
		segment.setChecksum(sender.checksumFor(segment));
		sender.ip.ip_send(sentPacket);
		boolean check = receiver.receiveSegmentWithTimeout(receiver.receivedSegment, TCP.RECV_WAIT_TIMEOUT_SECONDS);
		
		assertEquals(check, false);
	}
	
	public void testEmptyPacket() throws IOException{
		Packet sentPacket = new Packet();
		byte [] byteArray = new byte[5];
		byteArray = "whatever".getBytes();
		
		sentPacket.protocol = IP.TCP_PROTOCOL;
		sentPacket.destination = Integer.reverseBytes(sender.remoteAddress);
		sentPacket.source = Integer.reverseBytes(receiver.remoteAddress);
		sentPacket.id = 1;
		sentPacket.data = byteArray;
		sentPacket.length = 5;
		
		sender.ip.ip_send(sentPacket);
		boolean check = receiver.receiveSegmentWithTimeout(receiver.receivedSegment, TCP.RECV_WAIT_TIMEOUT_SECONDS);
		
		assertEquals(check, false);
	}
	
	
	
	

}




