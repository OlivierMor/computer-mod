package com.computermod.registry;

import com.computermod.ComputerMod;
import com.computermod.content.channel.ChannelMenu;
import com.computermod.content.computer.ComputerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
	public static final DeferredRegister<MenuType<?>> MENUS =
		DeferredRegister.create(Registries.MENU, ComputerMod.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER =
		MENUS.register("computer", () -> IMenuTypeExtension.create(ComputerMenu::new));

	public static final DeferredHolder<MenuType<?>, MenuType<ChannelMenu>> CHANNEL =
		MENUS.register("channel", () -> IMenuTypeExtension.create(ChannelMenu::new));

	private ModMenus() {}
}
