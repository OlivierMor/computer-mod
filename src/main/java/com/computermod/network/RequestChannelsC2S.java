package com.computermod.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.computermod.ComputerMod;
import com.computermod.channel.ChannelBus;
import com.computermod.channel.ChannelDirectory;
import com.computermod.channel.ChannelEntry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "tell me the channels that currently exist". Answered with {@link ChannelListS2C}. */
public record RequestChannelsC2S() implements CustomPacketPayload {

	public static final RequestChannelsC2S INSTANCE = new RequestChannelsC2S();

	public static final Type<RequestChannelsC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "request_channels"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RequestChannelsC2S> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(RequestChannelsC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer player))
				return;
			ChannelBus bus = ChannelBus.get();
			Map<String, ChannelDirectory.Usage> usage =
				ChannelDirectory.get().snapshot(player.serverLevel().getGameTime());
			// A channel is worth listing if it has a value OR a registered endpoint (e.g. a receiver
			// waiting on a channel nothing has published to yet). Sorted alphabetically, but names
			// that differ only in case stay distinct (the bus is case-sensitive).
			Set<String> nameSet = new HashSet<>(bus.channels());
			nameSet.addAll(usage.keySet());
			List<String> names = new ArrayList<>(nameSet);
			names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
			List<ChannelEntry> entries = new ArrayList<>(names.size());
			for (String name : names)
				entries.add(ChannelEntry.of(name, bus.read(name),
					usage.getOrDefault(name, ChannelDirectory.Usage.NONE)));
			PacketDistributor.sendToPlayer(player, new ChannelListS2C(entries));
		});
	}
}
