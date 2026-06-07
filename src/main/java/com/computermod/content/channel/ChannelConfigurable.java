package com.computermod.content.channel;

import java.util.List;

/** Shared config contract for the sensor and receiver blocks (set their channel). */
public interface ChannelConfigurable {
	String getChannelName();

	void configure(String channel);

	/** Live tree of what the sensor currently sees, depth-first (empty for receivers). */
	default List<SensorBlockEntity.Node> getReadings() {
		return List.of();
	}
}
