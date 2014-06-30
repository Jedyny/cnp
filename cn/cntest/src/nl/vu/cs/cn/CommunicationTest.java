package nl.vu.cs.cn;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.TCP.Socket;

public class CommunicationTest extends TestCase {
	
	public static int CLIENT_ADDR = 1;
	
	public static int SERVER_ADDR = 101;
	
	public static short CLIENT_PORT = 1444;
	
	public static short SERVER_PORT = 1555;

	public void testInformationExchange() throws IOException, InterruptedException {
		final Socket client = new TCP(CLIENT_ADDR).socket();
		final Socket server = new TCP(SERVER_ADDR).socket(SERVER_PORT);
		
		final IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_ADDR);
		
		String clientMessage = "Wer reitet so spät durch Nacht und Wind?";
		String serverMessage = "Es ist der Vater mit seinem Kind;";
		
		final byte[] clientWriteBuf = clientMessage.getBytes();
		final byte[] serverWriteBuf = serverMessage.getBytes();

		final byte[] clientReadBuf = new byte[serverWriteBuf.length];
		final byte[] serverReadBuf = new byte[clientWriteBuf.length]; 
		
		Runnable clientTask = new Runnable() {
			@Override public void run() {
				client.connect(serverAddr, SERVER_PORT);
				client.write(clientWriteBuf, 0, clientWriteBuf.length);
				client.close();
				client.read(clientReadBuf, 0, clientReadBuf.length);
			}
		};
		
		Runnable serverTask = new Runnable() {
			@Override public void run() {
				server.accept();
				server.read(serverReadBuf, 0, serverReadBuf.length);
				server.write(serverWriteBuf, 0, serverWriteBuf.length);
				server.close();
			}
		};
		
		Thread serverThread = new Thread(serverTask);
		serverThread.start();
		clientTask.run();
		serverThread.join();
		
		assertEquals(clientMessage, new String(serverReadBuf));
		assertEquals(serverMessage, new String(clientReadBuf));
	}
	
	public void testInformationExchangeInUnreliableEnvironment() throws IOException, InterruptedException {
		final Socket client = new UnreliableTcp(CLIENT_ADDR, 0.1, 0.1, 0.1).socket();
		final Socket server = new UnreliableTcp(SERVER_ADDR, 0.1, 0.1, 0.1).socket(SERVER_PORT);
		
		final IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_ADDR);
		
		String clientMessage = "Wer reitet so spät durch Nacht und Wind?";
		String serverMessage = "Es ist der Vater mit seinem Kind;";
		
		final byte[] clientWriteBuf = clientMessage.getBytes();
		final byte[] serverWriteBuf = serverMessage.getBytes();

		final byte[] clientReadBuf = new byte[serverWriteBuf.length];
		final byte[] serverReadBuf = new byte[clientWriteBuf.length]; 
		
		Runnable clientTask = new Runnable() {
			@Override public void run() {
				client.connect(serverAddr, SERVER_PORT);
				client.write(clientWriteBuf, 0, clientWriteBuf.length);
				client.close();
				client.read(clientReadBuf, 0, clientReadBuf.length);
			}
		};
		
		Runnable serverTask = new Runnable() {
			@Override public void run() {
				server.accept();
				server.read(serverReadBuf, 0, serverReadBuf.length);
				server.write(serverWriteBuf, 0, serverWriteBuf.length);
				server.close();
			}
		};
		
		Thread serverThread = new Thread(serverTask);
		serverThread.start();
		clientTask.run();
		serverThread.join();
		
		assertEquals(clientMessage, new String(serverReadBuf));
		assertEquals(serverMessage, new String(clientReadBuf));
	}
}
