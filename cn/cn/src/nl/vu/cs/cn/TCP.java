package nl.vu.cs.cn;

import java.io.IOException;

import static nl.vu.cs.cn.util.Preconditions.checkArgument;

/**
 * This class represents a TCP stack. It should be built on top of the IP stack
 * which is bound to a given IP address.
 */
public class TCP {
	
	/** The underlying IP stack for this TCP stack. */
	private IP ip;
	
	private static short DEFAULT_PORT = 1453;
	private static short CURRENT_PORT = DEFAULT_PORT;
	
	/* package */ static int ACK_WAIT_TIMEOUT_SECONDS = 1;
	/* package */ static int MAX_RESEND_TRIALS = 10;
	
	/**
	 * Constructs a TCP stack for the given virtual address. The virtual address
	 * for this TCP stack is then 192.168.1.address.
	 * 
	 * @param address
	 *            The last octet of the virtual IP address 1-254.
	 * @throws IOException
	 *             if the IP stack fails to initialize.
	 */
	public TCP(int address) throws IOException {
		ip = new IP(address);
	}

	/**
	 * @return a new socket for this stack
	 */
	public Socket socket() {
		return new Socket(ip, CURRENT_PORT++);
	}

	/**
	 * @return a new server socket for this stack bound to the given port
	 * @param port
	 *            the port to bind the socket to.
	 */
	public Socket socket(int port) {
		checkArgument(0 < port && port <= 65545);
		return new Socket(ip, (short) port);
	}
	
	/* package */ static int getInitSequenceNumber() {
		return 20051498;
	}
	
}
