package com.computermod.client.controller;

import com.computermod.ComputerMod;
import com.computermod.client.ControllerConfigScreen;
import com.computermod.content.controller.Binding;
import com.computermod.content.controller.ChannelControllerItem;
import com.computermod.content.controller.ControllerConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Intercepts the attack / use keys for a held controller (left-click → configure, right-click →
 * toggle operate), and draws the operate-mode HUD with each binding's live value.
 */
@EventBusSubscriber(modid = ComputerMod.MODID, value = Dist.CLIENT)
public final class ControllerInput {

	private static final int COL_ON = 0x6FE36F;
	private static final int COL_OFF = 0x6B7480;
	private static final int COL_ANALOG = 0x6FA8FF;
	private static final int COL_KEY = 0xC8D0DA;

	@SubscribeEvent
	static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null)
			return;
		ItemStack stack = ControllerHandler.heldController(player);
		if (!(stack.getItem() instanceof ChannelControllerItem))
			return;

		if (event.isAttack()) {
			InteractionHand hand = ControllerHandler.controllerHand(player);
			mc.setScreen(new ControllerConfigScreen(hand, ChannelControllerItem.getConfig(stack)));
			event.setSwingHand(false);
			event.setCanceled(true);
		} else if (event.isUseItem()) {
			ControllerHandler.toggle(ControllerHandler.controllerHand(player));
			event.setSwingHand(false);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	static void onRenderHud(RenderGuiEvent.Post event) {
		if (!ControllerHandler.isActive())
			return;
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null)
			return;
		ItemStack stack = ControllerHandler.heldController(player);
		if (!(stack.getItem() instanceof ChannelControllerItem))
			return;

		ControllerConfig config = ChannelControllerItem.getConfig(stack);
		GuiGraphics g = event.getGuiGraphics();
		Font font = mc.font;

		int x = 6;
		int y = 6;
		g.drawString(font, Component.translatable("computermod.controller.active"), x, y, COL_ON, false);
		y += 12;
		for (Binding b : config.bindings()) {
			if (!b.isValid())
				continue;
			Object value = ControllerHandler.liveValue(b.channel());
			String shown = format(value);
			boolean on = isOn(value);
			int color = b.isScroll() ? COL_ANALOG : (on ? COL_ON : COL_OFF);
			String line = ControllerHandler.label(b.key()) + " → " + b.channel() + " = " + shown;
			g.drawString(font, line, x, y, color, false);
			y += 10;
		}
	}

	private static boolean isOn(Object value) {
		if (value instanceof Boolean bool)
			return bool;
		if (value instanceof Number num)
			return num.doubleValue() != 0;
		return false;
	}

	private static String format(Object value) {
		if (value == null)
			return "—";
		if (value instanceof Boolean bool)
			return bool ? "ON" : "off";
		if (value instanceof Number num) {
			double d = num.doubleValue();
			return d == Math.rint(d) ? Long.toString((long) d) : String.format("%.2f", d);
		}
		return value.toString();
	}

	private ControllerInput() {}
}
