package com.computermod.reading.provider;

import java.util.LinkedHashMap;
import java.util.Map;

import com.computermod.reading.ReadingProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

/** Exposes every blockstate property and the block's comparator (analog) output. */
public class BlockStateReadingProvider implements ReadingProvider {
	@Override
	public void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out) {
		Map<String, Object> props = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties()) {
			Object value = state.getValue(property);
			if (value instanceof Boolean || value instanceof Number)
				props.put(property.getName(), value);
			else
				props.put(property.getName(), value.toString());
		}
		out.put("state", props);

		if (state.hasAnalogOutputSignal())
			out.put("analog_output", state.getAnalogOutputSignal(level, pos));
	}
}
