package com.computermod;

import com.computermod.client.ChannelScreen;
import com.computermod.client.ComputerScreen;
import com.computermod.client.controller.ControllerHandler;
import com.computermod.content.channel.ReceiverBlock;
import com.computermod.content.computer.ComputerRenderer;
import com.computermod.registry.ModBlockEntities;
import com.computermod.registry.ModBlocks;
import com.computermod.registry.ModItems;
import com.computermod.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point. Registers the computer screen and the config screen.
 */
@Mod(value = ComputerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ComputerMod.MODID, value = Dist.CLIENT)
public class ComputerModClient {
	public ComputerModClient(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
	}

	@SubscribeEvent
	static void onRegisterScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenus.COMPUTER.get(), ComputerScreen::new);
		event.register(ModMenus.CHANNEL.get(), ChannelScreen::new);
	}

	@SubscribeEvent
	static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(ModBlockEntities.COMPUTER.get(), ComputerRenderer::new);
	}

	@SubscribeEvent
	static void onClientSetup(FMLClientSetupEvent event) {
		// Drive the controller's model swap: 1 while this client is operating its own controller, else 0.
		// The base item model overrides to the "powered" model (raised in first person, like Create's
		// Linked Controller) when this reads >= 1. Guarded to the local player so another player's held
		// controller — whose operate state we don't sync — never picks up our client-side flag.
		event.enqueueWork(() -> ItemProperties.register(ModItems.CONTROLLER.get(),
			ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "active"),
			(stack, level, entity, seed) ->
				ControllerHandler.isActive() && entity == Minecraft.getInstance().player ? 1.0f : 0.0f));
	}

	@SubscribeEvent
	static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
		// Dim the receiver's LED face by its GLOW (0-15) so the indicator visibly brightens with the
		// signal in any ambient light. A grey multiplier keeps the texture's green hue and just scales
		// its brightness; full strength -> white (unchanged), no signal -> dark.
		event.register((state, level, pos, tintIndex) -> {
			float f = 0.15f + 0.85f * (state.getValue(ReceiverBlock.GLOW) / 15f);
			int v = Mth.clamp(Math.round(255 * f), 0, 255);
			return 0xFF000000 | (v << 16) | (v << 8) | v;
		}, ModBlocks.RECEIVER.get());
	}
}
