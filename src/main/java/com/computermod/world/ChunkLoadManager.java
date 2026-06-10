package com.computermod.world;

import java.util.ArrayList;
import java.util.List;

import com.computermod.ComputerMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * Chunk loading for the computer, sensor and receiver. Each block keeps a square of ticking chunks
 * loaded around itself (on by default, radius configurable per block in its GUI), so a far-away
 * system keeps computing, publishing and emitting with no player anywhere near it. Tickets are owned
 * by the block's position, persist with the world across restarts, and are re-validated on world
 * load: a ticket whose block is gone or toggled off is dropped.
 */
public final class ChunkLoadManager {

	/** Default loaded area radius: 1 = a 3x3 chunk square, covering the block plus adjacent power. */
	public static final int DEFAULT_RADIUS = 1;
	/** Largest selectable radius: 2 = a 5x5 chunk square. */
	public static final int MAX_RADIUS = 2;

	/** Implemented by block entities that hold chunks loaded. */
	public interface KeepLoaded {
		boolean isKeepLoaded();

		/** Loaded-area radius in chunks (0 = own chunk only). */
		int getLoadRadius();
	}

	private static final TicketController CONTROLLER = new TicketController(
		ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "keep_loaded"),
		ChunkLoadManager::validateTickets);

	public static void register(RegisterTicketControllersEvent event) {
		event.register(CONTROLLER);
	}

	/** Add or remove the ticking tickets owned by {@code owner} for a square of the given radius. */
	public static void setForced(ServerLevel level, BlockPos owner, int radius, boolean add) {
		radius = Mth.clamp(radius, 0, MAX_RADIUS);
		ChunkPos center = new ChunkPos(owner);
		for (int dx = -radius; dx <= radius; dx++)
			for (int dz = -radius; dz <= radius; dz++)
				CONTROLLER.forceChunk(level, owner, center.x + dx, center.z + dz, add, true);
	}

	/** World load: keep tickets only for blocks that still exist and still want their chunks loaded. */
	private static void validateTickets(ServerLevel level, TicketHelper helper) {
		List<BlockPos> owners = new ArrayList<>(helper.getBlockTickets().keySet());
		for (BlockPos pos : owners) {
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof KeepLoaded keep && keep.isKeepLoaded()))
				helper.removeAllTickets(pos);
		}
	}

	private ChunkLoadManager() {}
}
