package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.content.computer.ComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: flash {@code source} onto the computer at {@code pos} (persistent program memory). */
public record FlashProgramC2S(BlockPos pos, String source) implements CustomPacketPayload {

	public static final Type<FlashProgramC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "flash_program"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FlashProgramC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, FlashProgramC2S::pos,
		ByteBufCodecs.STRING_UTF8, FlashProgramC2S::source,
		FlashProgramC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FlashProgramC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player.distanceToSqr(msg.pos().getCenter()) > 64.0)
				return;
			if (player.level().getBlockEntity(msg.pos()) instanceof ComputerBlockEntity computer)
				computer.flashProgram(msg.source());
		});
	}
}
