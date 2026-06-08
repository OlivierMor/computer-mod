package com.computermod.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.computermod.ComputerMod;
import com.computermod.channel.ChannelBus;

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
			if (ctx.player() instanceof ServerPlayer player) {
				List<String> names = new ArrayList<>(ChannelBus.get().channels());
				Collections.sort(names);
				PacketDistributor.sendToPlayer(player, new ChannelListS2C(names));
			}
		});
	}
}
