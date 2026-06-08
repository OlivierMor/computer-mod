package com.computermod.registry;

import com.computermod.ComputerMod;
import com.computermod.content.controller.ControllerConfig;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
	public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ComputerMod.MODID);

	/** The binding layout stored on a Channel Controller item. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<ControllerConfig>> CONTROLLER_CONFIG =
		COMPONENTS.register("controller_config", () -> DataComponentType.<ControllerConfig>builder()
			.persistent(ControllerConfig.CODEC)
			.networkSynchronized(ControllerConfig.STREAM_CODEC)
			.build());

	private ModDataComponents() {}
}
