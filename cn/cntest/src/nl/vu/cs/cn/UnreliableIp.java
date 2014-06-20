package nl.vu.cs.cn;


import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import android.util.Log;

public class UnreliableIp extends IP {
	
	public static final String TAG = "Big Bad Rude IP Wizard";
	
	public UnreliableIp(int address) throws IOException {
		super(address);
	}
	
	@Override
	public int ip_send(Packet p) throws IOException {
		if (random.nextDouble() < packageLossProbability) {
			Log.i(TAG, "Abracadabra, hocus pocus, packet lost, bitch.");
			return p.length;
		}
		
		if (random.nextDouble() < packageCorruptionProbability) {
			Log.i(TAG, "Abracadabra, alakazam, pikachu, packet corrupted, asshole.");
			Packet newPacket = new Packet(); // we cannot currupt directly the packet, since Socket class reuses it.
			newPacket.destination = p.destination;
			newPacket.id = p.id;
			newPacket.length = p.length;
			newPacket.protocol = p.protocol;
			newPacket.source = p.source;
			newPacket.data = new byte[p.data.length];
			random.nextBytes(newPacket.data);
			p = newPacket;
		}
		
		if (random.nextDouble() < packageDuplicationProbability) {
			Log.i(TAG, "Wingardium leviooosa, packet cloned, moron.");
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
