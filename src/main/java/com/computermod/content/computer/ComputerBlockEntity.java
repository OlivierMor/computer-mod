package com.computermod.content.computer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.computermod.Config;
import com.computermod.channel.ChannelDirectory;
import com.computermod.registry.ModBlockEntities;
import com.computermod.runtime.ComputerApi;
import com.computermod.runtime.LuaRuntime;
import com.computermod.world.ChunkLoadManager;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the computer. Behaves like a microcontroller: you "flash" it with a program
 * (persistent), and it runs whenever powered. Powering it boots a fresh runtime from the flashed
 * code; losing power halts it and discards all volatile state (RAM + live terminal). It can be
 * powered by Create rotation (Stress) and/or Forge Energy.
 *
 * <p>The flash holds a small <b>filesystem</b>: named {@code .lua} files in a fixed order. Booting
 * runs {@code main.lua}; the other files are libraries loaded with {@code require("name")}.
 */
public class ComputerBlockEntity extends KineticBlockEntity implements MenuProvider, ChunkLoadManager.KeepLoaded {

	/** Stress units this computer imposes on the network (relative to RPM, like other machines). */
	public static final float STRESS_IMPACT = 4.0f;

	public static final String OFF = "OFF";

	/** The boot file every computer runs first. Always present, never deletable. */
	public static final String MAIN_FILE = "main.lua";
	public static final int MAX_FILES = 16;
	public static final int MAX_TOTAL_CHARS = 120_000;
	/** Legal file names: letters/digits/_/- plus the .lua suffix, e.g. {@code pid.lua}. */
	public static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,24}\\.lua");

	private static final int SYNC_INTERVAL = 10;

	/** Persisted "flash memory": the program files, in tab order. {@code main.lua} is always first. */
	private LinkedHashMap<String, String> files = newFlash();
	/** Whether this computer force-loads its chunks so it keeps running with no player nearby. */
	private boolean keepLoaded = false;
	/** Internal Forge Energy buffer (persisted). */
	private ComputerEnergyStorage energy;

	/** Persistent "disk": survives reboots and world reload (unlike RAM). */
	private static final int MAX_DISK_KEYS = 1024;
	private CompoundTag diskData = new CompoundTag();

	/** Server-only volatile runtime ("RAM"); null when powered off. */
	private LuaRuntime runtime;
	/** Frozen copy of the last terminal output shown after power-off (cleared on next boot). */
	private List<String> frozenTerminal;

	private int syncTimer = 0;
	private String lastSyncedState = null;
	private int lastSyncedRevision = -1;

	/** Client-only mirror, populated from sync packets. */
	private String clientState = OFF;
	private String clientError = "";
	private final List<String> clientTerminal = new ArrayList<>();

	public ComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		energy = new ComputerEnergyStorage(Config.FE_CAPACITY.get(), Config.FE_CAPACITY.get(),
			() -> level != null ? level.getGameTime() : 0L);
	}

	private static LinkedHashMap<String, String> newFlash() {
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put(MAIN_FILE, "");
		return map;
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.COMPUTER.get(),
			(be, side) -> be.energy);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
	}

	@Override
	public float calculateStressApplied() {
		this.lastStressApplied = STRESS_IMPACT;
		return STRESS_IMPACT;
	}

	// --- Power ---

	/** Kinetically powered: shaft turning and the network not overstressed. */
	public boolean isKineticPowered() {
		return getSpeed() != 0 && !overStressed;
	}

	/**
	 * Electrically powered: a source is actively feeding the buffer <em>and</em> it holds at least
	 * one tick's worth of energy. Requiring an active feed (not just stored charge) makes the
	 * computer behave like a microcontroller — cut the supply and it powers down within a tick,
	 * rather than coasting on whatever was left in the buffer.
	 */
	public boolean hasElectricalPower() {
		return level != null
			&& energy.isBeingSupplied(level.getGameTime())
			&& energy.getEnergyStored() >= Config.FE_PER_TICK.get();
	}

	/** Powered by any source. */
	public boolean isPowered() {
		return isKineticPowered() || hasElectricalPower();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;

		if (isPowered()) {
			if (runtime == null) {
				// Rising edge: boot from flash with fresh RAM and a clean terminal.
				frozenTerminal = null;
				LuaRuntime fresh = new LuaRuntime(Config.MAX_OPS_PER_TICK.get());
				fresh.setApiInstaller(g -> ComputerApi.install(g, fresh, this));
				runtime = fresh;
				runtime.start(new LinkedHashMap<>(files));
				forceSync();
			}
			// Drain the worker's world-access requests and grant it the next clock slice.
			runtime.onServerTick();
			// Pay for the slice with FE only when there's no free rotational power.
			if (runtime.isRunning() && !isKineticPowered())
				energy.consume(Config.FE_PER_TICK.get());
			syncIfChanged();
		} else if (runtime != null) {
			// Power lost: shut off, discard RAM, freeze the last terminal for display.
			frozenTerminal = runtime.getTerminalSnapshot();
			haltRuntime();
			forceSync();
		}
	}

	/** Stop the worker and drop this computer's channel-directory entries. */
	private void haltRuntime() {
		if (runtime != null) {
			runtime.stop();
			runtime = null;
		}
		ChannelDirectory.get().forget(worldPosition);
	}

	// --- Persistent disk store (server thread) ---

	public Object diskGet(String key) {
		return diskData.contains(key) ? com.computermod.reading.NbtCodec.fromTag(diskData.get(key)) : null;
	}

	public void diskSet(String key, Object value) {
		if (key == null || key.isEmpty())
			return;
		if (value == null) {
			diskData.remove(key);
		} else {
			if (!diskData.contains(key) && diskData.size() >= MAX_DISK_KEYS)
				return; // capacity guard
			net.minecraft.nbt.Tag tag = com.computermod.reading.NbtCodec.toTag(value);
			if (tag != null)
				diskData.put(key, tag);
		}
		setChanged();
	}

	public void diskDelete(String key) {
		diskData.remove(key);
		setChanged();
	}

	public List<String> diskList() {
		return new ArrayList<>(diskData.getAllKeys());
	}

	public void diskClear() {
		diskData = new CompoundTag();
		setChanged();
	}

	private int currentRevision() {
		return runtime != null ? runtime.getTerminalRevision() : lastSyncedRevision;
	}

	private void syncIfChanged() {
		if (!displayState().equals(lastSyncedState)) {
			forceSync();
		} else if (currentRevision() != lastSyncedRevision && ++syncTimer >= SYNC_INTERVAL) {
			forceSync();
		}
	}

	private void forceSync() {
		syncTimer = 0;
		lastSyncedState = displayState();
		lastSyncedRevision = currentRevision();
		sendData();
	}

	private String displayState() {
		if (!isPowered() || runtime == null)
			return OFF;
		return runtime.getState().name();
	}

	private List<String> currentTerminal() {
		if (runtime != null)
			return runtime.getTerminalSnapshot();
		return frozenTerminal != null ? frozenTerminal : List.of();
	}

	// --- Flashing (server side) ---

	/** Replace the flashed files. Reboots cleanly if currently powered. */
	public void flashProgram(Map<String, String> newFiles) {
		this.files = sanitize(newFiles);
		haltRuntime(); // kill the running worker so the next powered tick boots fresh
		frozenTerminal = null;
		setChanged();
		forceSync();
	}

	/**
	 * Enforce the flash limits no matter what a client sends: legal names only, {@code main.lua}
	 * always present and first, at most {@link #MAX_FILES} files / {@link #MAX_TOTAL_CHARS} chars.
	 */
	public static LinkedHashMap<String, String> sanitize(Map<String, String> input) {
		LinkedHashMap<String, String> out = newFlash();
		if (input == null)
			return out;
		int total = 0;
		for (Map.Entry<String, String> e : input.entrySet()) {
			String name = e.getKey() == null ? "" : e.getKey().trim();
			String source = e.getValue() == null ? "" : e.getValue();
			if (!FILE_NAME.matcher(name).matches())
				continue;
			if (!name.equals(MAIN_FILE) && out.size() >= MAX_FILES)
				continue;
			if (total + source.length() > MAX_TOTAL_CHARS)
				source = source.substring(0, Math.max(0, MAX_TOTAL_CHARS - total));
			total += source.length();
			out.put(name, source);
		}
		return out;
	}

	/** Empty the terminal of the running program (and any frozen snapshot). */
	public void clearTerminal() {
		frozenTerminal = null;
		if (runtime != null)
			runtime.clearTerminal();
		forceSync();
	}

	@Override
	public void invalidate() {
		// Block broken or chunk unloaded: never leave the worker thread running.
		haltRuntime();
		super.invalidate();
	}

	@Override
	public void destroy() {
		// Block actually broken (not just unloaded): release any chunk tickets it owns.
		if (keepLoaded && level instanceof net.minecraft.server.level.ServerLevel serverLevel)
			ChunkLoadManager.setForced(serverLevel, worldPosition, false);
		super.destroy();
	}

	// --- Chunk loading ---

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

	/** The flashed files in tab order ({@code main.lua} first). Do not mutate. */
	public Map<String, String> getFiles() {
		return files;
	}

	// --- Client-facing accessors (used by the screen) ---

	public String getClientState() {
		return clientState;
	}

	/** Last error message, or "" (client mirror). */
	public String getClientError() {
		return clientError;
	}

	public List<String> getClientTerminal() {
		return clientTerminal;
	}

	public int getStoredEnergy() {
		return energy.getEnergyStored();
	}

	public int getEnergyCapacity() {
		return energy.getMaxEnergyStored();
	}

	// --- MenuProvider ---

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.computermod.computer");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new ComputerMenu(id, inv, this);
	}

	// --- Persistence & sync ---

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		ListTag fileList = new ListTag();
		for (Map.Entry<String, String> e : files.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", e.getKey());
			tag.putString("Source", e.getValue());
			fileList.add(tag);
		}
		compound.put("Files", fileList);
		compound.putBoolean("KeepLoaded", keepLoaded);
		compound.putInt("Energy", energy.getEnergyStored());
		if (!clientPacket)
			compound.put("Disk", diskData);
		if (clientPacket) {
			compound.putString("State", displayState());
			compound.putString("Error", runtime != null ? runtime.getError() : "");
			ListTag lines = new ListTag();
			for (String line : currentTerminal())
				lines.add(StringTag.valueOf(line));
			compound.put("Terminal", lines);
		}
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		LinkedHashMap<String, String> loaded = new LinkedHashMap<>();
		if (compound.contains("Files", Tag.TAG_LIST)) {
			ListTag fileList = compound.getList("Files", Tag.TAG_COMPOUND);
			for (int i = 0; i < fileList.size(); i++) {
				CompoundTag tag = fileList.getCompound(i);
				loaded.put(tag.getString("Name"), tag.getString("Source"));
			}
		} else if (compound.contains("ProgramSource", Tag.TAG_STRING)) {
			// Worlds from before the filesystem: the whole program becomes main.lua.
			loaded.put(MAIN_FILE, compound.getString("ProgramSource"));
		}
		files = sanitize(loaded);
		keepLoaded = compound.getBoolean("KeepLoaded");
		energy.setStored(compound.getInt("Energy"));
		if (!clientPacket)
			diskData = compound.getCompound("Disk");
		if (clientPacket) {
			clientState = compound.getString("State");
			clientError = compound.getString("Error");
			clientTerminal.clear();
			ListTag lines = compound.getList("Terminal", Tag.TAG_STRING);
			for (int i = 0; i < lines.size(); i++)
				clientTerminal.add(lines.getString(i));
		}
	}
}
