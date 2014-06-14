package nl.vu.cs.cn;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;

import junit.framework.TestCase;

public class ConnectAcceptTest extends TestCase {
	
	public static int CLIENT_IP = 1;
	
	public static int CLIENT_2_IP = 2;
	
	public static int SERVER_IP = 10;
	
	public static int SERVER_PORT = 4444;
	
	public void testSimpleConnectAccept() throws IOException, InterruptedException {
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
	
	public void testConnectionTryToNonexistentHost() throws IOException {
		/*Socket client = new TCP(CLIENT_IP).socket();
		IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
		boolean isConnected = client.connect(serverAddr, SERVER_PORT);
		
		assertFalse(isConnected);
		assertEquals(client.state, ConnectionState.CLOSED); */
	}
	
	public void testConnectionTryToAlreadyBoundHost() throws IOException, InterruptedException {
		/* final Socket client = new TCP(CLIENT_IP).socket();
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
		assertEquals(client.remoteSequenceNumber + 1, server.localSequenceNumber);
		
		assertEquals(server.state, ConnectionState.ESTABLISHED);
		assertEquals(server.remoteAddress, client.localAddress);
		assertEquals(server.remotePort, client.localPort);
		assertEquals(server.remoteSequenceNumber + 1, client.localSequenceNumber);
		
		assertEquals(secondClient.state, ConnectionState.CLOSED);
		assertFalse(isSecondConnected); */
	}
}
