package com.computermod;

import com.computermod.client.ChannelScreen;
import com.computermod.client.ComputerScreen;
import com.computermod.content.computer.ComputerRenderer;
import com.computermod.registry.ModBlockEntities;
import com.computermod.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
}
