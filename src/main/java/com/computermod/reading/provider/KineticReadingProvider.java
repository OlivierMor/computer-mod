package com.computermod.reading.provider;

import java.util.Map;

import com.computermod.reading.ReadingProvider;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/** Create kinetic readings (RPM, stress) for any block extending {@link KineticBlockEntity}. */
public class KineticReadingProvider implements ReadingProvider {
	@Override
	public void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out) {
		if (be instanceof KineticBlockEntity kinetic) {
			out.put("rpm", (double) kinetic.getSpeed());
			out.put("generated_rpm", (double) kinetic.getGeneratedSpeed());
			out.put("overstressed", kinetic.isOverStressed());
			out.put("is_kinetic", true);
		}
	}
}
