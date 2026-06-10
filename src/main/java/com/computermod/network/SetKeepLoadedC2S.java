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

/** Client -> server: toggle "Keep loaded" (chunk loading) on a computer, sensor, or receiver. */
public record SetKeepLoadedC2S(BlockPos pos, boolean keep) implements CustomPacketPayload {

	public static final Type<SetKeepLoadedC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "set_keep_loaded"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetKeepLoadedC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SetKeepLoadedC2S::pos,
		ByteBufCodecs.BOOL, SetKeepLoadedC2S::keep,
		SetKeepLoadedC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SetKeepLoadedC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player.distanceToSqr(msg.pos().getCenter()) > 64.0)
				return;
			BlockEntity be = player.level().getBlockEntity(msg.pos());
			if (be instanceof ComputerBlockEntity computer)
				computer.setKeepLoaded(msg.keep());
			else if (be instanceof SensorBlockEntity sensor)
				sensor.setKeepLoaded(msg.keep());
			else if (be instanceof ReceiverBlockEntity receiver)
				receiver.setKeepLoaded(msg.keep());
		});
	}
}
