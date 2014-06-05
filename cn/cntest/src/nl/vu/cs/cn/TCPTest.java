package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.util.Bits;
import junit.framework.TestCase;

public class TCPTest extends TestCase {

	public static int SENDER_ADDR = 123;
	public static int RECEIVER_ADDR = 125;
	
	public void testSocketTest(){
		try {
			TCP tcpSenderAddress = new TCP(SENDER_ADDR);
			TCP tcpReceiverAddress = new TCP(RECEIVER_ADDR);
			Socket senderSocket = tcpSenderAddress.socket();
			Socket receiverSocket = tcpReceiverAddress.socket();
			int seq_num = tcpReceiverAddress.getInitSequenceNumber();
			
			int ipaddress = IpAddress.getAddress("192.168.0." + RECEIVER_ADDR).getAddress();
			senderSocket.remoteAddress=Integer.reverseBytes(ipaddress); 
			
			receiverSocket.remoteAddress = Integer.reverseBytes(IpAddress.getAddress("192.168.0." + SENDER_ADDR).getAddress());
			
			TcpSegment senderSegment = FillSegmentData((short)100,(short)120,seq_num,12,(short)(ACK_FLAG | PUSH_FLAG | SYN_FLAG | FIN_FLAG),(short)4); 
			TcpSegment receiverSegment = FillSegmentData((short)120,(short)100,seq_num,12,(short)(ACK_FLAG | PUSH_FLAG | SYN_FLAG | FIN_FLAG),(short)4); 
			senderSocket.sendSegment(senderSegment);
			receiverSocket.receiveSegment(receiverSegment);
						
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public TcpSegment FillSegmentData(short to, short from, int seq, int ack, short flags, short size){
		TcpSegment segment = new TcpSegment();
		segment.setToPort(to);
		segment.setFromPort(from);
		segment.setSeq(seq);
		segment.setAck(ack);
		segment.setFlags(flags);
		segment.setWindowSize(size);
		
		return segment;
	}
	
}
