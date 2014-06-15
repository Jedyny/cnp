package nl.vu.cs.cn;


import static nl.vu.cs.cn.TcpSegment.ACK_FLAG;
import static nl.vu.cs.cn.TcpSegment.PUSH_FLAG;
import static nl.vu.cs.cn.TcpSegment.SYN_FLAG;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import junit.framework.TestCase;

public class ConnectAcceptTest extends TestCase {
	
	public static int CLIENT_IP = 1;
	
	public static int CLIENT_2_IP = 2;
	
	public static int SERVER_IP = 10;
	
	public static int SERVER_PORT = 4444;

	
	public void I_testSimpleConnectAccept() throws IOException, InterruptedException {
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		
		Runnable clientTestTask = new Runnable() {
			@Override public void run() {
				IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
				client.connect(serverAddr, SERVER_PORT);
			}
		};
		
		Runnable serverTestTask = new Runnable() {
			@Override public void run() {
				server.accept();
			}
		};
		
		Thread serverThread = new Thread(serverTestTask);
		serverThread.start();
		clientTestTask.run();
		serverThread.join();
		
		assertEquals(client.state, ConnectionState.ESTABLISHED);
		assertEquals(client.remoteAddress, server.localAddress);
		assertEquals(client.remotePort, server.localPort);
		assertEquals(client.remoteSequenceNumber, server.localSequenceNumber);
		
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		assertEquals(server.remoteAddress, client.localAddress);
		assertEquals(server.remotePort, client.localPort);
		assertEquals(server.remoteSequenceNumber, client.localSequenceNumber);
	}
	
	public void I_testConnectionTryToNonexistentHost() throws IOException {
		Socket client = new TCP(CLIENT_IP).socket();
		IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
		boolean isConnected = client.connect(serverAddr, SERVER_PORT);
		
		assertFalse(isConnected);
		assertEquals(client.state, ConnectionState.CLOSED);
	}
	
	public void I_testConnectionTrytoexistentHost() throws IOException{
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		
		IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
		boolean isConnected = client.connect(serverAddr, SERVER_PORT);
		
		assertFalse(isConnected);
		assertEquals(client.state, ConnectionState.CLOSED);
		
	}
	
	public void I_testConnectionTryToAlreadyBoundHost() throws IOException, InterruptedException {
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket secondClient = new TCP(CLIENT_2_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		final IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
		
		Runnable clientTestTask = new Runnable() {
			@Override public void run() {
				client.connect(serverAddr, SERVER_PORT);
			}
		};
		
		Runnable serverTestTask = new Runnable() {
			@Override public void run() {
				server.accept();
			}
		};
		
		Thread serverThread = new Thread(serverTestTask);
		serverThread.start();
		clientTestTask.run();
		serverThread.join();
		
		boolean isSecondConnected = secondClient.connect(serverAddr, SERVER_PORT);
		
		assertEquals(client.state, ConnectionState.ESTABLISHED);
		assertEquals(client.remoteAddress, server.localAddress);
		assertEquals(client.remotePort, server.localPort);
		assertEquals(client.remoteSequenceNumber, server.localSequenceNumber);
		
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		assertEquals(server.remoteAddress, client.localAddress);
		assertEquals(server.remotePort, client.localPort);
		assertEquals(server.remoteSequenceNumber, client.localSequenceNumber);
		
		assertEquals(secondClient.state, ConnectionState.CLOSED);
		assertFalse(isSecondConnected);
	}
	
	public void I_testSynAckLostPacket () throws IOException, InterruptedException {
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket secondClient = new TCP(CLIENT_2_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		final IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
		
		
		Runnable clientTestTask = new Runnable() {
			@Override public void run() {
				TcpSegment segment = newSegment(client,12345,(byte)(SYN_FLAG));
				client.sendSegment(segment);
			}
		};
		
		Runnable serverTestTask = new Runnable() {
			@Override public void run() {
				server.accept();
				try {
				    Thread.sleep(10);
				} catch(InterruptedException ex) {
				    Thread.currentThread().interrupt();
				}
			}
		};
		
		Thread serverThread = new Thread(serverTestTask);
		serverThread.start();
		clientTestTask.run();
		boolean isSecondConnected = secondClient.connect(serverAddr, SERVER_PORT);
		
		serverThread.join();
		
		assertEquals(client.state, ConnectionState.CLOSED);
	
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		assertEquals(server.remoteAddress, secondClient.localAddress);
		assertEquals(server.remotePort, secondClient.localPort);
		assertEquals(server.remoteSequenceNumber, secondClient.localSequenceNumber);
		
		assertEquals(secondClient.state, ConnectionState.ESTABLISHED);
		assertEquals(secondClient.remoteAddress, server.localAddress);
		assertEquals(secondClient.remotePort, server.localPort);
		assertEquals(secondClient.remoteSequenceNumber, server.localSequenceNumber);
		assertTrue(isSecondConnected);
		
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
	
	public void I_testAckLostClientReads () throws IOException,InterruptedException {
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		final int offset = 0;
		final int length = 25;
		final byte[] receivedBytes = new byte[length];

		client.localSequenceNumber = TCP.getInitSequenceNumber();
		server.localSequenceNumber = TCP.getInitSequenceNumber();
		
		Runnable clientTestTask = new Runnable() {
			@Override public void run() {
				IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
				client.connect(serverAddr, SERVER_PORT);
				client.read(receivedBytes, offset, length);
			}
		};
		
		Runnable serverTestTask = new Runnable() {
			@Override public void run() {
				server.accept();
				TcpSegment segment = newSegment(client,client.localSequenceNumber,(byte)(SYN_FLAG | ACK_FLAG | PUSH_FLAG));
				server.sendSegment(segment);
			}
		};
		
		Thread serverThread = new Thread(serverTestTask);
		serverThread.start();
		clientTestTask.run();
		serverThread.join();
		
		assertEquals(client.state, ConnectionState.ESTABLISHED);
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		
	}
	
	public void testAckLostClientWrites () throws IOException,InterruptedException {
		final Socket client = new TCP(CLIENT_IP).socket();
		final Socket server = new TCP(SERVER_IP).socket(SERVER_PORT);
		final String msg = "What are we doing tonight?";
		final int offset = 0;
		final int length = msg.length();
		final byte[] receivedBytes = new byte[length];
		client.localSequenceNumber = TCP.getInitSequenceNumber();
		server.localSequenceNumber = TCP.getInitSequenceNumber();
		
		Runnable clientTestTask = new Runnable() {
			@Override public void run() {
				IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
				client.connect(serverAddr, SERVER_PORT);
				TcpSegment receivedSynAckSegment = server.receivedSegment;
				client.receiveSegment(server.receivedSegment);
				client.write(msg.getBytes(), offset, length);
				
			}
		};
		
		Runnable serverTestTask = new Runnable() {
			@Override public void run() {
				server.accept();
				TcpSegment segment = newSegment(client,client.localSequenceNumber,(byte)(SYN_FLAG | ACK_FLAG | PUSH_FLAG));
				server.sendSegment(segment);
				server.receiveSegment(server.receivedSegment);
				TcpSegment firstReceivedSegment = server.receivedSegment;
				server.receiveSegment(server.receivedSegment);
				TcpSegment SecondReceivedSegment = server.receivedSegment;
				//server.read(receivedBytes, offset, length);
				
				assertEquals(firstReceivedSegment.hasAckFlag(),true);
				assertEquals(SecondReceivedSegment.hasAckFlag(),true);
				assertEquals(SecondReceivedSegment.dataLength, msg.length());
				
			}
		};
		
		Thread serverThread = new Thread(serverTestTask);
		serverThread.start();
		clientTestTask.run();
		serverThread.join();
		
		assertEquals(client.state, ConnectionState.ESTABLISHED);
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		assertEquals(new String(receivedBytes), msg);
	}
	

}


