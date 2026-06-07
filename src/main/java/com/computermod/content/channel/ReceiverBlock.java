package com.computermod.content.channel;

import com.computermod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Receiver block: a thin plate mounted against a block that emits redstone (0-15) from a channel value. */
public class ReceiverBlock extends WallChannelBlock<ReceiverBlockEntity> {

	public ReceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		ReceiverBlockEntity be = getBlockEntity(level, pos);
		return be == null ? 0 : be.getOutput();
	}

	@Override
	public Class<ReceiverBlockEntity> getBlockEntityClass() {
		return ReceiverBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ReceiverBlockEntity> getBlockEntityType() {
		return ModBlockEntities.RECEIVER.get();
	}
}
