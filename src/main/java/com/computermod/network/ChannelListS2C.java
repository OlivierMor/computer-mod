package com.computermod.network;

import java.util.ArrayList;
import java.util.List;

import com.computermod.ComputerMod;
import com.computermod.channel.KnownChannels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the current set of live channel names, cached into {@link KnownChannels}. */
public record ChannelListS2C(List<String> channels) implements CustomPacketPayload {

	public static final Type<ChannelListS2C> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "channel_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChannelListS2C> CODEC = StreamCodec.composite(
		ByteBufCodecs.<RegistryFriendlyByteBuf, String, List<String>>collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
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
