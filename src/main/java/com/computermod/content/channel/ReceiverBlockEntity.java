package com.computermod.content.channel;

import java.util.List;

import com.computermod.channel.ChannelBus;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Receiver / actuator: subscribes to a channel and emits redstone (0-15) on all sides based on the
 * latest value (number → clamped, boolean → 15/0, otherwise 0).
 */
public class ReceiverBlockEntity extends SmartBlockEntity implements MenuProvider, ChannelConfigurable {

	private String channel = "";
	private int output = 0;

	public ReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		int target = channel.isEmpty() ? 0 : toRedstone(ChannelBus.get().read(channel));
		if (target != output) {
			output = target;
			setChanged();
			// Reflect the strength in the blockstate so the LED lights up and the block emits light
			// proportional to the signal (both LIT and GLOW sync to the client automatically).
			BlockState state = getBlockState();
			if (state.getBlock() instanceof ReceiverBlock) {
				BlockState lit = state.setValue(ReceiverBlock.LIT, output > 0).setValue(ReceiverBlock.GLOW, output);
				if (lit != state)
					level.setBlock(worldPosition, lit, Block.UPDATE_ALL);
			}
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			sendData();
		}
	}

	private static int toRedstone(Object value) {
		if (value instanceof Boolean b)
			return b ? 15 : 0;
		if (value instanceof Number n)
			return Math.max(0, Math.min(15, n.intValue()));
		if (value instanceof String s) {
			try {
				return Math.max(0, Math.min(15, (int) Double.parseDouble(s)));
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}
		return 0;
	}

	public int getOutput() {
		return output;
	}

	@Override
	public String getChannelName() {
		return channel;
	}

	@Override
	public void configure(String newChannel) {
		this.channel = newChannel == null ? "" : newChannel;
		setChanged();
		sendData();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.computermod.receiver");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new ChannelMenu(id, inv, this);
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.putString("Channel", channel);
		compound.putInt("Output", output);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		channel = compound.getString("Channel");
		output = compound.getInt("Output");
	}
}
