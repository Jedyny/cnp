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
	
	public void IGNORE_testBusyServer() throws IOException, InterruptedException {
		final int CLIENTS_SIZE = 10; // if changed add new responses; if more that 100 change SERVER_ADDR;
		final int RESPONSE_MAX_SIZE = 100;
		final int MAX_RUN_TIME_SEC = 600;
		
		final Socket[] clients = new Socket[CLIENTS_SIZE];
		for (int i = 0; i < CLIENTS_SIZE; ++i) {
			clients[i] = new TCP(CLIENT_ADDR + i).socket(); 
		}
		final Socket server = new TCP(SERVER_ADDR).socket(SERVER_PORT);
		
		final IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_ADDR);
		
		final String[] serverResponses = { "You shall have no other gods before Me.",
				"You shall not make idols.",
				"You shall not take the name of the LORD your God in vain.",
				"Remember the Sabbath day, to keep it holy.",
				"Honor your father and your mother.",
				"You shall not murder.",
				"You shall not commit adultery.",
				"You shall not steal.",
				"You shall not bear false witness against your neighbor.",
				"You shall not covet."
		};
		final String[] clientReceivedResponses = new String[CLIENTS_SIZE];
		
		final Random rnd = new Random();
		
		class ClientTask implements Runnable {
			
			int id;
			
			ClientTask(int id) {
				this.id = id;
			}
			
			@Override
			public void run() {
				byte[] query = { (byte) id };
				byte[] response = new byte[RESPONSE_MAX_SIZE];
				
				do  {
					try {
						Thread.sleep(rnd.nextInt(4000) + 1000);
					} catch (InterruptedException fuckIt) { }
				} while (!clients[id].connect(serverAddr, SERVER_PORT));
				clients[id].write(query, 0, query.length);
				clients[id].close();
				int receivedBytes = clients[id].read(response, 0, response.length);
				clientReceivedResponses[id] = new String(response, 0, receivedBytes);
			}
		}
		
		Runnable serverTask = new Runnable() {
			@Override public void run() {
				byte[] buf = new byte[1];
				
				for (int accepted = 0; accepted < CLIENTS_SIZE; ++accepted) {
					server.accept();
					int readResult = server.read(buf, 0, buf.length);
					byte[] response = serverResponses[buf[0]].getBytes();
					server.write(response, 0, response.length);
					server.close();
				}
			}
		};
		
		ExecutorService executor = Executors.newFixedThreadPool(CLIENTS_SIZE + 1);
		for (int i = 0; i < CLIENTS_SIZE; ++i) {
			executor.submit(new ClientTask(i));
		}
		executor.submit(serverTask);
		executor.shutdown();
		executor.awaitTermination(MAX_RUN_TIME_SEC, TimeUnit.SECONDS);
		
		for (int i = 0; i < CLIENTS_SIZE; ++i) {
			assertEquals(serverResponses[i], clientReceivedResponses[i]);
		}
	}
	
	

}
