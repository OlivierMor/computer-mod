package com.computermod.channel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple server-wide, string-keyed value bus shared by computers, sensors and receivers. Values
 * are plain Java objects (Number / Boolean / String / List / Map) — far more expressive than the
 * 0-15 of vanilla/Create redstone links. Sensors publish each tick; readers read the latest value.
 *
 * <p>Transient runtime state (not saved); cleared on server stop.
 */
public final class ChannelBus {

	private static final ChannelBus INSTANCE = new ChannelBus();

	private final Map<String, Object> values = new ConcurrentHashMap<>();

	public static ChannelBus get() {
		return INSTANCE;
	}

	public void publish(String channel, Object value) {
		if (channel == null || channel.isEmpty())
			return;
		if (value == null)
			values.remove(channel);
		else
			values.put(channel, value);
	}

	public Object read(String channel) {
		return channel == null ? null : values.get(channel);
	}

	public Set<String> channels() {
		return values.keySet();
	}

	public void clear() {
		values.clear();
	}

	private ChannelBus() {}
}
