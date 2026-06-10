package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.content.channel.ReceiverBlockEntity;
import com.computermod.content.channel.SensorBlockEntity;
import com.computermod.content.computer.ComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: set the chunk-loading settings of a computer, sensor, or receiver: whether it
 * keeps its chunks loaded, and the radius of the loaded area. The server clamps the radius.
 */
public record ConfigureChunkLoadingC2S(BlockPos pos, boolean keepLoaded, int radius)
	implements CustomPacketPayload {

	public static final Type<ConfigureChunkLoadingC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "configure_chunk_loading"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureChunkLoadingC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ConfigureChunkLoadingC2S::pos,
		ByteBufCodecs.BOOL, ConfigureChunkLoadingC2S::keepLoaded,
		ByteBufCodecs.VAR_INT, ConfigureChunkLoadingC2S::radius,
		ConfigureChunkLoadingC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ConfigureChunkLoadingC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player.distanceToSqr(msg.pos().getCenter()) > 64.0)
				return;
			BlockEntity be = player.level().getBlockEntity(msg.pos());
			if (be instanceof ComputerBlockEntity computer)
				computer.configureChunkLoading(msg.keepLoaded(), msg.radius());
			else if (be instanceof SensorBlockEntity sensor)
				sensor.configureChunkLoading(msg.keepLoaded(), msg.radius());
			else if (be instanceof ReceiverBlockEntity receiver)
				receiver.configureChunkLoading(msg.keepLoaded(), msg.radius());
		});
	}
}
