package com.computermod.network;

import com.computermod.ComputerMod;
import com.computermod.channel.ChannelBus;
import com.computermod.content.controller.ChannelControllerItem;
import com.computermod.content.controller.ControllerConfig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: a held {@link ChannelControllerItem} publishes a player-driven value onto a channel.
 * The value is a tagged union — either a boolean (digital binding) or a number (analog binding).
 *
 * <p>The server only honours the publish if the player is actually holding a controller whose config
 * targets that channel, so a rogue client can't write to arbitrary channels.
 */
public record SetChannelValueC2S(String channel, boolean numeric, double number, boolean flag)
	implements CustomPacketPayload {

	public static final Type<SetChannelValueC2S> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "set_channel_value"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetChannelValueC2S> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SetChannelValueC2S::channel,
		ByteBufCodecs.BOOL, SetChannelValueC2S::numeric,
		ByteBufCodecs.DOUBLE, SetChannelValueC2S::number,
		ByteBufCodecs.BOOL, SetChannelValueC2S::flag,
		SetChannelValueC2S::new);

	public static SetChannelValueC2S ofBool(String channel, boolean value) {
		return new SetChannelValueC2S(channel, false, 0.0, value);
	}

	public static SetChannelValueC2S ofNumber(String channel, double value) {
		return new SetChannelValueC2S(channel, true, value, false);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SetChannelValueC2S msg, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			Player player = ctx.player();
			if (player == null || msg.channel().isEmpty())
				return;
			if (!holdsControllerFor(player, msg.channel()))
				return;
			ChannelBus.get().publish(msg.channel(), msg.numeric() ? msg.number() : msg.flag());
		});
	}

	private static boolean holdsControllerFor(Player player, String channel) {
		for (ItemStack stack : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() }) {
			if (stack.getItem() instanceof ChannelControllerItem) {
				ControllerConfig config = ChannelControllerItem.getConfig(stack);
				if (config.controls(channel))
					return true;
			}
		}
		return false;
	}
}
