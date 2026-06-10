package com.computermod.content.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.computermod.channel.ChannelBus;
import com.computermod.channel.ChannelDirectory;
import com.computermod.reading.ReadingStack;
import com.computermod.world.ChunkLoadManager;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Sensor / transmitter: reads the block it faces via the universal {@link ReadingStack} and publishes
 * the whole reading table onto a named channel <em>the moment it changes</em> — it scans every tick and
 * transmits immediately (no fixed interval), so e.g. an item being added goes out that same tick. The
 * GUI shows, live, exactly what the sensor currently sees.
 */
public class SensorBlockEntity extends SmartBlockEntity
	implements MenuProvider, ChannelConfigurable, ChunkLoadManager.KeepLoaded {

	/** How often (in ticks) we refresh + sync the readings tree shown in the GUI (a client packet). */
	private static final int DISPLAY_INTERVAL = 10;
	/** Safety caps so a huge modded block-entity NBT can't bloat the sync packet. */
	private static final int MAX_NODES = 256;
	private static final int MAX_DEPTH = 8;

	/** One row of the readings tree synced to the client: indent depth, key, value preview, expandable. */
	public record Node(int depth, String key, String value, boolean container) {}

	private String channel = "";
	/** Whether this sensor force-loads its chunks so it keeps publishing with no player nearby. */
	private boolean keepLoaded = false;
	private int displayTimer = 0;
	/** Last table published to the channel; used to publish only when something actually changed. */
	private Map<String, Object> lastPublished = null;

	private final List<Node> readings = new ArrayList<>();
	private List<Node> lastSyncedReadings = null;

	public SensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		Direction facing = SensorBlock.getFacing(getBlockState());
		BlockPos target = worldPosition.relative(facing);
		Map<String, Object> data = ReadingStack.scan(level, target);

		// Transmit the instant the readings change — no fixed interval, so changes go out the same tick.
		if (!channel.isEmpty()) {
			ChannelDirectory.get().touch(channel, ChannelDirectory.Kind.SENSOR, worldPosition, level.getGameTime());
			if (!data.equals(lastPublished)) {
				ChannelBus.get().publish(channel, data);
				lastPublished = data;
			}
		}

		// Refresh the GUI tree at a gentler rate (it's a client packet; eyes don't need every tick).
		if (++displayTimer >= DISPLAY_INTERVAL) {
			displayTimer = 0;
			List<Node> nodes = new ArrayList<>();
			for (Map.Entry<String, Object> entry : data.entrySet())
				flatten(entry.getKey(), entry.getValue(), 0, nodes);
			if (!nodes.equals(lastSyncedReadings)) {
				readings.clear();
				readings.addAll(nodes);
				lastSyncedReadings = new ArrayList<>(nodes);
				sendData();
			}
		}
	}

	/** Walk a scanned value depth-first into an ordered flat list of tree rows. */
	private static void flatten(String key, Object value, int depth, List<Node> out) {
		if (out.size() >= MAX_NODES)
			return;
		if (value instanceof Map<?, ?> map) {
			boolean open = !map.isEmpty() && depth < MAX_DEPTH;
			out.add(new Node(depth, key, "{" + map.size() + (map.size() == 1 ? " field}" : " fields}"), open));
			if (open)
				for (Map.Entry<?, ?> e : map.entrySet())
					flatten(String.valueOf(e.getKey()), e.getValue(), depth + 1, out);
		} else if (value instanceof List<?> list) {
			boolean open = !list.isEmpty() && depth < MAX_DEPTH;
			out.add(new Node(depth, key, "[" + list.size() + (list.size() == 1 ? " entry]" : " entries]"), open));
			if (open) {
				int i = 1;
				for (Object o : list)
					flatten("#" + (i++), o, depth + 1, out);
			}
		} else {
			out.add(new Node(depth, key, scalar(value), false));
		}
	}

	private static String scalar(Object value) {
		if (value == null)
			return "nil";
		String text = value.toString();
		return text.length() > 48 ? text.substring(0, 47) + "…" : text;
	}

	@Override
	public String getChannelName() {
		return channel;
	}

	@Override
	public List<Node> getReadings() {
		return readings;
	}

	@Override
	public void configure(String newChannel) {
		String previous = channel;
		this.channel = newChannel == null ? "" : newChannel;
		if (!previous.isEmpty() && !previous.equals(channel)) {
			ChannelBus.get().publish(previous, null); // release the old channel
			ChannelDirectory.get().forget(worldPosition);
		}
		lastPublished = null; // force an immediate re-publish to the (new) channel next tick
		setChanged();
		sendData();
	}

	@Override
	public void destroy() {
		// Broken: stop occupying the channel — clear the value and drop out of the directory.
		if (!channel.isEmpty())
			ChannelBus.get().publish(channel, null);
		ChannelDirectory.get().forget(worldPosition);
		if (keepLoaded && level instanceof net.minecraft.server.level.ServerLevel serverLevel)
			ChunkLoadManager.setForced(serverLevel, worldPosition, false);
		super.destroy();
	}

	@Override
	public boolean isKeepLoaded() {
		return keepLoaded;
	}

	public void setKeepLoaded(boolean keep) {
		if (keepLoaded == keep)
			return;
		keepLoaded = keep;
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
			ChunkLoadManager.setForced(serverLevel, worldPosition, keep);
		setChanged();
		sendData();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.computermod.sensor");
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
		compound.putBoolean("KeepLoaded", keepLoaded);
		if (clientPacket) {
			ListTag lines = new ListTag();
			for (Node node : readings) {
				CompoundTag tag = new CompoundTag();
				tag.putInt("d", node.depth());
				tag.putString("k", node.key());
				tag.putString("v", node.value());
				tag.putBoolean("c", node.container());
				lines.add(tag);
			}
			compound.put("Readings", lines);
		}
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		channel = compound.getString("Channel");
		keepLoaded = compound.getBoolean("KeepLoaded");
		if (clientPacket) {
			readings.clear();
			ListTag lines = compound.getList("Readings", Tag.TAG_COMPOUND);
			for (int i = 0; i < lines.size(); i++) {
				CompoundTag tag = lines.getCompound(i);
				readings.add(new Node(tag.getInt("d"), tag.getString("k"), tag.getString("v"), tag.getBoolean("c")));
			}
		}
	}
}
