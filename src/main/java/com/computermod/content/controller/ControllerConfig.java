package com.computermod.content.controller;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The layout stored on a Channel Controller item: an ordered list of {@link Binding}s the player has
 * defined. Persisted as a data component, so it survives in the item stack and syncs to the client.
 */
public record ControllerConfig(List<Binding> bindings) {

	public static final ControllerConfig EMPTY = new ControllerConfig(List.of());

	public static final Codec<ControllerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Binding.CODEC.listOf().fieldOf("bindings").forGetter(ControllerConfig::bindings))
		.apply(instance, ControllerConfig::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ControllerConfig> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.<RegistryFriendlyByteBuf, Binding, List<Binding>>collection(ArrayList::new, Binding.STREAM_CODEC),
		ControllerConfig::bindings,
		ControllerConfig::new);

	public ControllerConfig {
		bindings = List.copyOf(bindings);
	}

	/** True if any binding targets a non-empty channel matching {@code channel}. */
	public boolean controls(String channel) {
		if (channel == null || channel.isEmpty())
			return false;
		for (Binding b : bindings)
			if (channel.equals(b.channel()))
				return true;
		return false;
	}
}
