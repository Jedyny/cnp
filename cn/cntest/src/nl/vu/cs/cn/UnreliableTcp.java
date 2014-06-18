package nl.vu.cs.cn;

import java.io.IOException;

public class UnreliableTcp extends TCP {
	
	public UnreliableTcp(int address, double corruption, double duplication, double loss) throws IOException {
		UnreliableIp ip = new UnreliableIp(address);
		ip.setCorruptionProbability(corruption);
		ip.setDuplicationProbability(duplication);
		ip.setLossProbability(loss);
		this.ip = ip;
	}
}
