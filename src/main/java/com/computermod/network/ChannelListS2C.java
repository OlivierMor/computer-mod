package com.computermod.network;

import java.util.ArrayList;
import java.util.List;

import com.computermod.ComputerMod;
import com.computermod.channel.ChannelEntry;
import com.computermod.channel.KnownChannels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: the channels currently known to the server (live values on the bus plus every
 * endpoint registered in the directory), with usage counts and value previews. Cached into
 * {@link KnownChannels} for the GUIs.
 */
public record ChannelListS2C(List<ChannelEntry> channels) implements CustomPacketPayload {

	public static final Type<ChannelListS2C> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "channel_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChannelListS2C> CODEC = StreamCodec.composite(
		ByteBufCodecs.<RegistryFriendlyByteBuf, ChannelEntry, List<ChannelEntry>>collection(ArrayList::new,
			ChannelEntry.STREAM_CODEC),
		ChannelListS2C::channels,
		ChannelListS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ChannelListS2C msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> KnownChannels.set(msg.channels()));
	}
}
