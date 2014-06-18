package nl.vu.cs.cn;

import java.io.IOException;

import junit.framework.TestCase;
import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.TCP.Socket;

public class CloseConnectionTest extends TestCase {
	
	public static int SENDER_ADDR = 123;
	
	public static int RECEIVER_ADDR = 125;
	
	public static short SENDER_PORT = 1234;
	
	public static short RECEIVER_PORT = 4321;
	
	private Socket sender;
	
	private Socket receiver;
	private IpAddress senderAddr;
	private IpAddress receiverAddr;
	@Override
	public void setUp() throws IOException {
		sender = new TCP(SENDER_ADDR).socket(SENDER_PORT);
		receiver = new TCP(RECEIVER_ADDR).socket(RECEIVER_PORT);
		
		senderAddr = IpAddress.getAddress("192.168.0." + SENDER_ADDR);
		receiverAddr = IpAddress.getAddress("192.168.0." + RECEIVER_ADDR);
		
		int senderIpLittleEndian = senderAddr.getAddress();
		int receiverIpLittleEndian = receiverAddr.getAddress();
		
		sender.remoteAddress = Integer.reverseBytes(receiverIpLittleEndian);
		receiver.remoteAddress = Integer.reverseBytes(senderIpLittleEndian);
		
		sender.localSequenceNumber = TCP.getInitSequenceNumber();
		receiver.localSequenceNumber = TCP.getInitSequenceNumber();
	
		sender.remoteSequenceNumber = receiver.localSequenceNumber;
		receiver.remoteSequenceNumber = sender.localSequenceNumber;
		
		sender.remotePort = RECEIVER_PORT;
		receiver.remotePort = SENDER_PORT;
	}		

	public void testTryToCloseWhenNoConnection() {
		sender.state = ConnectionState.CLOSED;
		boolean returnedValue = sender.close();
		assertEquals(false, returnedValue);
	}
	
	public void testSimpleCloseConnection() {
		sender.state = ConnectionState.ESTABLISHED;
		receiver.state = ConnectionState.ESTABLISHED;
		boolean returnedValue = sender.close();
		assertEquals(true, returnedValue);
		assertEquals(ConnectionState.READ_ONLY, sender.state);
		// receiver won't receive it at that point
		
		returnedValue = receiver.close();
		assertEquals(true, returnedValue);
		assertEquals(ConnectionState.CLOSED, receiver.state);
		// sender won't receive it at that point
	}
	
	public void testWriteAndCloseConnection() throws InterruptedException{
		final String msg = "Are you going to be late again?";
		final byte[] msgAsBytes = msg.getBytes();
		final byte[] receivedBytes = new byte[msgAsBytes.length];
		final int[] readBytes = new int[1];
		
		Runnable writerInHurry = new Runnable() {
			@Override
			public void run() {
				sender.state = ConnectionState.ESTABLISHED;
				sender.write(msgAsBytes, 0, msgAsBytes.length);
				sender.close();
			}
		};
		
		Runnable readerWithPlentyOfTime = new Runnable() {
			@Override
			public void run() {
				receiver.state = ConnectionState.ESTABLISHED;
				readBytes[0] = receiver.read(receivedBytes, 0, Integer.MAX_VALUE);
			}
		};
		
		Thread reader = new Thread(readerWithPlentyOfTime);
		reader.start();
		writerInHurry.run();
		reader.join();
		
		assertEquals(msg, new String(receivedBytes));
		assertEquals(msgAsBytes.length, readBytes[0]);
		assertEquals(ConnectionState.READ_ONLY, sender.state);
		assertEquals(ConnectionState.WRITE_ONLY, receiver.state);
	}
	
	public void testReadAndCloseConnection() throws InterruptedException {
		final String msg = "Are you going to be late again?";
		final byte[] msgAsBytes = msg.getBytes();
		final int wantToReceive = 10;
		final byte[] receivedBytes = new byte[wantToReceive];
		final int[] writtenBytes = new int[1];
		
		Runnable writerWithPlentyOfTime = new Runnable() {
			@Override
			public void run() {
				sender.state = ConnectionState.ESTABLISHED;
				writtenBytes[0] = sender.write(msgAsBytes, 0, msgAsBytes.length);
			}
		};
		
		Runnable readerInHurry = new Runnable() {
			@Override
			public void run() {
				receiver.state = ConnectionState.ESTABLISHED;
				receiver.read(receivedBytes, 0, wantToReceive);
				receiver.close();
			}
		};
		
		Thread reader = new Thread(readerInHurry);
		reader.start();
		writerWithPlentyOfTime.run();
		reader.join();
		
		assertEquals(new String(msgAsBytes, 0, 10), new String(receivedBytes));
		assertEquals(wantToReceive, writtenBytes[0]);
		assertEquals(ConnectionState.READ_ONLY, receiver.state);
		assertEquals(ConnectionState.WRITE_ONLY, sender.state);
	}
}
