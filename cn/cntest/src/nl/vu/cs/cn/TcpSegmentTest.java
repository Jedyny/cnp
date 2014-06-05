package nl.vu.cs.cn;

import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;
import junit.framework.TestCase;

public class TcpSegmentTest extends TestCase {
	
	public void testFromToByteArray() {
		TcpSegment segment = new TcpSegment();
		segment.setToPort((short) 20);
		segment.setFromPort((short) 80);
		segment.setSeq(123456789);
		segment.setAck(123456790);
		segment.setFlags((byte) (ACK_FLAG | SYN_FLAG));
		byte[] arr = segment.toByteArray();
		
		TcpSegment restoredSegment = new TcpSegment();
		restoredSegment.fromByteArray(arr, arr.length);
		assertEquals(restoredSegment.getToPort(), 20);
		assertEquals(restoredSegment.getFromPort(), 80);
		assertEquals(restoredSegment.getSeq(), 123456789);
		assertEquals(restoredSegment.getAck(), 123456790);
		assertTrue(restoredSegment.hasAckFlag());
		assertTrue(restoredSegment.hasSynFlag());
		assertFalse(restoredSegment.hasFinFlag());
	}
	
	public void testDataWriteRead() {
		String msg = "Quoth the raven, \"Nevermore.\"";
		byte[] data = msg.getBytes();
		
		TcpSegment segment = new TcpSegment();
		segment.setToPort((short) 20);
		segment.setFromPort((short) 80);
		segment.setSeq(123456789);
		segment.setAck(123456790);
		segment.setFlags((byte) (ACK_FLAG | SYN_FLAG));
		segment.setData(data, 0, data.length);
		
		assertEquals(segment.getToPort(), 20);
		assertEquals(segment.getFromPort(), 80);
		assertEquals(segment.getSeq(), 123456789);
		assertEquals(segment.getAck(), 123456790);
		assertTrue(segment.hasAckFlag());
		assertTrue(segment.hasSynFlag());
		assertFalse(segment.hasFinFlag());
		
		byte[] restoredData = new byte[data.length];
		segment.getData(restoredData, 0);
		assertEquals(new String(restoredData), msg);
	}
	
	public void testDataWriteReadWithOffset() {
		String msg = "Quoth the raven, \"Nevermore.\"";
		byte[] data = msg.getBytes();
		
		TcpSegment segment = new TcpSegment();
		segment.setToPort((short) 20);
		segment.setFromPort((short) 80);
		segment.setSeq(123456789);
		segment.setAck(123456790);
		segment.setFlags((byte) (ACK_FLAG | SYN_FLAG));
		segment.setData(data, 10, 5);
		
		assertEquals(segment.getToPort(), 20);
		assertEquals(segment.getFromPort(), 80);
		assertEquals(segment.getSeq(), 123456789);
		assertEquals(segment.getAck(), 123456790);
		assertTrue(segment.hasAckFlag());
		assertTrue(segment.hasSynFlag());
		assertFalse(segment.hasFinFlag());
		
		byte[] prefix = "black ".getBytes();
		byte[] restoredData = new byte[prefix.length + 5];
		System.arraycopy(prefix, 0, restoredData, 0, prefix.length);
		segment.getData(restoredData, prefix.length);
		assertEquals(new String(restoredData), "black raven");
	}
}
