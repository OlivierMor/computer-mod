package com.computermod.registry;

import com.computermod.ComputerMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
	public static final DeferredRegister<CreativeModeTab> TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ComputerMod.MODID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup." + ComputerMod.MODID))
			.icon(() -> ModItems.COMPUTER.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(ModItems.COMPUTER.get());
				output.accept(ModItems.SENSOR.get());
				output.accept(ModItems.RECEIVER.get());
			})
			.build());

	private ModCreativeTabs() {}
}
