package com.computermod.content.channel;

import com.computermod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Receiver block: a thin plate mounted against a block that emits redstone (0-15) from a channel value. */
public class ReceiverBlock extends WallChannelBlock<ReceiverBlockEntity> {

	/** Whether the status LED is lit (any signal at all). Selects the lit vs unlit model. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** Current signal strength 0-15; drives both the emitted light level and how bright the LED renders. */
	public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 15);

	public ReceiverBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT, GLOW);
	}

	// The receiver is now a flat circuit-board card like the sensor, so it inherits the thin-plate
	// hitbox from WallChannelBlock instead of the old redstone-link silhouette.

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
