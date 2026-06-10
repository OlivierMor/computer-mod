package com.computermod.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.computermod.content.computer.ComputerBlockEntity;
import com.computermod.content.computer.ComputerMenu;
import com.computermod.network.ClearTerminalC2S;
import com.computermod.network.ConfigureChunkLoadingC2S;
import com.computermod.network.FlashProgramC2S;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * The computer GUI: a small IDE. The Code tab edits the computer's flash filesystem through a tab
 * strip (main.lua plus library files loaded with require), with find (Ctrl+F) and flash (Ctrl+S).
 * The Console tab shows the running program's print output with copy/clear. The status bar shows
 * run state, errors, cursor position and stored energy.
 *
 * <p>Edits live client-side until flashed; the working copy survives window resizes because it is
 * kept on the screen instance, not in the widgets.
 */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {

	private enum Tab { CODE, CONSOLE }

	/** External documentation link. */
	private static final String WIKI_URL = "https://oliviermor.github.io/computer-mod/";

	private static final int BG = 0xF00B0E13;
	private static final int HEADER = 0xFF161B22;
	private static final int FIELD = 0xFF0E141B;
	private static final int BORDER = 0xFF333A44;
	private static final int ACCENT = 0xFF4F8CFF;
	private static final int TEXT = 0xFFC8D0DA;
	private static final int DIM = 0xFF6B7480;
	private static final int AMBER = 0xFFE3B341;

	private static final int CONSOLE_LINE_H = 10;

	private Tab tab = Tab.CODE;
	private CodeEditor editor;
	private Button flashButton;
	private Button copyButton;
	private Button clearButton;
	private Button keepLoadedButton;
	private Button areaButton;
	private int consoleScroll = 0; // lines scrolled up from the bottom

	/** Working copy of the filesystem being edited (kept across init() calls / window resizes). */
	private LinkedHashMap<String, String> workingFiles;
	/** What is currently on the block, to detect unflashed changes. */
	private LinkedHashMap<String, String> flashedFiles;
	private String currentFile = ComputerBlockEntity.MAIN_FILE;

	/** Inline edit box for naming a new file or renaming a tab; null when not naming. */
	private EditBox nameBox;
	/** The file being renamed, or null when the name box creates a new file. */
	private String renaming;
	/** Tab with an armed delete confirmation (clicking its × again deletes), and when it was armed. */
	private String deleteArmed;
	private long deleteArmedAt;

	/** Find bar state. */
	private EditBox findBox;
	private boolean findOpen = false;

	/** Ticks left to show the "Flashed ✓" confirmation. */
	private int flashedTicks = 0;

	private int tabCodeX, tabConsoleX, tabW;
	/** Left x of each rendered file tab, parallel to {@link #fileTabNames}. */
	private final List<Integer> fileTabXs = new ArrayList<>();
	private final List<String> fileTabNames = new ArrayList<>();
	private int fileStripEnd;

	public ComputerScreen(ComputerMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 480;
		this.imageHeight = 260;
	}

	private int contentTop() {
		return topPos + 54; // header 16 + main tabs 18 + file strip 16 + gaps
	}

	private int contentBottom() {
		return topPos + imageHeight - 26;
	}

	private int contentLeft() {
		return leftPos + 8;
	}

	private int contentRight() {
		return leftPos + imageWidth - 8;
	}

	private int fileStripY() {
		return topPos + 38;
	}

	@Override
	protected void init() {
		// Fit on small windows instead of clipping.
		this.imageWidth = Math.min(480, this.width - 8);
		this.imageHeight = Math.min(260, this.height - 8);
		super.init();

		if (workingFiles == null) {
			workingFiles = new LinkedHashMap<>(menu.getBlockEntity().getFiles());
			flashedFiles = new LinkedHashMap<>(workingFiles);
			if (!workingFiles.containsKey(currentFile))
				currentFile = workingFiles.keySet().iterator().next();
		}

		tabW = 70;
		tabCodeX = leftPos + 8;
		tabConsoleX = tabCodeX + tabW + 4;

		editor = new CodeEditor(font, contentLeft(), contentTop(), imageWidth - 16,
			contentBottom() - contentTop());
		editor.setCharacterLimit(ComputerBlockEntity.MAX_TOTAL_CHARS);
		editor.setValue(workingFiles.getOrDefault(currentFile, ""));
		editor.setChangeListener(() -> workingFiles.put(currentFile, editor.getValue()));
		addRenderableWidget(editor);

		flashButton = Button.builder(Component.literal("⚡ Flash"), b -> flash())
			.bounds(contentRight() - 70, topPos + imageHeight - 23, 70, 18)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.literal("Write the files to the computer and reboot (Ctrl+S)")))
			.build();
		addRenderableWidget(flashButton);

		copyButton = Button.builder(Component.literal("Copy"), b -> copyConsole())
			.bounds(contentRight() - 110, topPos + imageHeight - 23, 50, 18).build();
		clearButton = Button.builder(Component.literal("Clear"), b -> PacketDistributor
			.sendToServer(new ClearTerminalC2S(menu.getBlockEntity().getBlockPos())))
			.bounds(contentRight() - 54, topPos + imageHeight - 23, 50, 18).build();
		addRenderableWidget(copyButton);
		addRenderableWidget(clearButton);

		addRenderableWidget(Button.builder(Component.literal("Wiki ↗"), b -> openWiki())
			.bounds(leftPos + imageWidth - 58, topPos + 16, 50, 16).build());

		ComputerBlockEntity be = menu.getBlockEntity();
		keepLoadedButton = Button.builder(keepLoadedLabel(), b ->
			PacketDistributor.sendToServer(new ConfigureChunkLoadingC2S(be.getBlockPos(),
				!be.isKeepLoaded(), be.getLoadRadius())))
			.bounds(leftPos + imageWidth - 58 - 92, topPos + 16, 88, 16)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				"Keeps this computer's part of the world loaded and running even when no player is "
					+ "nearby, so it never reboots or pauses while you are away. Needed for anything "
					+ "that must work at a distance: a beacon broadcasting coordinates, a remote farm "
					+ "controller, an airport reporting to aircraft. Turn it off for machines you only "
					+ "use while standing next to them, to save server performance.")))
			.build();
		addRenderableWidget(keepLoadedButton);

		areaButton = Button.builder(areaLabel(), b ->
			PacketDistributor.sendToServer(new ConfigureChunkLoadingC2S(be.getBlockPos(),
				be.isKeepLoaded(), (be.getLoadRadius() + 1) % 3)))
			.bounds(leftPos + imageWidth - 58 - 92 - 60, topPos + 16, 56, 16)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				"Size of the loaded chunk area centred on this block. 3×3 is a good default: it also "
					+ "keeps the power source and machines beside it running. Use 1×1 for a lone "
					+ "block, 5×5 for a large site.")))
			.build();
		addRenderableWidget(areaButton);

		findBox = new EditBox(font, contentRight() - 150, topPos + 19, 110, 14, Component.literal("find"));
		findBox.setHint(Component.literal("find…"));
		findBox.setMaxLength(64);
		findBox.setResponder(q -> editor.setSearchQuery(q));
		addRenderableWidget(findBox);

		nameBox = new EditBox(font, 0, 0, 90, 12, Component.literal("file name"));
		nameBox.setMaxLength(28);
		addRenderableWidget(nameBox);

		applyTab();
	}

	private void openWiki() {
		if (WIKI_URL.isEmpty() || minecraft == null)
			return;
		minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
			if (confirmed)
				Util.getPlatform().openUri(WIKI_URL);
			minecraft.setScreen(this);
		}, WIKI_URL, true));
	}

	private void applyTab() {
		boolean code = tab == Tab.CODE;
		editor.visible = code;
		editor.active = code;
		flashButton.visible = code;
		flashButton.active = code;
		findBox.visible = code && findOpen;
		copyButton.visible = !code;
		copyButton.active = !code;
		clearButton.visible = !code;
		clearButton.active = !code;
		closeNameBox();
		setFocused(code ? editor : null);
		editor.setFocused(code);
	}

	// --- file operations ---

	private void switchFile(String name) {
		if (!workingFiles.containsKey(name) || name.equals(currentFile))
			return;
		workingFiles.put(currentFile, editor.getValue());
		currentFile = name;
		editor.setValue(workingFiles.get(name));
		editor.setFocused(true);
		setFocused(editor);
	}

	private void flash() {
		workingFiles.put(currentFile, editor.getValue());
		PacketDistributor.sendToServer(new FlashProgramC2S(menu.getBlockEntity().getBlockPos(),
			new LinkedHashMap<>(workingFiles)));
		flashedFiles = new LinkedHashMap<>(workingFiles);
		flashedTicks = 50;
	}

	private boolean isModified(String file) {
		return !workingFiles.getOrDefault(file, "").equals(flashedFiles.getOrDefault(file, ""));
	}

	private boolean anyModified() {
		return !workingFiles.equals(flashedFiles);
	}

	private void copyConsole() {
		if (minecraft != null)
			minecraft.keyboardHandler.setClipboard(String.join("\n",
				menu.getBlockEntity().getClientTerminal()));
	}

	private void openNameBox(String forRename, int x) {
		renaming = forRename;
		nameBox.setValue(forRename == null ? "" : forRename.substring(0, forRename.length() - 4));
		nameBox.setPosition(Math.min(x, leftPos + imageWidth - 100), fileStripY() + 1);
		nameBox.visible = true;
		nameBox.setFocused(true);
		setFocused(nameBox);
	}

	private void closeNameBox() {
		if (nameBox == null)
			return;
		nameBox.visible = false;
		nameBox.setFocused(false);
		renaming = null;
	}

	/** Apply the name box: create a new file, or rename {@link #renaming}. */
	private void confirmName() {
		String base = nameBox.getValue().trim();
		if (base.endsWith(".lua"))
			base = base.substring(0, base.length() - 4);
		String name = base + ".lua";
		boolean valid = ComputerBlockEntity.FILE_NAME.matcher(name).matches()
			&& !name.equals(ComputerBlockEntity.MAIN_FILE)
			&& !workingFiles.containsKey(name);
		if (!valid) {
			closeNameBox();
			return;
		}
		if (renaming == null) {
			if (workingFiles.size() < ComputerBlockEntity.MAX_FILES) {
				workingFiles.put(name, "");
				switchFile(name);
			}
		} else {
			// Rename in place, preserving tab order.
			LinkedHashMap<String, String> renamed = new LinkedHashMap<>();
			for (var e : workingFiles.entrySet())
				renamed.put(e.getKey().equals(renaming) ? name : e.getKey(), e.getValue());
			workingFiles = renamed;
			if (currentFile.equals(renaming))
				currentFile = name;
		}
		closeNameBox();
	}

	private void deleteFile(String name) {
		if (name.equals(ComputerBlockEntity.MAIN_FILE) || !workingFiles.containsKey(name))
			return;
		workingFiles.remove(name);
		if (currentFile.equals(name)) {
			currentFile = ComputerBlockEntity.MAIN_FILE;
			editor.setValue(workingFiles.get(currentFile));
		}
	}

	// --- input ---

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (nameBox.visible) {
				closeNameBox();
				setFocused(editor);
				return true;
			}
			if (findOpen) {
				findOpen = false;
				findBox.visible = false;
				editor.setSearchQuery("");
				setFocused(editor);
				return true;
			}
			onClose();
			return true;
		}

		if (nameBox.visible && getFocused() == nameBox) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				confirmName();
				setFocused(editor);
				return true;
			}
			nameBox.keyPressed(keyCode, scanCode, modifiers);
			return true; // swallow everything (e.g. the inventory key) while typing a name
		}

		if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
			flash();
			return true;
		}
		if (ctrl && keyCode == GLFW.GLFW_KEY_F && tab == Tab.CODE) {
			findOpen = true;
			findBox.visible = true;
			findBox.setValue(findBox.getValue());
			setFocused(findBox);
			findBox.setFocused(true);
			return true;
		}

		if (findOpen && getFocused() == findBox
			&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			editor.findNext((modifiers & GLFW.GLFW_MOD_SHIFT) == 0);
			return true;
		}

		if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
			return true;
		return keyCode == GLFW.GLFW_KEY_TAB && super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (inTab(mouseX, mouseY, tabCodeX)) { tab = Tab.CODE; applyTab(); return true; }
		if (inTab(mouseX, mouseY, tabConsoleX)) { tab = Tab.CONSOLE; applyTab(); return true; }

		if (tab == Tab.CODE && mouseY >= fileStripY() && mouseY < fileStripY() + 14
			&& !nameBox.visible && handleFileStripClick(mouseX, button))
			return true;

		if (nameBox.visible && !nameBox.isMouseOver(mouseX, mouseY)) {
			confirmName();
			setFocused(editor);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** Clicks in the file tab strip: select, ×-delete (click twice), rename (right-click), +new. */
	private boolean handleFileStripClick(double mouseX, int button) {
		for (int i = 0; i < fileTabNames.size(); i++) {
			String name = fileTabNames.get(i);
			int x = fileTabXs.get(i);
			int w = fileTabWidth(name);
			if (mouseX < x || mouseX >= x + w)
				continue;
			boolean onClose = !name.equals(ComputerBlockEntity.MAIN_FILE)
				&& mouseX >= x + w - 10;
			if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				if (!name.equals(ComputerBlockEntity.MAIN_FILE))
					openNameBox(name, x);
				return true;
			}
			if (onClose) {
				long now = System.currentTimeMillis();
				if (name.equals(deleteArmed) && now - deleteArmedAt < 2500) {
					deleteFile(name);
					deleteArmed = null;
				} else {
					deleteArmed = name;
					deleteArmedAt = now;
				}
				return true;
			}
			deleteArmed = null;
			switchFile(name);
			return true;
		}
		// the "+" button at the end of the strip
		if (mouseX >= fileStripEnd && mouseX < fileStripEnd + 13
			&& workingFiles.size() < ComputerBlockEntity.MAX_FILES) {
			openNameBox(null, fileStripEnd);
			return true;
		}
		return false;
	}

	private boolean inTab(double mx, double my, int x) {
		return mx >= x && mx <= x + tabW && my >= topPos + 18 && my <= topPos + 36;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (tab == Tab.CODE && editor != null && editor.visible
			&& editor.mouseDragged(mouseX, mouseY, button, dragX, dragY))
			return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.CONSOLE) {
			int max = Math.max(0, menu.getBlockEntity().getClientTerminal().size() - visibleLines());
			consoleScroll = clamp(consoleScroll + (int) Math.signum(scrollY), 0, max);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private int visibleLines() {
		return (contentBottom() - contentTop()) / CONSOLE_LINE_H;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (flashedTicks > 0)
			flashedTicks--;
		// reflect the server-confirmed chunk-loading state
		keepLoadedButton.setMessage(keepLoadedLabel());
		areaButton.setMessage(areaLabel());
		areaButton.active = menu.getBlockEntity().isKeepLoaded();
	}

	private Component keepLoadedLabel() {
		return Component.literal(menu.getBlockEntity().isKeepLoaded() ? "Keep loaded ✔" : "Keep loaded ✘");
	}

	private Component areaLabel() {
		int size = menu.getBlockEntity().getLoadRadius() * 2 + 1;
		return Component.literal("Area " + size + "×" + size);
	}

	// --- rendering ---

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
		g.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 16, HEADER);
		if (tab != Tab.CODE)
			g.fill(contentLeft(), contentTop(), contentRight(), contentBottom(), FIELD);
		g.fill(leftPos, topPos + imageHeight - 26, leftPos + imageWidth, topPos + imageHeight - 25, BORDER);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		drawTab(g, tabCodeX, "Code", tab == Tab.CODE, mouseX, mouseY);
		drawTab(g, tabConsoleX, "Console", tab == Tab.CONSOLE, mouseX, mouseY);
		ComputerBlockEntity be = menu.getBlockEntity();
		if (tab == Tab.CODE) {
			renderFileStrip(g, mouseX, mouseY);
			if (findOpen && editor.searchMatchCount() >= 0 && !findBox.getValue().isEmpty()) {
				String count = editor.searchMatchCount() + " match" + (editor.searchMatchCount() == 1 ? "" : "es");
				g.drawString(font, count, findBox.getX() + findBox.getWidth() + 4, findBox.getY() + 3,
					editor.searchMatchCount() == 0 ? 0xFFFF6060 : DIM, false);
			}
		} else {
			renderConsole(g, be);
		}
		renderStatusBar(g, be);
	}

	private void drawTab(GuiGraphics g, int x, String label, boolean active, int mouseX, int mouseY) {
		boolean hover = inTab(mouseX, mouseY, x);
		g.fill(x, topPos + 18, x + tabW, topPos + 36, active ? 0xFF22304A : (hover ? 0xFF1B2230 : 0xFF131820));
		if (active)
			g.fill(x, topPos + 34, x + tabW, topPos + 36, ACCENT);
		int tw = font.width(label);
		g.drawString(font, label, x + (tabW - tw) / 2, topPos + 23, active ? 0xFFFFFFFF : TEXT, false);
	}

	private int fileTabWidth(String name) {
		int w = font.width(name) + 12;
		if (isModified(name))
			w += 6;
		if (!name.equals(ComputerBlockEntity.MAIN_FILE))
			w += 9; // room for the × button
		return w;
	}

	private void renderFileStrip(GuiGraphics g, int mouseX, int mouseY) {
		int y = fileStripY();
		fileTabNames.clear();
		fileTabXs.clear();
		int x = contentLeft();
		for (String name : workingFiles.keySet()) {
			int w = fileTabWidth(name);
			if (x + w > contentRight() - 16)
				break; // tabs that don't fit are still flashed, just not shown
			fileTabNames.add(name);
			fileTabXs.add(x);
			boolean active = name.equals(currentFile);
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
			g.fill(x, y, x + w, y + 14, active ? 0xFF1E2836 : (hover ? 0xFF161D28 : 0xFF10151D));
			if (active)
				g.fill(x, y, x + w, y + 1, ACCENT);
			int tx = x + 6;
			g.drawString(font, name, tx, y + 3, active ? 0xFFFFFFFF : TEXT, false);
			tx += font.width(name);
			if (isModified(name)) {
				g.drawString(font, "•", tx + 1, y + 3, AMBER, false);
				tx += 7;
			}
			if (!name.equals(ComputerBlockEntity.MAIN_FILE)) {
				boolean armed = name.equals(deleteArmed) && System.currentTimeMillis() - deleteArmedAt < 2500;
				boolean hoverClose = hover && mouseX >= x + w - 10;
				g.drawString(font, "×", x + w - 9, y + 3,
					armed ? 0xFFFF5050 : (hoverClose ? 0xFFFFFFFF : DIM), false);
			}
			x += w + 2;
		}
		fileStripEnd = x;
		if (workingFiles.size() < ComputerBlockEntity.MAX_FILES && !nameBox.visible) {
			boolean hover = mouseX >= x && mouseX < x + 13 && mouseY >= y && mouseY < y + 14;
			g.fill(x, y, x + 13, y + 14, hover ? 0xFF1B2230 : 0xFF10151D);
			g.drawString(font, "+", x + 4, y + 3, hover ? 0xFFFFFFFF : DIM, false);
		}
		if (nameBox.visible)
			g.drawString(font, ".lua", nameBox.getX() + nameBox.getWidth() + 3, y + 3, DIM, false);

		// armed delete hint
		if (deleteArmed != null && System.currentTimeMillis() - deleteArmedAt < 2500)
			g.drawString(font, "click × again to delete " + deleteArmed,
				contentLeft(), contentBottom() + 4, 0xFFFF8080, false);
	}

	private void renderConsole(GuiGraphics g, ComputerBlockEntity be) {
		List<String> lines = be.getClientTerminal();
		int vis = visibleLines();
		if (lines.isEmpty()) {
			g.drawString(font, "(no output — print(...) appears here)", contentLeft() + 4, contentTop() + 4, DIM, false);
			return;
		}
		int start = Math.max(0, lines.size() - vis - consoleScroll);
		int y = contentTop() + 3;
		g.enableScissor(contentLeft(), contentTop(), contentRight(), contentBottom());
		for (int i = start; i < lines.size() && y < contentBottom() - 2; i++) {
			String line = lines.get(i);
			int color = line.startsWith("error:") ? 0xFFFF7070 : 0xFFA8E8A8;
			g.drawString(font, line, contentLeft() + 4, y, color, false);
			y += CONSOLE_LINE_H;
		}
		g.disableScissor();
		if (lines.size() > vis) {
			int trackX = contentRight() - 4;
			int trackTop = contentTop() + 2;
			int trackH = contentBottom() - contentTop() - 4;
			g.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0xFF161C24);
			int max = lines.size() - vis;
			int barH = Math.max(8, trackH * vis / lines.size());
			int pos = max - consoleScroll;
			int barY = trackTop + (trackH - barH) * pos / Math.max(1, max);
			g.fill(trackX, barY, trackX + 3, barY + barH, ACCENT);
		}
	}

	private void renderStatusBar(GuiGraphics g, ComputerBlockEntity be) {
		int y = topPos + imageHeight - 18;
		String state = be.getClientState();
		int color = switch (state) {
			case "RUNNING" -> 0xFF55FF55;
			case "ERROR" -> 0xFFFF6060;
			case "FINISHED" -> 0xFF55D0FF;
			default -> 0xFF808890;
		};
		g.drawString(font, "●", leftPos + 8, y, color, false);
		int x = leftPos + 18;
		g.drawString(font, state, x, y, color, false);
		x += font.width(state) + 10;

		// the error message, right next to the state
		if (state.equals("ERROR") && !be.getClientError().isEmpty()) {
			String err = clip(be.getClientError(), flashButton.getX() - x - 130);
			g.drawString(font, err, x, y, 0xFFFF9090, false);
		}

		// flash feedback / unflashed marker, just left of the Flash button
		if (tab == Tab.CODE) {
			if (flashedTicks > 0 && !anyModified()) {
				String msg = "Flashed ✓";
				g.drawString(font, msg, flashButton.getX() - font.width(msg) - 8, y, 0xFF6FE36F, false);
			} else if (anyModified()) {
				String msg = "unflashed changes";
				g.drawString(font, msg, flashButton.getX() - font.width(msg) - 8, y, AMBER, false);
			}
			// cursor position
			String pos = "Ln " + editor.cursorLine() + ", Col " + editor.cursorColumn();
			g.drawString(font, pos, leftPos + imageWidth / 2 - font.width(pos) / 2, y, DIM, false);
		}

		// power: stored FE (rotational power needs no display; the shaft is visible in-world)
		if (be.getEnergyCapacity() > 0 && be.getStoredEnergy() > 0 && tab == Tab.CONSOLE) {
			String fe = formatFe(be.getStoredEnergy()) + " FE";
			g.drawString(font, fe, flashButton.getX() - font.width(fe) - 8, y, DIM, false);
		}
	}

	private static String formatFe(int fe) {
		if (fe >= 1_000_000)
			return String.format("%.1fM", fe / 1_000_000.0);
		if (fe >= 1_000)
			return String.format("%.1fk", fe / 1_000.0);
		return String.valueOf(fe);
	}

	private String clip(String s, int maxWidth) {
		if (maxWidth <= 8)
			return "";
		if (font.width(s) <= maxWidth)
			return s;
		while (s.length() > 1 && font.width(s + "…") > maxWidth)
			s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, "Computer", 8, 5, 0xFFFFFFFF, false);
	}
}
