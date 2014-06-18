package nl.vu.cs.cn;


import java.io.IOException;
import java.util.Random;

import nl.vu.cs.cn.IP;

public class UnreliableIp extends IP {
	
	public UnreliableIp(int address) throws IOException {
		super(address);
	}
	
	@Override
	public int ip_send(Packet p) throws IOException {
		if (random.nextDouble() < packageLossProbability) {
			return p.length;
		}
		
		if (random.nextDouble() < packageCorruptionProbability) {
			random.nextBytes(p.data);
		}
		
		if (random.nextDouble() < packageDuplicationProbability) {
			super.ip_send(p);
		}
		
		return super.ip_send(p);
	}
	
	public void setCorruptionProbability(double p) {
		packageCorruptionProbability = p;
	}
	
	public void setDuplicationProbability(double p) {
		packageDuplicationProbability = p;
	}
	
	public void setLossProbability(double p) {
		packageLossProbability = p;
	}
	
	private Random random = new Random();

	private double packageCorruptionProbability;
		
	private double packageDuplicationProbability;
	
	private double packageLossProbability;
}
