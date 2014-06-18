package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.FIN_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.nio.ByteBuffer;

import junit.framework.TestCase;
import nl.vu.cs.cn.IP.IpAddress;

public class ChecksumCalculationTest extends TestCase {
	
	public void testChecksumCalculationSynSegment() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 80);
		segment.setToPort((short) 15200);
		segment.setSeq(512964240);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (SYN_FLAG | PUSH_FLAG) );
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(1);
		int toAddr = getNetworkAddress(10);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	public void testChecksumCalculationSynAckSegment() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 13444);
		segment.setToPort((short) 1234);
		segment.setSeq(298562202);
		segment.setAck(240114204);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (SYN_FLAG | ACK_FLAG | PUSH_FLAG) );
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(5);
		int toAddr = getNetworkAddress(72);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	public void testChecksumCalculationDataSegment() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 12544);
		segment.setToPort((short) 3490);
		segment.setSeq(643389353);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (PUSH_FLAG));
		
		String data = "Happy families are all alike; every unhappy family is unhappy in its own way.";
		byte[] dataAsByte = data.getBytes();
		segment.setData(dataAsByte, 0, dataAsByte.length);
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(19);
		int toAddr = getNetworkAddress(27);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	public void testChecksumCalculationDataSegmentWithGarbage() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 12544);
		segment.setToPort((short) 3490);
		segment.setSeq(643389353);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (PUSH_FLAG));
		
		String data = "On an exceptionally hot evening early in July a young "
				+ "man came out of the garret in which he lodged in S. Place "
				+ "and walked slowly, as though in hesitation, towards K. bridge.";
		byte[] dataAsByte = data.getBytes();
		segment.setData(dataAsByte, 0, dataAsByte.length);
		segment.length -= 20;
		segment.dataLength -= 20;
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(19);
		int toAddr = getNetworkAddress(27);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	public void testChecksumCalculationAckSegment() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 66);
		segment.setToPort((short) 13004);
		segment.setSeq(399420411);
		segment.setAck(240230570);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (ACK_FLAG | PUSH_FLAG) );
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(66);
		int toAddr = getNetworkAddress(4);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	public void testChecksumCalculationFinSegment() {
		TcpSegment segment = new TcpSegment();
		segment.setFromPort((short) 5555);
		segment.setToPort((short) 4444);
		segment.setSeq(739500235);
		segment.setWindowSize((short) 1);
		segment.setFlags((byte) (FIN_FLAG | PUSH_FLAG) );
		
		ByteBuffer buffer = ByteBuffer.allocate(12 + segment.length);
		
		int fromAddr = getNetworkAddress(100);
		int toAddr = getNetworkAddress(56);
		
		buffer.putInt(fromAddr);
		buffer.putInt(toAddr);
		buffer.putShort((short) IP.TCP_PROTOCOL);
		buffer.putShort((short) segment.length);
		buffer.put(segment.toByteArray(), 0, segment.length);
		
		long ourChecksum = ourChecksumFor(segment, fromAddr, toAddr);
		long expectedChecksum = expectedChecksumFor(buffer.array());
		
		assertEquals(expectedChecksum, ourChecksum);
		
		segment.setChecksum((short) ourChecksum);
		assertEquals(0, ourChecksumFor(segment, fromAddr, toAddr));
	}
	
	private int getNetworkAddress(int suffix) {
		return Integer.reverseBytes(IpAddress.getAddress("192.168.0." + suffix).getAddress());
	}
	
	private long ourChecksumFor(TcpSegment segment, int localAddress, int remoteAddress) {
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
		return sum;
	}
	
	// implementation found on Stack Overflow
	private long expectedChecksumFor(byte[] buf) {
	    int length = buf.length;
	    int i = 0;
	    long sum = 0;
	    long data;

	    // loop through all 16-bit words unless there's 0 or 1 byte left.
	    while( length > 1 ){
	        data = ( ((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
	        sum += data;
	        if( (sum & 0xFFFF0000) > 0 ){
	            sum = sum & 0xFFFF;
	            sum += 1;
	        }
	        i += 2;
	        length -= 2;
	    }

	    if (length > 0 ){ // ie. there are 8 bits of data remaining.
	        sum += (buf[i] << 8 & 0xFF00); // create a 16 bit word where the 8 lsb are 0's and add it to the sum.
	        if( (sum & 0xFFFF0000) > 0) {
	            sum = sum & 0xFFFF;
	            sum += 1;
	        }
	    }

	    sum = ~sum; 
	    sum = sum & 0xFFFF;
	    return sum;
	}

}
