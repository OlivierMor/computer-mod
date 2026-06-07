package com.computermod.reading.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.computermod.reading.ReadingProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

/**
 * Reads the universal NeoForge block capabilities — item inventories, fluid tanks and Forge Energy.
 * Works for any mod that exposes them, with no mod-specific code.
 */
public class CapabilityReadingProvider implements ReadingProvider {
	@Override
	public void collect(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Map<String, Object> out) {
		IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
		if (items != null) {
			List<Object> contents = new ArrayList<>();
			int total = 0;
			for (int slot = 0; slot < items.getSlots(); slot++) {
				ItemStack stack = items.getStackInSlot(slot);
				if (stack.isEmpty())
					continue;
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("slot", slot);
				entry.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				entry.put("count", stack.getCount());
				contents.add(entry);
				total += stack.getCount();
			}
			out.put("items", contents);
			out.put("item_count", total);
			out.put("slots", items.getSlots());
		}

		IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
		if (fluids != null) {
			List<Object> tanks = new ArrayList<>();
			for (int i = 0; i < fluids.getTanks(); i++) {
				FluidStack fluid = fluids.getFluidInTank(i);
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("fluid", fluid.isEmpty() ? "minecraft:empty"
					: BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
				entry.put("amount", fluid.getAmount());
				entry.put("capacity", fluids.getTankCapacity(i));
				tanks.add(entry);
			}
			out.put("tanks", tanks);
		}

		IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
		if (energy != null) {
			out.put("energy", energy.getEnergyStored());
			out.put("energy_capacity", energy.getMaxEnergyStored());
		}
	}
}
