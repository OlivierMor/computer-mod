package com.computermod.registry;

import com.computermod.ComputerMod;
import com.computermod.content.channel.ReceiverBlock;
import com.computermod.content.channel.SensorBlock;
import com.computermod.content.computer.ComputerBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ComputerMod.MODID);

	public static final DeferredBlock<ComputerBlock> COMPUTER = BLOCKS.registerBlock(
		"computer",
		ComputerBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY)
			.requiresCorrectToolForDrops()
			.strength(3.0f, 6.0f)
			.sound(SoundType.NETHERITE_BLOCK)
	);

	public static final DeferredBlock<SensorBlock> SENSOR = BLOCKS.registerBlock(
		"sensor",
		SensorBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE)
			.requiresCorrectToolForDrops()
			.strength(2.5f, 6.0f)
			.sound(SoundType.NETHERITE_BLOCK)
	);

	public static final DeferredBlock<ReceiverBlock> RECEIVER = BLOCKS.registerBlock(
		"receiver",
		ReceiverBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.requiresCorrectToolForDrops()
			.strength(2.5f, 6.0f)
			.sound(SoundType.NETHERITE_BLOCK)
	);

	private ModBlocks() {}
}
