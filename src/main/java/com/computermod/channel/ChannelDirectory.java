package com.computermod.channel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide registry of <em>who uses which channel</em>. The {@link ChannelBus} only stores the
 * latest value per channel; this directory remembers the endpoints behind it — every sensor,
 * computer, receiver and controller that recently published to or read from a channel — so GUIs can
 * show where a channel's traffic comes from and where it goes, including channels that only ever
 * flow between two computers.
 *
 * <p>Endpoints assert themselves cheaply ("touch") whenever they interact with a channel: sensors
 * and receivers once per tick while configured, computers on every {@code emit}/{@code channel}
 * call, controllers on every input packet. Entries expire after {@link #TTL_TICKS} without a touch,
 * and blocks/computers remove themselves eagerly via {@link #forget} when broken or powered off.
 *
 * <p>Thread-safe: computers touch from their Lua worker threads.
 */
public final class ChannelDirectory {

	/** How long (in game ticks) an endpoint stays listed after its last interaction. */
	private static final long TTL_TICKS = 1200; // one minute

	public enum Kind { SENSOR, COMPUTER, RECEIVER, CONTROLLER }

	/** Aggregated per-channel usage counts handed to the sync packet. */
	public record Usage(int sensors, int computers, int receivers, int controllers) {
		public static final Usage NONE = new Usage(0, 0, 0, 0);
	}

	/** One concrete user of a channel: a block position or a player id, qualified by kind. */
	private record Endpoint(Kind kind, Object key) {}

	private static final ChannelDirectory INSTANCE = new ChannelDirectory();

	/** channel -> (endpoint -> last game time it touched the channel). */
	private final Map<String, Map<Endpoint, Long>> uses = new ConcurrentHashMap<>();

	public static ChannelDirectory get() {
		return INSTANCE;
	}

	/** Record that {@code key} (a BlockPos or player UUID) just used {@code channel} as {@code kind}. */
	public void touch(String channel, Kind kind, Object key, long gameTime) {
		if (channel == null || channel.isEmpty() || key == null)
			return;
		uses.computeIfAbsent(channel, c -> new ConcurrentHashMap<>())
			.put(new Endpoint(kind, key), gameTime);
	}

	/** Drop every entry belonging to {@code key} — call when a block is broken or a computer halts. */
	public void forget(Object key) {
		if (key == null)
			return;
		for (Iterator<Map.Entry<String, Map<Endpoint, Long>>> it = uses.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Map<Endpoint, Long>> entry = it.next();
			entry.getValue().keySet().removeIf(e -> key.equals(e.key()));
			if (entry.getValue().isEmpty())
				it.remove();
		}
	}

	/** Current usage per channel, pruning entries that have not touched within the TTL. */
	public Map<String, Usage> snapshot(long now) {
		Map<String, Usage> out = new HashMap<>();
		for (Iterator<Map.Entry<String, Map<Endpoint, Long>>> it = uses.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Map<Endpoint, Long>> entry = it.next();
			Map<Endpoint, Long> endpoints = entry.getValue();
			endpoints.values().removeIf(last -> now - last > TTL_TICKS);
			if (endpoints.isEmpty()) {
				it.remove();
				continue;
			}
			int sensors = 0, computers = 0, receivers = 0, controllers = 0;
			for (Endpoint e : endpoints.keySet()) {
				switch (e.kind()) {
					case SENSOR -> sensors++;
					case COMPUTER -> computers++;
					case RECEIVER -> receivers++;
					case CONTROLLER -> controllers++;
				}
			}
			out.put(entry.getKey(), new Usage(sensors, computers, receivers, controllers));
		}
		return out;
	}

	public void clear() {
		uses.clear();
	}

	private ChannelDirectory() {}
}
