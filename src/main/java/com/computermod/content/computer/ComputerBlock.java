package com.computermod.content.computer;

import com.computermod.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The programmable computer block. It is a Create kinetic machine: it connects to a shaft
 * (currently from below, rotating on the Y axis like a Millstone) and draws Stress from the
 * network while running. The attached {@link ComputerBlockEntity} hosts the script runtime.
 */
public class ComputerBlock extends KineticBlock implements IBE<ComputerBlockEntity> {

	public ComputerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.DOWN;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof ComputerBlockEntity computer)
				serverPlayer.openMenu(computer, buf -> buf.writeBlockPos(pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<ComputerBlockEntity> getBlockEntityClass() {
		return ComputerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ComputerBlockEntity> getBlockEntityType() {
		return ModBlockEntities.COMPUTER.get();
	}
}
