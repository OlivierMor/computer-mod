package com.computermod;

import org.slf4j.Logger;

import com.computermod.content.computer.ComputerBlockEntity;
import com.computermod.network.ModNetwork;
import com.computermod.registry.ModBlockEntities;
import com.computermod.registry.ModBlocks;
import com.computermod.registry.ModCreativeTabs;
import com.computermod.registry.ModDataComponents;
import com.computermod.registry.ModItems;
import com.computermod.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Entry point for Computer Mod — a universal, programmable computer for Create.
 */
@Mod(ComputerMod.MODID)
public class ComputerMod {
	public static final String MODID = "computermod";
	public static final Logger LOGGER = LogUtils.getLogger();

	public ComputerMod(IEventBus modEventBus, ModContainer modContainer) {
		ModBlocks.BLOCKS.register(modEventBus);
		ModItems.ITEMS.register(modEventBus);
		ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
		ModMenus.MENUS.register(modEventBus);
		ModCreativeTabs.TABS.register(modEventBus);
		ModDataComponents.COMPONENTS.register(modEventBus);

		modEventBus.addListener(ModNetwork::register);
		modEventBus.addListener(ComputerBlockEntity::registerCapabilities);

		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
			(net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> com.computermod.channel.ChannelBus.get().clear());

		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

		LOGGER.info("Computer Mod loaded");
	}
}
