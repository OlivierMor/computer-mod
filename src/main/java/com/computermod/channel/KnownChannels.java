package com.computermod.channel;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the channels currently known to the server (with usage counts and value
 * previews), kept in sync via the channel-list packet. Used by the GUIs to suggest channels and to
 * flag whether a typed name already exists. Holds no client-only types, so it is safe to touch from
 * packet handlers.
 */
public final class KnownChannels {

	private static volatile List<ChannelEntry> channels = List.of();

	public static void set(List<ChannelEntry> entries) {
		channels = List.copyOf(entries);
	}

	public static List<ChannelEntry> get() {
		return channels;
	}

	public static List<String> names() {
		List<String> out = new ArrayList<>(channels.size());
		for (ChannelEntry e : channels)
			out.add(e.name());
		return out;
	}

	public static ChannelEntry entry(String name) {
		if (name == null || name.isEmpty())
			return null;
		for (ChannelEntry e : channels)
			if (e.name().equalsIgnoreCase(name))
				return e;
		return null;
	}

	public static boolean exists(String name) {
		return entry(name) != null;
	}

	private KnownChannels() {}
}
