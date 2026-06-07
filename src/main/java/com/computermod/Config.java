package com.computermod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration. Holds tunables for the computer's compute budget and power model.
 * More values (FE cost, RPM scaling) are added alongside the power milestone.
 */
public class Config {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	public static final ModConfigSpec.IntValue MAX_OPS_PER_TICK = BUILDER
		.comment("Computer clock speed: Lua instructions executed per game tick (x20 = per second).",
			"Higher = faster computation but more CPU per running computer.")
		.defineInRange("maxOpsPerTick", 200_000, 1_000, 50_000_000);

	public static final ModConfigSpec.DoubleValue MIN_RPM = BUILDER
		.comment("Minimum absolute RPM required for a kinetically-powered computer to run.")
		.defineInRange("minRpm", 1.0, 0.0, 4096.0);

	public static final ModConfigSpec.IntValue FE_CAPACITY = BUILDER
		.comment("Internal Forge Energy buffer capacity of a computer (FE).")
		.defineInRange("feCapacity", 100_000, 0, Integer.MAX_VALUE);

	public static final ModConfigSpec.IntValue FE_PER_TICK = BUILDER
		.comment("Forge Energy consumed per tick while running on electrical power (when not kinetically powered).")
		.defineInRange("fePerTick", 20, 0, Integer.MAX_VALUE);

	public static final ModConfigSpec SPEC = BUILDER.build();

	private Config() {}
}
