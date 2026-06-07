package com.computermod.reading;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * One layer of the universal "read anything" stack. Given a block in the world, a provider writes
 * any readings it understands into {@code out} as plain Java values (Number / Boolean / String /
 * {@link java.util.List} / {@link java.util.Map}). Providers must not assume a specific mod — they
 * read through generic interfaces (capabilities, Create's kinetic base, blockstate, NBT, ...), which
 * is what makes the computer work with Create and any addon out of the box.
 */
public interface ReadingProvider {
	void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out);
}
