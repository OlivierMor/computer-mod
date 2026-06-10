package com.computermod.world;

import java.util.ArrayList;
import java.util.List;

import com.computermod.ComputerMod;
import com.computermod.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * Chunk loading for blocks with the "Keep loaded" toggle. A block that opts in holds a ticking chunk
 * ticket on its own chunk (plus a configurable ring), so a computer keeps running, a sensor keeps
 * publishing, and a receiver keeps emitting while no player is anywhere near. Tickets are owned by the
 * block's position, persist with the world across restarts, and are re-validated on world load: a
 * ticket whose block is gone, toggled off, or disallowed by config is dropped.
 */
public final class ChunkLoadManager {

	/** Implemented by block entities that can opt into chunk loading. */
	public interface KeepLoaded {
		boolean isKeepLoaded();
	}

	private static final TicketController CONTROLLER = new TicketController(
		ResourceLocation.fromNamespaceAndPath(ComputerMod.MODID, "keep_loaded"),
		ChunkLoadManager::validateTickets);

	public static void register(RegisterTicketControllersEvent event) {
		event.register(CONTROLLER);
	}

	/** Add or remove the ticking tickets owned by {@code owner} for the configured radius. */
	public static void setForced(ServerLevel level, BlockPos owner, boolean add) {
		if (add && !Config.ALLOW_CHUNK_LOADING.get())
			return;
		int radius = Config.CHUNK_LOAD_RADIUS.get();
		ChunkPos center = new ChunkPos(owner);
		for (int dx = -radius; dx <= radius; dx++)
			for (int dz = -radius; dz <= radius; dz++)
				CONTROLLER.forceChunk(level, owner, center.x + dx, center.z + dz, add, true);
	}

	/** World load: keep tickets only for blocks that still exist and still want to be loaded. */
	private static void validateTickets(ServerLevel level, TicketHelper helper) {
		List<BlockPos> owners = new ArrayList<>(helper.getBlockTickets().keySet());
		for (BlockPos pos : owners) {
			BlockEntity be = level.getBlockEntity(pos);
			boolean valid = Config.ALLOW_CHUNK_LOADING.get()
				&& be instanceof KeepLoaded keep && keep.isKeepLoaded();
			if (!valid)
				helper.removeAllTickets(pos);
		}
	}

	private ChunkLoadManager() {}
}
