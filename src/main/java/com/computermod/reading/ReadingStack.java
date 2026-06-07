package com.computermod.reading;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.computermod.reading.provider.BlockStateReadingProvider;
import com.computermod.reading.provider.CapabilityReadingProvider;
import com.computermod.reading.provider.KineticReadingProvider;
import com.computermod.reading.provider.MetaReadingProvider;
import com.computermod.reading.provider.NbtReadingProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs every {@link ReadingProvider} against a block and merges their readings into a single map.
 * This is the universal read path used by {@code scan()} in Lua and (later) by sensor blocks.
 */
public final class ReadingStack {

	// Order matters only for which key wins on a collision; providers generally namespace their keys.
	private static final List<ReadingProvider> PROVIDERS = List.of(
		new MetaReadingProvider(),
		new KineticReadingProvider(),
		new CapabilityReadingProvider(),
		new BlockStateReadingProvider(),
		new NbtReadingProvider()
	);

	public static Map<String, Object> scan(Level level, BlockPos pos) {
		Map<String, Object> out = new LinkedHashMap<>();
		BlockState state = level.getBlockState(pos);
		BlockEntity be = level.getBlockEntity(pos);
		for (ReadingProvider provider : PROVIDERS) {
			try {
				provider.collect(level, pos, state, be, out);
			} catch (Exception ignored) {
				// A misbehaving provider/addon must never crash the computer's scan.
			}
		}
		return out;
	}

	private ReadingStack() {}
}
