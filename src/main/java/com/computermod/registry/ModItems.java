package com.computermod.registry;

import com.computermod.ComputerMod;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ComputerMod.MODID);

	public static final DeferredItem<BlockItem> COMPUTER = ITEMS.registerSimpleBlockItem(ModBlocks.COMPUTER);
	public static final DeferredItem<BlockItem> SENSOR = ITEMS.registerSimpleBlockItem(ModBlocks.SENSOR);
	public static final DeferredItem<BlockItem> RECEIVER = ITEMS.registerSimpleBlockItem(ModBlocks.RECEIVER);

	private ModItems() {}
}
