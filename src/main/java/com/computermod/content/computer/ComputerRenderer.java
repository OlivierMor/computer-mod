package com.computermod.content.computer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the cogwheel that runs horizontally through the centre of the computer, exactly like
 * Create's Encased Cogwheel: a shaftless cogwheel spinning on the block's horizontal axis, plus a
 * half-shaft poking out of each axis face that is actually connected. The casing itself is the
 * static block model; only these kinetic parts are drawn here.
 *
 * <p>Unlike Create's own kinetic renderers this does <em>not</em> bail out when Flywheel is active,
 * because the computer registers no Flywheel visual — so this renderer is always responsible for
 * the cog and there is no risk of double-drawing.</p>
 */
public class ComputerRenderer extends KineticBlockEntityRenderer<ComputerBlockEntity> {

	public ComputerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ComputerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
							  int light, int overlay) {
		BlockState state = be.getBlockState();
		if (!(state.getBlock() instanceof IRotate def))
			return;

		Axis axis = def.getRotationAxis(state);
		float angle = getAngleForBe(be, be.getBlockPos(), axis);

		// The computer is a full opaque cube, so the light sampled at its own position is ~0 and the
		// cog would render pitch black. Sample the open faces the teeth poke through instead.
		int cogLight = lightAroundCog(be.getLevel(), be.getBlockPos(), axis, light);

		// The cogwheel itself, lying in the plane perpendicular to the horizontal axis.
		SuperByteBuffer cog = CachedBuffers.partialFacingVertical(AllPartialModels.SHAFTLESS_COGWHEEL, state,
			Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE));
		kineticRotationTransform(cog, be, axis, angle, cogLight);
		cog.renderInto(ms, buffer.getBuffer(RenderType.solid()));

		// A half-shaft sticking out of each end, but ONLY when something is actually plugged into that
		// end coaxially. The block reports a shaft towards both axis ends unconditionally (so it can be
		// driven from either side), which would otherwise leave phantom stubs poking through the front
		// and back faces with nothing attached.
		for (Direction d : Iterate.directionsInAxis(axis)) {
			if (!isShaftConnected(be.getLevel(), be.getBlockPos(), d))
				continue;
			SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, d);
			kineticRotationTransform(shaft, be, axis, angle, cogLight);
			shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
		}
	}

	/** True when the neighbour on side {@code d} is a kinetic block presenting a shaft back towards us. */
	private static boolean isShaftConnected(Level level, BlockPos pos, Direction d) {
		if (level == null)
			return false;
		BlockPos neighbour = pos.relative(d);
		BlockState ns = level.getBlockState(neighbour);
		return ns.getBlock() instanceof IRotate other
			&& other.hasShaftTowards(level, neighbour, ns, d.getOpposite());
	}

	/**
	 * Brightest light over the faces perpendicular to {@code axis} — i.e. the open cells the cog
	 * teeth actually stick out into. Falls back to {@code fallback} if the level is unavailable.
	 */
	private static int lightAroundCog(Level level, BlockPos pos, Axis axis, int fallback) {
		if (level == null)
			return fallback;
		int sky = 0;
		int block = 0;
		for (Direction d : Direction.values()) {
			if (d.getAxis() == axis)
				continue;
			int packed = LevelRenderer.getLightColor(level, pos.relative(d));
			sky = Math.max(sky, LightTexture.sky(packed));
			block = Math.max(block, LightTexture.block(packed));
		}
		return LightTexture.pack(block, sky);
	}
}
