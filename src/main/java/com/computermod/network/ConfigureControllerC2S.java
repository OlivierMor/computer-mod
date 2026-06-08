package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.content.controller.ChannelControllerItem;
import com.computermod.content.controller.ControllerConfig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: save an edited {@link ControllerConfig} onto the controller held in {@code hand}. */
public record ConfigureControllerC2S(InteractionHand hand, ControllerConfig config) implements CustomPacketPayload {

	public static final Type<ConfigureControllerC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "configure_controller"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureControllerC2S> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
		ConfigureControllerC2S::hand,
		ControllerConfig.STREAM_CODEC, ConfigureControllerC2S::config,
		ConfigureControllerC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ConfigureControllerC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player == null)
				return;
			ItemStack stack = player.getItemInHand(msg.hand());
			if (stack.getItem() instanceof ChannelControllerItem)
				ChannelControllerItem.setConfig(stack, msg.config());
		});
	}
}
