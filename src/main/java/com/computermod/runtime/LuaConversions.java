package com.computermod.runtime;

import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Converts the plain-Java reading values (Number/Boolean/String/List/Map) into Lua values. */
public final class LuaConversions {

	@SuppressWarnings("unchecked")
	public static LuaValue toLua(Object value) {
		if (value == null)
			return LuaValue.NIL;
		if (value instanceof Boolean b)
			return LuaValue.valueOf(b);
		if (value instanceof Double || value instanceof Float)
			return LuaValue.valueOf(((Number) value).doubleValue());
		if (value instanceof Number n)
			return LuaValue.valueOf(n.doubleValue());
		if (value instanceof String s)
			return LuaValue.valueOf(s);
		if (value instanceof Map<?, ?> map) {
			LuaTable table = new LuaTable();
			for (Map.Entry<?, ?> entry : ((Map<Object, Object>) map).entrySet())
				table.set(String.valueOf(entry.getKey()), toLua(entry.getValue()));
			return table;
		}
		if (value instanceof List<?> list) {
			LuaTable table = new LuaTable();
			for (int i = 0; i < list.size(); i++)
				table.set(i + 1, toLua(list.get(i)));
			return table;
		}
		return LuaValue.valueOf(value.toString());
	}

	/** Convert a Lua value back into a plain Java object (for publishing onto the channel bus). */
	public static Object fromLua(LuaValue value) {
		if (value == null || value.isnil())
			return null;
		if (value.isboolean())
			return value.toboolean();
		if (value.isint())
			return value.toint();
		if (value.isnumber())
			return value.todouble();
		if (value.isstring())
			return value.tojstring();
		if (value.istable()) {
			org.luaj.vm2.LuaTable table = value.checktable();
			int len = table.length();
			boolean isArray = len > 0 && table.keyCount() == len;
			if (isArray) {
				java.util.List<Object> list = new java.util.ArrayList<>();
				for (int i = 1; i <= len; i++)
					list.add(fromLua(table.get(i)));
				return list;
			}
			java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
			for (LuaValue key : table.keys())
				map.put(key.tojstring(), fromLua(table.get(key)));
			return map;
		}
		return value.tojstring();
	}

	private LuaConversions() {}
}
