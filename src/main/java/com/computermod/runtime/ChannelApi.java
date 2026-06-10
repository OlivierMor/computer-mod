package com.computermod.runtime;

import com.computermod.channel.ChannelBus;
import com.computermod.channel.ChannelDirectory;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Lua bindings for the {@link ChannelBus}: {@code emit(channel, value)} publishes any value,
 * {@code channel(name)} reads the latest value, {@code channels()} lists active channel names.
 * The bus is a thread-safe map so these don't need to marshal to the server thread. Every call also
 * registers this computer in the {@link ChannelDirectory}, so GUIs can show which channels are in
 * use by computers (including computer-to-computer channels no sensor ever touches).
 */
public final class ChannelApi {

	public static void install(Globals globals, LuaRuntime runtime, Level level, BlockPos selfPos) {
		ChannelBus bus = ChannelBus.get();
		ChannelDirectory directory = ChannelDirectory.get();

		globals.set("emit", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				String channel = args.checkjstring(1);
				Object value = LuaConversions.fromLua(args.arg(2));
				bus.publish(channel, value);
				if (level != null)
					directory.touch(channel, ChannelDirectory.Kind.COMPUTER, selfPos, level.getGameTime());
				return LuaValue.NIL;
			}
		});

		globals.set("channel", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue arg) {
				String channel = arg.checkjstring();
				if (level != null)
					directory.touch(channel, ChannelDirectory.Kind.COMPUTER, selfPos, level.getGameTime());
				return LuaConversions.toLua(bus.read(channel));
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
