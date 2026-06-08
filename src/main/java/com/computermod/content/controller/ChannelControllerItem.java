package com.computermod.content.controller;

import java.util.List;

import com.computermod.registry.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A handheld "Channel Controller": while held you toggle an operate mode (right-click) in which the
 * inputs you bound drive their channels, and unbound inputs pass through to normal play. Left-click
 * opens the configuration screen. All of that interaction lives client-side (see ControllerHandler);
 * the item itself only carries the {@link ControllerConfig} component.
 */
public class ChannelControllerItem extends Item {

	public ChannelControllerItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static ControllerConfig getConfig(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.CONTROLLER_CONFIG.get(), ControllerConfig.EMPTY);
	}

	public static void setConfig(ItemStack stack, ControllerConfig config) {
		stack.set(ModDataComponents.CONTROLLER_CONFIG.get(), config);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		int count = getConfig(stack).bindings().size();
		tooltip.add(Component.translatable("item.computermod.channel_controller.bindings", count)
			.withStyle(net.minecraft.ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.computermod.channel_controller.hint")
			.withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}
}
