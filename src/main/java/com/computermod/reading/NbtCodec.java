package com.computermod.reading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Converts between the plain-Java value model (Number / Boolean / String / List / Map) used by the
 * Lua bridge and Minecraft NBT, for persisting the computer's disk store.
 */
public final class NbtCodec {

	public static Tag toTag(Object value) {
		if (value instanceof Boolean b)
			return ByteTag.valueOf(b);
		if (value instanceof Double || value instanceof Float)
			return DoubleTag.valueOf(((Number) value).doubleValue());
		if (value instanceof Long l)
			return LongTag.valueOf(l);
		if (value instanceof Number n)
			return IntTag.valueOf(n.intValue());
		if (value instanceof String s)
			return StringTag.valueOf(s);
		if (value instanceof Map<?, ?> map) {
			CompoundTag compound = new CompoundTag();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				Tag t = toTag(e.getValue());
				if (t != null)
					compound.put(String.valueOf(e.getKey()), t);
			}
			return compound;
		}
		if (value instanceof List<?> list) {
			ListTag listTag = new ListTag();
			for (Object o : list) {
				Tag t = toTag(o);
				if (t != null)
					listTag.add(t);
			}
			return listTag;
		}
		return value == null ? null : StringTag.valueOf(value.toString());
	}

	public static Object fromTag(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (String key : compound.getAllKeys())
				map.put(key, fromTag(compound.get(key)));
			return map;
		}
		if (tag instanceof CollectionTag<?> list) {
			List<Object> values = new ArrayList<>();
			for (Tag element : list)
				values.add(fromTag(element));
			return values;
		}
		if (tag instanceof NumericTag numeric) {
			byte type = numeric.getId();
			if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE)
				return numeric.getAsDouble();
			if (type == Tag.TAG_BYTE)
				return numeric.getAsByte() != 0; // we only store booleans as bytes
			return numeric.getAsLong();
		}
		return tag == null ? null : tag.getAsString();
	}

	private NbtCodec() {}
}
