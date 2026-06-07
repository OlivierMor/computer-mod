package com.computermod.content.computer;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * The computer's internal Forge Energy buffer. Neighbours can charge it (receive), but it cannot be
 * drained externally (maxExtract = 0); the computer consumes it internally each tick via
 * {@link #consume(int)}.
 */
public class ComputerEnergyStorage extends EnergyStorage {

	public ComputerEnergyStorage(int capacity, int maxReceive) {
		super(capacity, maxReceive, 0);
	}

	/** Internally spend energy to power the computer for a tick. */
	public void consume(int amount) {
		energy = Math.max(0, energy - amount);
	}

	/** Restore persisted energy on load. */
	public void setStored(int amount) {
		energy = Math.min(capacity, Math.max(0, amount));
	}
}
