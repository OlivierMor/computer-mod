package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.content.channel.ChannelConfigurable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: set the channel on a sensor or receiver. */
public record ConfigureChannelC2S(BlockPos pos, String channel) implements CustomPacketPayload {

	public static final Type<ConfigureChannelC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "configure_channel"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureChannelC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ConfigureChannelC2S::pos,
		ByteBufCodecs.STRING_UTF8, ConfigureChannelC2S::channel,
		ConfigureChannelC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ConfigureChannelC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player.distanceToSqr(msg.pos().getCenter()) > 64.0)
				return;
			if (player.level().getBlockEntity(msg.pos()) instanceof ChannelConfigurable configurable)
				configurable.configure(msg.channel());
		});
	}
}
