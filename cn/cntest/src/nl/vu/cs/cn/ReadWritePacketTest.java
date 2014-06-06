package nl.vu.cs.cn;


import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import junit.framework.TestCase;

public class ReadWritePacketTest extends TestCase {
	

	public static int SENDER_ADDR = 123;
	
	public static int RECEIVER_ADDR = 125;
	
	public static short SENDER_PORT = 1234;
	
	public static short RECEIVER_PORT = 4321;
	
	private Socket sender;
	
	private Socket receiver;


	@Override
	public void setUp() throws IOException{
		
		sender = new TCP(SENDER_ADDR).socket(SENDER_PORT);
		receiver = new TCP(RECEIVER_ADDR).socket(RECEIVER_PORT);
		
		int senderIpLittleEndian = IpAddress.getAddress("192.168.0." + SENDER_ADDR).getAddress();
		int receiverIpLittleEndian = IpAddress.getAddress("192.168.0." + RECEIVER_ADDR).getAddress();
		
		sender.remoteAddress = Integer.reverseBytes(receiverIpLittleEndian);
		receiver.remoteAddress = Integer.reverseBytes(senderIpLittleEndian);
		
		sender.localSequenceNumber = TCP.getInitSequenceNumber();
		receiver.localSequenceNumber = TCP.getInitSequenceNumber();
		
		sender.remoteSequenceNumber = receiver.localSequenceNumber - 1;
		receiver.remoteSequenceNumber = sender.localSequenceNumber - 1;
		
		sender.remotePort = RECEIVER_PORT;
		receiver.remotePort = SENDER_PORT;
		
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
			  sender.state = ConnectionState.WRITE_ONLY;
			  sender.write(buffer, offset, len);
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
			  
			  receiver.state = ConnectionState.READ_ONLY;
			  receiver.read(buffer, offset, len);
			  
 
		   } 
		}
		
		
	public void testReadWriteWithoutData() throws InterruptedException {
		
		String message = "";
		int offset = 0;
		int len = message.length();
		
		Thread write = new Thread(new writeThread(message.getBytes(), offset, len), "Ms. Writer");
		write.start();
		byte [] receivedData = new byte[message.length()];
		Thread read = new Thread(new readThread(receivedData, offset, len), "Mr. Reader");
		read.start();
		write.join();
		read.join();
		
		assertEquals(new String(receivedData), message);
				 
	}
	
	public void testReadWriteWithData() throws InterruptedException {
		
		String message = "To be or not to be";
		int offset = 0;
		int len = message.length();
		
		Thread write = new Thread(new writeThread(message.getBytes(), offset, len), "Ms. Writer");
		write.start();
		byte [] receivedData = new byte[message.length()];
		Thread read = new Thread(new readThread(receivedData, offset, len), "Mr. Reader");
		read.start();
		write.join();
		read.join();
		
		assertEquals(new String(receivedData), message);
				 
	}
	
	public void testReadWriteLotOfData() throws InterruptedException {
		
		String witch1= "When shall we three meet again? " +
				"In thunder, lightning or in rain?";
		String witch2 = "When the hurlyburly's done. " +
				"When the battle's lost and won.";
		String witch3 = "That will be ere the set of sun.";
		String message = witch1 + witch2 + witch3;
		int offset = 0;
		int len = message.length();
		
		Thread write = new Thread(new writeThread(message.getBytes(), offset, len), "Ms. Writer");
		write.start();
		byte [] receivedData = new byte[message.length()];
		Thread read = new Thread(new readThread(receivedData, offset, len), "Mr. Reader");
		read.start();
		write.join();
		read.join();
		
		assertEquals(new String(receivedData), message);		 
	}
	



}
