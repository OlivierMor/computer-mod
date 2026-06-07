package com.computermod.runtime;

import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

/**
 * Per-instruction hook used to throttle the Lua worker to a fixed clock rate. The LuaJ interpreter
 * calls {@link #onInstruction} once per VM instruction; after a slice of instructions is spent we
 * ask the {@link Clock} for the next slice, which blocks the worker thread until the main server
 * thread grants it (once per game tick). This bounds CPU per computer while letting tight loops run
 * continuously at clock speed.
 *
 * <p>Must be installed via {@code globals.load(this)} so the base {@link DebugLib} initializes its
 * globals reference (the VM's call/return hooks rely on it).
 */
public class BudgetDebugLib extends DebugLib {

	/** Blocks until the next clock slice is available and returns its instruction allowance. */
	public interface Clock {
		int nextSlice();
	}

	private final Clock clock;
	private int remaining;

	public BudgetDebugLib(Clock clock, int initialSlice) {
		this.clock = clock;
		this.remaining = Math.max(1, initialSlice);
	}

	@Override
	public void onInstruction(int pc, Varargs v, int top) {
		if (--remaining <= 0)
			remaining = clock.nextSlice();
	}
}
