package com.computermod.content.computer;

import com.computermod.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The programmable computer block. It is a Create kinetic machine driven by a shaft that runs
 * horizontally through its centre, exactly like Create's Brass Encased Shaft: the recessed
 * openings on the two axis faces accept a coaxial shaft connection from either side. It draws
 * Stress from the network while running. The attached {@link ComputerBlockEntity} hosts the
 * script runtime.
 */
public class ComputerBlock extends HorizontalAxisKineticBlock implements IBE<ComputerBlockEntity> {

	public ComputerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Prefer aligning to an adjacent kinetic source if one is present...
		Axis preferred = getPreferredHorizontalAxis(context);
		if (preferred != null)
			return defaultBlockState().setValue(HORIZONTAL_AXIS, preferred);
		// ...otherwise run the shaft along the player's facing, so the screen face points at them
		// and the shaft ports face away to the sides, rather than a port staring at the player.
		return defaultBlockState().setValue(HORIZONTAL_AXIS, context.getHorizontalDirection().getAxis());
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
