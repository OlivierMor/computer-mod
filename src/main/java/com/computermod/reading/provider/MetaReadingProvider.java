package com.computermod.reading.provider;

import java.util.Map;

import com.computermod.reading.ReadingProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/** Basic identity: block id, whether it's air, and whether it has a block entity. */
public class MetaReadingProvider implements ReadingProvider {
	@Override
	public void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out) {
		out.put("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		out.put("is_air", state.isAir());
		out.put("has_block_entity", be != null);
	}
}
