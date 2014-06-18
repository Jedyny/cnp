package nl.vu.cs.cn;

import java.io.IOException;

import junit.framework.TestCase;
import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.TCP.Socket;

public class ReadWritePacketTest extends TestCase {

	public static int SENDER_ADDR = 123;

	public static int RECEIVER_ADDR = 125;

	public static short SENDER_PORT = 1234;

	public static short RECEIVER_PORT = 4321;

	private Socket sender;

	private Socket receiver;

	@Override
	public void setUp() throws IOException {

		sender = new TCP(SENDER_ADDR).socket(SENDER_PORT);
		receiver = new TCP(RECEIVER_ADDR).socket(RECEIVER_PORT);

		int senderIpLittleEndian = IpAddress.getAddress(
				"192.168.0." + SENDER_ADDR).getAddress();
		int receiverIpLittleEndian = IpAddress.getAddress(
				"192.168.0." + RECEIVER_ADDR).getAddress();

		sender.remoteAddress = Integer.reverseBytes(receiverIpLittleEndian);
		receiver.remoteAddress = Integer.reverseBytes(senderIpLittleEndian);

		sender.localSequenceNumber = TCP.getInitSequenceNumber();
		receiver.localSequenceNumber = TCP.getInitSequenceNumber();

		sender.remoteSequenceNumber = receiver.localSequenceNumber;
		receiver.remoteSequenceNumber = sender.localSequenceNumber;

		sender.remotePort = RECEIVER_PORT;
		receiver.remotePort = SENDER_PORT;

		sender.state = ConnectionState.ESTABLISHED;
		receiver.state = ConnectionState.ESTABLISHED;

	}

	public void testReadWriteWithoutData() throws InterruptedException {
		sendData("");
	}

	public void testReadWriteWithData() throws InterruptedException {
		sendData("We're all mad here");
	}

	public void testReadWriteLotOfData() throws InterruptedException {
		sendData(JABBERWOCKY);
	}
	
	public void testReadWriteWithDifferentBuffers() throws InterruptedException {
		sendData(JABBERWOCKY, 100, 20);
		sendData(JABBERWOCKY, 20, 100);
	}
	
	public void testReadLessThanExpected() throws InterruptedException {
		byte[] jabberbytes = JABBERWOCKY.getBytes();
		sendData(JABBERWOCKY, jabberbytes.length, jabberbytes.length + 32, jabberbytes.length + 32);
	}
	
	public void sendData(String msg) throws InterruptedException {
		sendData(msg, msg.getBytes().length, msg.getBytes().length, msg.getBytes().length);
	}
	
	public void sendData(String msg, int writerBufLen, int readerBufLen) throws InterruptedException {
		sendData(msg, writerBufLen, readerBufLen, msg.getBytes().length);
	}

	public void sendData(final String msg, final int writerBufLen, final int readerBufLen, final int expected) throws InterruptedException {
		final byte[] msgAsBytes = msg.getBytes();
		final byte[] receivedBytes = new byte[msgAsBytes.length];

		Runnable writer = new Runnable() {
			@Override
			public void run() {
				byte[] buf = new byte[writerBufLen];
				for (int currOffset = 0, chunkSize = Math.min(buf.length, msgAsBytes.length);
						currOffset < msg.length();
						currOffset += chunkSize, chunkSize = Math.min(buf.length, msgAsBytes.length - currOffset)) {
					System.arraycopy(msgAsBytes, currOffset, buf, 0, chunkSize);
					sender.write(buf, 0, chunkSize);
				}
			};
		};

		Runnable reader = new Runnable() {
			@Override
			public void run() {
				byte[] buf = new byte[readerBufLen];
				int readBytes = 0;
				for (int currOffset = 0, chunkSize = Math.min(buf.length, expected);
						currOffset < msg.length();
						currOffset += chunkSize, chunkSize = Math.min(buf.length, expected - currOffset)) {
					int readChunkSize = receiver.read(buf, 0, chunkSize);
					System.arraycopy(buf, 0, receivedBytes, currOffset, readChunkSize);
					readBytes += readChunkSize;
				}
				assertEquals(msgAsBytes.length, readBytes);
			};
		};

		Thread writerThread = new Thread(writer);
		writerThread.start();
		reader.run();
		writerThread.join();

		String receivedMsg = new String(receivedBytes);
		assertEquals(msg, receivedMsg);
	}
	
	public static String JABBERWOCKY = "Twas brillig, and the slithy toves\n"
			+ "Did gyre and gimble in the wabe;\n"
			+ "All mimsy were the borogoves,\n"
			+ "And the mome raths outgrabe.\n" + "\n"
			+ "\"Beware the Jabberwock, my son!\n"
			+ "The jaws that bite, the claws that catch!\n"
			+ "Beware the Jubjub bird, and shun\n"
			+ "The frumious Bandersnatch!\"\n" + "\n"
			+ "He took his vorpal sword in hand:\n"
			+ "Long time the manxome foe he sought—\n"
			+ "So rested he by the Tumtum tree,\n"
			+ "And stood awhile in thought.\n" + "\n"
			+ "And as in uffish thought he stood,\n"
			+ "The Jabberwock, with eyes of flame,\n"
			+ "Came whiffling through the tulgey wood,\n"
			+ "And burbled as it came!\n" + "\n"
			+ "One, two! One, two! and through and through\n"
			+ "The vorpal blade went snicker-snack!\n"
			+ "He left it dead, and with its head\n"
			+ "He went galumphing back.\n" + "\n"
			+ "\"And hast thou slain the Jabberwock?\n"
			+ "Come to my arms, my beamish boy!\n"
			+ "O frabjous day! Callooh! Callay!\"\n"
			+ "He chortled in his joy.\n" + "\n"
			+ "'Twas brillig, and the slithy toves\n"
			+ "Did gyre and gimble in the wabe;\n"
			+ "All mimsy were the borogoves,\n"
			+ "And the mome raths outgrabe.\n";
}
