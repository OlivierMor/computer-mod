package com.computermod.content.computer;

import com.computermod.registry.ModBlocks;
import com.computermod.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Slot-less menu for the computer. Exists purely to open {@code ComputerScreen} with a synced
 * reference to the {@link ComputerBlockEntity} and standard open/close lifecycle.
 */
public class ComputerMenu extends AbstractContainerMenu {

	private final ComputerBlockEntity blockEntity;
	private final ContainerLevelAccess access;

	/** Client-side constructor: reads the block position from the open packet. */
	public ComputerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
		this(id, inv, resolve(inv, buf.readBlockPos()));
	}

	/** Server-side / shared constructor. */
	public ComputerMenu(int id, Inventory inv, ComputerBlockEntity be) {
		super(ModMenus.COMPUTER.get(), id);
		this.blockEntity = be;
		this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
	}

	private static ComputerBlockEntity resolve(Inventory inv, BlockPos pos) {
		BlockEntity be = inv.player.level().getBlockEntity(pos);
		if (be instanceof ComputerBlockEntity c)
			return c;
		throw new IllegalStateException("No computer block entity at " + pos);
	}

	public ComputerBlockEntity getBlockEntity() {
		return blockEntity;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModBlocks.COMPUTER.get());
	}
}
