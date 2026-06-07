package com.computermod.reading.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.computermod.reading.ReadingProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Universal fallback: exposes the entire block entity NBT as a structured table. This lets the
 * computer read state that an addon persists even when it offers no other API.
 */
public class NbtReadingProvider implements ReadingProvider {
	@Override
	public void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out) {
		if (be == null)
			return;
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		out.put("nbt", convert(tag));
	}

	private static Object convert(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (String key : compound.getAllKeys())
				map.put(key, convert(compound.get(key)));
			return map;
		}
		if (tag instanceof CollectionTag<?> list) {
			List<Object> values = new ArrayList<>();
			for (Tag element : list)
				values.add(convert(element));
			return values;
		}
		if (tag instanceof NumericTag numeric) {
			byte type = numeric.getId();
			if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE)
				return numeric.getAsDouble();
			return numeric.getAsLong();
		}
		return tag == null ? null : tag.getAsString();
	}
}
