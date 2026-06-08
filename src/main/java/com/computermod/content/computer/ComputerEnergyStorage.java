package com.computermod.content.computer;

import java.util.function.LongSupplier;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * The computer's internal Forge Energy buffer. Neighbours can charge it (receive), but it cannot be
 * drained externally (maxExtract = 0); the computer consumes it internally each tick via
 * {@link #consume(int)}.
 *
 * <p>This buffer is only a small input smoothing capacitor, not a battery: the computer treats
 * itself as electrically powered only while energy is <em>actively being supplied</em>
 * ({@link #isBeingSupplied(long)}). Cutting the feed turns the computer off within a tick, like a
 * real microcontroller losing Vcc — whatever charge happens to remain in the buffer is not enough
 * to keep it running on its own.
 */
public class ComputerEnergyStorage extends EnergyStorage {

	/** Supplies the current game time so receives can be timestamped. */
	private final LongSupplier gameTime;
	/** Last game tick on which energy actually flowed in; {@link Long#MIN_VALUE} if never. */
	private long lastReceivedTick = Long.MIN_VALUE;

	public ComputerEnergyStorage(int capacity, int maxReceive, LongSupplier gameTime) {
		super(capacity, maxReceive, 0);
		this.gameTime = gameTime;
	}

	@Override
	public int receiveEnergy(int toReceive, boolean simulate) {
		int received = super.receiveEnergy(toReceive, simulate);
		if (!simulate && received > 0)
			lastReceivedTick = gameTime.getAsLong();
		return received;
	}

	/**
	 * True if energy was pushed in on the current or previous tick. The one-tick tolerance absorbs
	 * the arbitrary ordering between this block entity and the supplier ticking; any longer gap
	 * means the supply is gone and the computer must power down.
	 */
	public boolean isBeingSupplied(long now) {
		return now - lastReceivedTick <= 1;
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
