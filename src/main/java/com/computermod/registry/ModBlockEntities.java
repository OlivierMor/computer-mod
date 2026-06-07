package com.computermod.registry;

import com.computermod.ComputerMod;
import com.computermod.content.channel.ReceiverBlockEntity;
import com.computermod.content.channel.SensorBlockEntity;
import com.computermod.content.computer.ComputerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ComputerMod.MODID);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER =
		BLOCK_ENTITIES.register("computer", () -> BlockEntityType.Builder
			.of((pos, state) -> new ComputerBlockEntity(ModBlockEntities.COMPUTER.get(), pos, state), ModBlocks.COMPUTER.get())
			.build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SensorBlockEntity>> SENSOR =
		BLOCK_ENTITIES.register("sensor", () -> BlockEntityType.Builder
			.of((pos, state) -> new SensorBlockEntity(ModBlockEntities.SENSOR.get(), pos, state), ModBlocks.SENSOR.get())
			.build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReceiverBlockEntity>> RECEIVER =
		BLOCK_ENTITIES.register("receiver", () -> BlockEntityType.Builder
			.of((pos, state) -> new ReceiverBlockEntity(ModBlockEntities.RECEIVER.get(), pos, state), ModBlocks.RECEIVER.get())
			.build(null));

	private ModBlockEntities() {}
}
