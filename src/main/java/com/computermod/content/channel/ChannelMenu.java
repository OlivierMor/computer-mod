package com.computermod.content.channel;

import com.computermod.registry.ModBlocks;
import com.computermod.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Slot-less config menu shared by the sensor and receiver blocks. */
public class ChannelMenu extends AbstractContainerMenu {

	private final BlockEntity blockEntity;
	private final ContainerLevelAccess access;

	public ChannelMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
		this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
	}

	public ChannelMenu(int id, Inventory inv, BlockEntity be) {
		super(ModMenus.CHANNEL.get(), id);
		this.blockEntity = be;
		this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
	}

	public BlockEntity getBlockEntity() {
		return blockEntity;
	}

	public ChannelConfigurable getConfigurable() {
		return blockEntity instanceof ChannelConfigurable c ? c : null;
	}

	public boolean isSensor() {
		return blockEntity instanceof SensorBlockEntity;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		Block block = isSensor() ? ModBlocks.SENSOR.get() : ModBlocks.RECEIVER.get();
		return stillValid(access, player, block);
	}
}
