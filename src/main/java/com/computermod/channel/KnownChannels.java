package com.computermod.channel;

import java.util.List;

/**
 * Client-side cache of the channel names currently live on the server's {@link ChannelBus}, kept in
 * sync via the channel-list packet. Used by the GUIs to suggest existing channels and flag whether a
 * typed name already exists. Holds no client-only types, so it is safe to touch from packet handlers.
 */
public final class KnownChannels {

	private static volatile List<String> channels = List.of();

	public static void set(List<String> names) {
		channels = List.copyOf(names);
	}

	public static List<String> get() {
		return channels;
	}

	public static boolean exists(String name) {
		if (name == null || name.isEmpty())
			return false;
		for (String c : channels)
			if (c.equalsIgnoreCase(name))
				return true;
		return false;
	}

	private KnownChannels() {}
}
