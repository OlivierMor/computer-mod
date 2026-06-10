package com.computermod.channel;

import java.util.List;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One channel as shown in client GUIs: its name, how many endpoints of each kind currently use it
 * (from the {@link ChannelDirectory}), and a short preview of the latest value on the bus. Synced
 * server to client in the channel-list packet.
 */
public record ChannelEntry(String name, int sensors, int computers, int receivers, int controllers,
	String preview, boolean hasValue) {

	public static final StreamCodec<RegistryFriendlyByteBuf, ChannelEntry> STREAM_CODEC = StreamCodec.of(
		(buf, e) -> {
			buf.writeUtf(e.name());
			buf.writeVarInt(e.sensors());
			buf.writeVarInt(e.computers());
			buf.writeVarInt(e.receivers());
			buf.writeVarInt(e.controllers());
			buf.writeUtf(e.preview());
			buf.writeBoolean(e.hasValue());
		},
		buf -> new ChannelEntry(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), buf.readUtf(), buf.readBoolean()));

	/** Build an entry for {@code name} from the live bus value and directory usage counts. */
	public static ChannelEntry of(String name, Object value, ChannelDirectory.Usage usage) {
		return new ChannelEntry(name, usage.sensors(), usage.computers(), usage.receivers(),
			usage.controllers(), preview(value), value != null);
	}

	/** Short, single-line preview of a channel value, e.g. {@code 12.5}, {@code "on"}, {@code {7 fields}}. */
	public static String preview(Object value) {
		if (value == null)
			return "";
		String text;
		if (value instanceof Map<?, ?> map)
			text = "{" + map.size() + (map.size() == 1 ? " field}" : " fields}");
		else if (value instanceof List<?> list)
			text = "[" + list.size() + (list.size() == 1 ? " entry]" : " entries]");
		else if (value instanceof String s)
			text = "\"" + s + "\"";
		else if (value instanceof Double d && d == Math.floor(d) && !d.isInfinite())
			text = String.valueOf(d.longValue());
		else
			text = String.valueOf(value);
		return text.length() > 28 ? text.substring(0, 27) + "…" : text;
	}
}
