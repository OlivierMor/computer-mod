package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.content.computer.ComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: clear the console of the computer at {@code pos} (the program keeps running). */
public record ClearTerminalC2S(BlockPos pos) implements CustomPacketPayload {

	public static final Type<ClearTerminalC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "clear_terminal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClearTerminalC2S> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ClearTerminalC2S::pos,
		ClearTerminalC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ClearTerminalC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player.distanceToSqr(msg.pos().getCenter()) > 64.0)
				return;
			if (player.level().getBlockEntity(msg.pos()) instanceof ComputerBlockEntity computer)
				computer.clearTerminal();
		});
	}
}
