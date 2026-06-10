package com.computermod.content.computer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the shaft that runs horizontally through the centre of the computer, exactly like
 * Create's Brass Encased Shaft: the casing (static block model) has a recessed opening on each
 * axis face, and the full-length rotating shaft drawn here pokes through those openings, so its
 * spinning ends are visible from either side. Only this kinetic part is drawn here.
 *
 * <p>Unlike Create's own kinetic renderers this does <em>not</em> bail out when Flywheel is active,
 * because the computer registers no Flywheel visual — so this renderer is always responsible for
 * the shaft and there is no risk of double-drawing.</p>
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
		// shaft would render pitch black. Sample the two axis faces its ends poke through instead.
		int shaftLight = lightAtShaftEnds(be.getLevel(), be.getBlockPos(), axis, light);

		SuperByteBuffer shaft = CachedBuffers.block(KINETIC_BLOCK, shaft(axis));
		kineticRotationTransform(shaft, be, axis, angle, shaftLight);
		shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
	}

	/**
	 * Brightest light over the two faces on {@code axis} — i.e. the openings the shaft ends are
	 * visible through. Falls back to {@code fallback} if the level is unavailable.
	 */
	private static int lightAtShaftEnds(Level level, BlockPos pos, Axis axis, int fallback) {
		if (level == null)
			return fallback;
		int sky = 0;
		int block = 0;
		for (Direction d : Direction.values()) {
			if (d.getAxis() != axis)
				continue;
			int packed = LevelRenderer.getLightColor(level, pos.relative(d));
			sky = Math.max(sky, LightTexture.sky(packed));
			block = Math.max(block, LightTexture.block(packed));
		}
		return LightTexture.pack(block, sky);
	}
}
