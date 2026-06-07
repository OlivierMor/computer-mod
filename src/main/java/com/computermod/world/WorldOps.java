package com.computermod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Universal, capability-based world manipulation used by the computer's output API. Must be called
 * on the server thread. Works for any block exposing the standard NeoForge capabilities.
 */
public final class WorldOps {

	/** Move up to {@code max} items from the inventory on {@code fromPos} into {@code toPos}. Returns moved count. */
	public static int moveItems(Level level, BlockPos fromPos, Direction fromFace, BlockPos toPos, Direction toFace, int max) {
		IItemHandler from = level.getCapability(Capabilities.ItemHandler.BLOCK, fromPos, fromFace);
		IItemHandler to = level.getCapability(Capabilities.ItemHandler.BLOCK, toPos, toFace);
		if (from == null || to == null || max <= 0)
			return 0;

		int moved = 0;
		for (int slot = 0; slot < from.getSlots() && moved < max; slot++) {
			ItemStack inSlot = from.getStackInSlot(slot);
			if (inSlot.isEmpty())
				continue;
			int want = Math.min(inSlot.getCount(), max - moved);
			ItemStack extracted = from.extractItem(slot, want, true);
			if (extracted.isEmpty())
				continue;
			ItemStack leftover = ItemHandlerHelper.insertItem(to, extracted, false);
			int accepted = extracted.getCount() - leftover.getCount();
			if (accepted > 0) {
				from.extractItem(slot, accepted, false);
				moved += accepted;
			}
		}
		return moved;
	}

	/** Move up to {@code maxMb} mB of fluid from {@code fromPos} into {@code toPos}. Returns moved amount. */
	public static int moveFluid(Level level, BlockPos fromPos, Direction fromFace, BlockPos toPos, Direction toFace, int maxMb) {
		IFluidHandler from = level.getCapability(Capabilities.FluidHandler.BLOCK, fromPos, fromFace);
		IFluidHandler to = level.getCapability(Capabilities.FluidHandler.BLOCK, toPos, toFace);
		if (from == null || to == null || maxMb <= 0)
			return 0;

		FluidStack drainable = from.drain(maxMb, IFluidHandler.FluidAction.SIMULATE);
		if (drainable.isEmpty())
			return 0;
		int filled = to.fill(drainable, IFluidHandler.FluidAction.EXECUTE);
		if (filled <= 0)
			return 0;
		from.drain(filled, IFluidHandler.FluidAction.EXECUTE);
		return filled;
	}

	private WorldOps() {}
}
