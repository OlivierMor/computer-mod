package com.computermod.runtime;

import com.computermod.channel.ChannelBus;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import net.minecraft.world.level.Level;

/**
 * Lua bindings for the {@link ChannelBus}: {@code emit(channel, value)} publishes any value,
 * {@code channel(name)} reads the latest value, {@code channels()} lists active channel names.
 * The bus is a thread-safe map so these don't need to marshal to the server thread.
 */
public final class ChannelApi {

	public static void install(Globals globals, LuaRuntime runtime, Level level) {
		ChannelBus bus = ChannelBus.get();

		globals.set("emit", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				String channel = args.checkjstring(1);
				Object value = LuaConversions.fromLua(args.arg(2));
				bus.publish(channel, value);
				return LuaValue.NIL;
			}
		});

		globals.set("channel", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue arg) {
				return LuaConversions.toLua(bus.read(arg.checkjstring()));
			}
		});

		globals.set("channels", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				LuaTable table = new LuaTable();
				int i = 1;
				for (String name : bus.channels())
					table.set(i++, LuaValue.valueOf(name));
				return table;
			}
		});
	}

	private ChannelApi() {}
}
