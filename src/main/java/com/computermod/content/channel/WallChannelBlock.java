package com.computermod.content.channel;

import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the sensor and receiver: a thin plate that mounts against the face of another block,
 * like a button or Create's redstone link. {@link #FACING} points <em>into</em> the block it is stuck to.
 */
public abstract class WallChannelBlock<T extends BlockEntity> extends Block implements IBE<T> {

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final int DEPTH = 4; // plate thickness in pixels
	private static final VoxelShape DOWN = Block.box(3, 0, 3, 13, DEPTH, 13);
	private static final VoxelShape UP = Block.box(3, 16 - DEPTH, 3, 13, 16, 13);
	private static final VoxelShape NORTH = Block.box(3, 3, 0, 13, 13, DEPTH);
	private static final VoxelShape SOUTH = Block.box(3, 3, 16 - DEPTH, 13, 13, 16);
	private static final VoxelShape WEST = Block.box(0, 3, 3, DEPTH, 13, 13);
	private static final VoxelShape EAST = Block.box(16 - DEPTH, 3, 3, 16, 13, 13);

	protected WallChannelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public static Direction getFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case DOWN -> DOWN;
			case UP -> UP;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Mount on the clicked face: FACING points into the block we were placed against.
		BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos support = pos.relative(state.getValue(FACING));
		return !level.getBlockState(support).isAir();
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
		LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		// Pop off if the block we are mounted to is removed.
		if (direction == state.getValue(FACING) && !state.canSurvive(level, pos))
			return Blocks.AIR.defaultBlockState();
		return state;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		// Route removal through Create's helper so SmartBlockEntity.destroy() actually runs
		// (releasing the block's channel and any chunk tickets it owns).
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof MenuProvider menu)
			serverPlayer.openMenu(menu, buf -> buf.writeBlockPos(pos));
		return InteractionResult.SUCCESS;
	}
}
