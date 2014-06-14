package nl.vu.cs.cn;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import junit.framework.TestCase;

public class ClosedConnectionTest extends TestCase {
	
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
	
		sender.remoteSequenceNumber = receiver.localSequenceNumber - 1;
		receiver.remoteSequenceNumber = sender.localSequenceNumber - 1;
		
		sender.remotePort = RECEIVER_PORT;
		receiver.remotePort = SENDER_PORT;
	}

	public class writeAndCloseThread implements Runnable  { 
		  private byte[] buffer;
		  private int offset;
		  private int len;
			//This method will be executed when this thread is executed
			public writeAndCloseThread(byte [] buffer,int offset, int len){
				this.buffer = buffer;
				this.offset = offset;
				this.len = len;
			}
			public void run() 
			{	  
				sender.state = ConnectionState.ESTABLISHED;
				sender.write(buffer, offset, len);
				sender.close();
		   }
		}
		
		public class readThread implements Runnable { 
			  private byte[] buffer;
			  private int offset;
			  private int len;
			  
			public readThread(byte [] buffer,int offset, int len){
					this.buffer = buffer;
					this.offset = offset;
					this.len = len;
			  }
			  public void run() 
			  {
				  receiver.state = ConnectionState.ESTABLISHED;
				  receiver.read(buffer, offset, len);
			   } 
			}

	
	
	public void testSimpleCloseConnection() {
		
		sender.state = ConnectionState.CLOSED;
		boolean returnedValue = sender.close();
		assertEquals(returnedValue, false);
	}
	
	
	public void testWriteAndCloseConnection() throws InterruptedException{
		
		String message = "Are you going to be late again?";
		int offset = 0;
		int len = message.length();
		
		Thread write = new Thread(new writeAndCloseThread(message.getBytes(), offset, len), "Ms. Writer");
		write.start();
		byte [] receivedData = new byte[message.length()];
		Thread read = new Thread(new readThread(receivedData, offset, len), "Mr. Reader");
		read.start();
		write.join();
		read.join();
		
		assertEquals(new String(receivedData), message);
		assertEquals(sender.state, ConnectionState.READ_ONLY);	
	}

	public class writeThread implements Runnable  { 
		  private byte[] buffer;
		  private int offset;
		  private int len;
			//This method will be executed when this thread is executed
			public writeThread(byte [] buffer,int offset, int len){
				this.buffer = buffer;
				this.offset = offset;
				this.len = len;
			}
			public void run() 
			{	  
				sender.state = ConnectionState.ESTABLISHED;
				sender.write(buffer, offset, len);
		   }
		}
		
		public class readAndCloseThread implements Runnable { 
			  private byte[] buffer;
			  private int offset;
			  private int len;
			  
			public readAndCloseThread(byte [] buffer,int offset, int len){
					this.buffer = buffer;
					this.offset = offset;
					this.len = len;
			  }
			  public void run() 
			  {
				  receiver.state = ConnectionState.ESTABLISHED;
				  receiver.read(buffer, offset, len);
				  receiver.close();
			   } 
			}
	
	public void testReadAndCloseConnection() throws InterruptedException {
		
		String message = "Are you going to be late again?";
		int offset = 0;
		int len = message.length();
		
		Thread write = new Thread(new writeThread(message.getBytes(), offset, len), "Ms. Writer");
		write.start();
		byte [] receivedData = new byte[message.length()];
		Thread read = new Thread(new readAndCloseThread(receivedData, offset, len), "Mr. Reader");
		read.start();
		write.join();
		read.join();
		
		assertEquals(receiver.state, ConnectionState.READ_ONLY);
		
	}
	
	

	
	
	

}
