package com.computermod.runtime;

import java.util.List;

import com.computermod.content.computer.ComputerBlockEntity;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Installs the computer's custom Lua standard library. The computer is a pure "brain": it computes,
 * stores data, knows its own location, and communicates over wireless channels. It does NOT directly
 * sense or act on neighbouring blocks — that is what Sensor (input) and Receiver (output) blocks are
 * for. API: {@code print}, {@code getLocation()}, {@code disk.*}, {@code emit/channel/channels},
 * {@code sleep}.
 */
public final class ComputerApi {

	public static void install(Globals globals, LuaRuntime runtime, ComputerBlockEntity be) {
		Level level = be.getLevel();

		// getLocation(): the computer's own world coordinates (a built-in "GPS receiver").
		globals.set("getLocation", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				BlockPos p = be.getBlockPos();
				LuaTable t = new LuaTable();
				t.set("x", LuaValue.valueOf(p.getX()));
				t.set("y", LuaValue.valueOf(p.getY()));
				t.set("z", LuaValue.valueOf(p.getZ()));
				return t;
			}
		});

		// disk: a persistent key-value store that survives reboots and world reloads (unlike RAM).
		LuaTable disk = new LuaTable();
		disk.set("get", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue key) {
				String k = key.checkjstring();
				Object value = runtime.runOnMain(() -> be.diskGet(k));
				return LuaConversions.toLua(value);
			}
		});
		disk.set("set", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue key, LuaValue val) {
				String k = key.checkjstring();
				Object value = LuaConversions.fromLua(val);
				runtime.runOnMain(() -> {
					be.diskSet(k, value);
					return Boolean.TRUE;
				});
				return LuaValue.NIL;
			}
		});
		disk.set("delete", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue key) {
				String k = key.checkjstring();
				runtime.runOnMain(() -> {
					be.diskDelete(k);
					return Boolean.TRUE;
				});
				return LuaValue.NIL;
			}
		});
		disk.set("list", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				List<String> keys = runtime.runOnMain(be::diskList);
				LuaTable table = new LuaTable();
				int i = 1;
				for (String s : keys)
					table.set(i++, LuaValue.valueOf(s));
				return table;
			}
		});
		disk.set("clear", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				runtime.runOnMain(() -> {
					be.diskClear();
					return Boolean.TRUE;
				});
				return LuaValue.NIL;
			}
		});
		globals.set("disk", disk);

		// sleep(seconds): idle without burning clock budget.
		globals.set("sleep", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue arg) {
				double seconds = arg.checkdouble();
				// The game's time resolution is one tick (0.05s); any positive sleep waits >= 1 tick.
				int ticks = seconds <= 0 ? 0 : Math.max(1, (int) Math.round(seconds * 20.0));
				runtime.sleepTicks(ticks);
				return LuaValue.NIL;
			}
		});

		ChannelApi.install(globals, runtime, level);
	}

	private ComputerApi() {}
}
