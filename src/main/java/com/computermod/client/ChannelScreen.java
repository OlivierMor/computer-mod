package com.computermod.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.computermod.content.channel.ChannelConfigurable;
import com.computermod.content.channel.ChannelMenu;
import com.computermod.content.channel.ReceiverBlockEntity;
import com.computermod.content.channel.SensorBlockEntity;
import com.computermod.content.channel.SensorBlockEntity.Node;
import com.computermod.network.ConfigureChannelC2S;
import com.computermod.network.ConfigureChunkLoadingC2S;
import com.computermod.network.RequestChannelsC2S;
import com.computermod.world.ChunkLoadManager;

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
 * Configuration GUI for the sensor and receiver blocks, styled to match the computer.
 *
 * <p><b>Sensor:</b> pick the broadcast channel, then explore everything the sensor currently sees in
 * a live tree: search it, expand/collapse it all at once, copy the whole tree as text, or click any
 * value to copy the exact Lua expression that reads it (e.g. {@code channel("tank").fluid.amount}).
 * Expansion is remembered by path, so rows don't jump when the data refreshes.
 *
 * <p><b>Receiver:</b> pick the channel to listen to and watch the live value arrive and turn into a
 * redstone strength on a 0-15 meter.
 */
public class ChannelScreen extends AbstractContainerScreen<ChannelMenu> {

	/** Wiki landing page. */
	private static final String WIKI_URL = "https://oliviermor.github.io/computer-mod/";

	private static final int LINE_HEIGHT = 11;
	private static final int INDENT = 10;
	private static final int ARROW_GUTTER = 8;

	private static final int BG = 0xF00B0E13;
	private static final int HEADER = 0xFF161B22;
	private static final int BORDER = 0xFF333A44;
	private static final int ACCENT = 0xFF4F8CFF;
	private static final int TEXT = 0xFFC8D0DA;
	private static final int DIM = 0xFF6B7480;

	private static final int COL_ARROW = 0xFF8AA0FF;
	private static final int COL_KEY = 0xFFBFE6FF;
	private static final int COL_SEP = 0xFF6A6A6A;
	private static final int COL_SCALAR = 0xFF80FF80;
	private static final int COL_TABLE = 0xFF6FA8FF;

	private EditBox channelBox;
	private EditBox searchBox;
	private Button keepLoadedButton;
	private Button areaButton;
	private ChannelSuggestions suggestions;
	private int refreshTimer = 0;
	private int scrollOffset = 0;
	private boolean draggingScrollbar = false;
	/** Paths of expanded container rows (stable across data refreshes, unlike indices). */
	private final Set<String> expandedPaths = new HashSet<>();
	/** Transient "copied!" style feedback. */
	private String toast;
	private long toastAt;

	/** One row ready for rendering: the node plus its computed path. */
	private record Row(Node node, String path) {}

	public ChannelScreen(ChannelMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = menu.isSensor() ? 340 : 300;
		this.imageHeight = menu.isSensor() ? 252 : 186;
	}

	@Override
	protected void init() {
		super.init();
		ChannelConfigurable config = menu.getConfigurable();

		channelBox = new EditBox(font, leftPos + 64, topPos + 24, imageWidth - 64 - 66, 16,
			Component.literal("channel"));
		channelBox.setMaxLength(64);
		channelBox.setHint(Component.literal("channel name"));
		if (config != null)
			channelBox.setValue(config.getChannelName());
		addRenderableWidget(channelBox);

		Button apply = Button.builder(Component.literal("Apply"), b -> applyChannel())
			.bounds(leftPos + imageWidth - 58, topPos + 23, 50, 18).build();
		addRenderableWidget(apply);

		addRenderableWidget(Button.builder(Component.literal("Wiki ↗"), b -> openWiki())
			.bounds(leftPos + imageWidth - 58, topPos + 16 - 16, 50, 14).build());

		String role = menu.isSensor() ? "keeps publishing fresh readings" : "keeps emitting its redstone signal";
		keepLoadedButton = Button.builder(keepLoadedLabel(), b -> {
			if (menu.getBlockEntity() instanceof ChunkLoadManager.KeepLoaded keep)
				PacketDistributor.sendToServer(new ConfigureChunkLoadingC2S(menu.getBlockEntity().getBlockPos(),
					!keep.isKeepLoaded(), keep.getLoadRadius()));
		})
			.bounds(leftPos + imageWidth - 58 - 92, topPos, 88, 14)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				"Keeps this block's part of the world loaded and running even when no player is nearby, "
					+ "so it " + role + " while you are away. Needed for far-away systems such as a "
					+ "remote outpost or an airport. Turn it off for blocks you only use while standing "
					+ "next to them, to save server performance.")))
			.build();
		addRenderableWidget(keepLoadedButton);

		areaButton = Button.builder(areaLabel(), b -> {
			if (menu.getBlockEntity() instanceof ChunkLoadManager.KeepLoaded keep)
				PacketDistributor.sendToServer(new ConfigureChunkLoadingC2S(menu.getBlockEntity().getBlockPos(),
					keep.isKeepLoaded(), (keep.getLoadRadius() + 1) % 3));
		})
			.bounds(leftPos + imageWidth - 58 - 92 - 60, topPos, 56, 14)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				"Size of the loaded chunk area centred on this block. 3×3 is a good default: it also "
					+ "keeps the machines beside it running. Use 1×1 for a lone block, 5×5 for a "
					+ "large site.")))
			.build();
		addRenderableWidget(areaButton);

		if (menu.isSensor()) {
			searchBox = new EditBox(font, leftPos + 12, topPos + 48, 110, 14, Component.literal("search"));
			searchBox.setMaxLength(48);
			searchBox.setHint(Component.literal("search…"));
			addRenderableWidget(searchBox);

			int bx = leftPos + imageWidth - 12;
			bx -= 50;
			addRenderableWidget(Button.builder(Component.literal("Copy"), b -> copyTree())
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
					Component.literal("Copy the whole tree as text")))
				.bounds(bx, topPos + 46, 50, 16).build());
			bx -= 24;
			addRenderableWidget(Button.builder(Component.literal("⊟"), b -> expandedPaths.clear())
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Collapse all")))
				.bounds(bx, topPos + 46, 22, 16).build());
			bx -= 24;
			addRenderableWidget(Button.builder(Component.literal("⊞"), b -> expandAll())
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Expand all")))
				.bounds(bx, topPos + 46, 22, 16).build());
		}

		suggestions = new ChannelSuggestions(font);
		suggestions.setBox(channelBox);
		PacketDistributor.sendToServer(RequestChannelsC2S.INSTANCE); // pull the current channel list
	}

	private void applyChannel() {
		PacketDistributor.sendToServer(new ConfigureChannelC2S(menu.getBlockEntity().getBlockPos(),
			channelBox.getValue().trim()));
		showToast("channel applied");
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (++refreshTimer >= 20) { // keep the suggestion list fresh while the GUI is open
			refreshTimer = 0;
			PacketDistributor.sendToServer(RequestChannelsC2S.INSTANCE);
		}
		// reflect the server-confirmed chunk-loading state
		keepLoadedButton.setMessage(keepLoadedLabel());
		areaButton.setMessage(areaLabel());
		areaButton.active = menu.getBlockEntity() instanceof ChunkLoadManager.KeepLoaded keep
			&& keep.isKeepLoaded();
	}

	private Component keepLoadedLabel() {
		boolean on = menu.getBlockEntity() instanceof ChunkLoadManager.KeepLoaded keep && keep.isKeepLoaded();
		return Component.literal(on ? "Keep loaded ✔" : "Keep loaded ✘");
	}

	private Component areaLabel() {
		int radius = menu.getBlockEntity() instanceof ChunkLoadManager.KeepLoaded keep ? keep.getLoadRadius() : 1;
		int size = radius * 2 + 1;
		return Component.literal("Area " + size + "×" + size);
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

	private void showToast(String message) {
		toast = message;
		toastAt = System.currentTimeMillis();
	}

	// --- sensor tree model ---

	private int panelTop() {
		return topPos + 66;
	}

	private int panelBottom() {
		return topPos + imageHeight - 22;
	}

	private int panelLeft() {
		return leftPos + 12;
	}

	private int panelRight() {
		return leftPos + imageWidth - 12;
	}

	private List<Node> readings() {
		if (menu.getBlockEntity() instanceof SensorBlockEntity sensor)
			return sensor.getReadings();
		return List.of();
	}

	/** A path segment in Lua syntax: {@code .key}, {@code [3]} for list entries, or {@code ["odd key"]}. */
	private static String luaSegment(String key) {
		if (key.startsWith("#")) {
			try {
				return "[" + Integer.parseInt(key.substring(1)) + "]";
			} catch (NumberFormatException ignored) {
				// fall through to the quoted form
			}
		}
		if (key.matches("[A-Za-z_][A-Za-z0-9_]*"))
			return "." + key;
		return "[\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
	}

	/** All nodes with their paths, in server (depth-first) order. */
	private List<Row> allRows() {
		List<Node> nodes = readings();
		List<Row> rows = new ArrayList<>(nodes.size());
		String[] stack = new String[64];
		for (Node n : nodes) {
			int depth = Math.min(n.depth(), stack.length - 1);
			String parent = depth == 0 ? "" : stack[depth - 1];
			String path = parent + luaSegment(n.key());
			stack[depth] = path;
			rows.add(new Row(n, path));
		}
		return rows;
	}

	/**
	 * Rows currently visible. Without a search: a row shows when all its ancestors are expanded.
	 * With a search: rows whose key/value/path match, shown flat with their ancestors for context.
	 */
	private List<Row> visibleRows() {
		List<Row> all = allRows();
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);

		if (query.isEmpty()) {
			List<Row> visible = new ArrayList<>();
			int collapseDepth = Integer.MAX_VALUE;
			for (Row row : all) {
				if (row.node().depth() > collapseDepth)
					continue; // hidden under a collapsed ancestor
				collapseDepth = Integer.MAX_VALUE;
				visible.add(row);
				if (row.node().container() && !expandedPaths.contains(row.path()))
					collapseDepth = row.node().depth();
			}
			return visible;
		}

		// search: collect matches, then include every ancestor of a match
		Set<Integer> keep = new HashSet<>();
		int[] ancestors = new int[64]; // index of the most recent row at each depth
		for (int i = 0; i < all.size(); i++) {
			Row row = all.get(i);
			int depth = Math.min(row.node().depth(), ancestors.length - 1);
			ancestors[depth] = i;
			boolean match = row.node().key().toLowerCase(Locale.ROOT).contains(query)
				|| row.node().value().toLowerCase(Locale.ROOT).contains(query);
			if (match)
				for (int d = 0; d <= depth; d++)
					keep.add(ancestors[d]);
		}
		List<Row> visible = new ArrayList<>();
		for (int i = 0; i < all.size(); i++)
			if (keep.contains(i))
				visible.add(all.get(i));
		return visible;
	}

	private void expandAll() {
		for (Row row : allRows())
			if (row.node().container())
				expandedPaths.add(row.path());
	}

	/** Copy the whole tree as indented text (good for pasting into notes or an LLM). */
	private void copyTree() {
		StringBuilder out = new StringBuilder();
		String channel = channelBox.getValue().trim();
		out.append("sensor").append(channel.isEmpty() ? "" : " on channel \"" + channel + "\"").append(":\n");
		for (Row row : allRows()) {
			out.append("  ".repeat(row.node().depth() + 1))
				.append(row.node().key()).append(" = ").append(row.node().value()).append('\n');
		}
		if (minecraft != null)
			minecraft.keyboardHandler.setClipboard(out.toString());
		showToast("tree copied to clipboard");
	}

	/** The full Lua expression that reads this row's value from the channel. */
	private String luaExpression(String path) {
		String channel = channelBox.getValue().trim();
		String base = "channel(\"" + (channel.isEmpty() ? "myChannel" : channel) + "\")";
		return base + path;
	}

	private int maxScroll(int visibleCount) {
		return Math.max(0, visibleCount * LINE_HEIGHT + 8 - (panelBottom() - panelTop()));
	}

	// --- input ---

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Only Escape closes the GUI — otherwise typing 'e' (the inventory key) in the channel box
		// would exit the screen. Route everything else to the focused widget.
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (suggestions != null && suggestions.keyPressed(keyCode))
			return true;
		if (channelBox.isFocused()
			&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			applyChannel();
			return true;
		}
		if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
			return true;
		return keyCode == GLFW.GLFW_KEY_TAB && super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (suggestions != null && suggestions.mouseClicked(mouseX, mouseY))
			return true;

		if (menu.isSensor() && mouseX >= panelLeft() && mouseX <= panelRight()
			&& mouseY >= panelTop() && mouseY <= panelBottom()) {

			// Close the suggestion dropdown so it stops covering the tree.
			if (channelBox.isFocused()) {
				channelBox.setFocused(false);
				setFocused(null);
			}

			List<Row> visible = visibleRows();

			// scrollbar?
			if (mouseX >= panelRight() - 5 && maxScroll(visible.size()) > 0) {
				draggingScrollbar = true;
				scrollToMouse(mouseY, visible.size());
				return true;
			}

			int rowIndex = (int) ((mouseY - (panelTop() + 4) + scrollOffset) / LINE_HEIGHT);
			if (rowIndex >= 0 && rowIndex < visible.size()) {
				Row row = visible.get(rowIndex);
				if (row.node().container()) {
					if (!expandedPaths.remove(row.path()))
						expandedPaths.add(row.path());
					scrollOffset = Math.min(scrollOffset, maxScroll(visibleRows().size()));
				} else if (minecraft != null) {
					// click a value: copy the Lua expression that reads it
					String expr = luaExpression(row.path());
					minecraft.keyboardHandler.setClipboard(expr);
					showToast("copied  " + expr);
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollToMouse(mouseY, visibleRows().size());
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void scrollToMouse(double mouseY, int visibleCount) {
		int max = maxScroll(visibleCount);
		double f = (mouseY - panelTop()) / Math.max(1, panelBottom() - panelTop());
		scrollOffset = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (menu.isSensor() && mouseX >= panelLeft() && mouseX <= panelRight()
			&& mouseY >= panelTop() && mouseY <= panelBottom()) {
			int max = maxScroll(visibleRows().size());
			scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (deltaY * LINE_HEIGHT)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	// --- rendering ---

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
		g.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 14, HEADER);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);

		g.drawString(font, "Channel", leftPos + 12, topPos + 28, TEXT, false);

		if (menu.isSensor())
			renderTree(g, mouseX, mouseY);
		else
			renderReceiverPanel(g);

		// transient feedback (copied / applied)
		if (toast != null && System.currentTimeMillis() - toastAt < 2500) {
			String t = clip(toast, imageWidth - 24);
			g.drawString(font, t, leftPos + 12, topPos + imageHeight - 18, 0xFF6FE36F, false);
		}

		// Channel autocomplete: existence dot + dropdown of live channels, drawn on top of everything.
		ChannelSuggestions.statusDot(g, channelBox);
		if (suggestions != null)
			suggestions.render(g, mouseX, mouseY, height);
	}

	// --- sensor rendering ---

	private void renderTree(GuiGraphics g, int mouseX, int mouseY) {
		int top = panelTop();
		int bottom = panelBottom();
		int left = panelLeft();
		int right = panelRight();
		g.fill(left, top, right, bottom, 0xFF0E141B);
		g.renderOutline(left, top, right - left, bottom - top, BORDER);

		List<Row> visible = visibleRows();
		boolean searching = searchBox != null && !searchBox.getValue().trim().isEmpty();

		g.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
		if (visible.isEmpty()) {
			String msg = searching ? "No matches." : "Nothing in front of the sensor.";
			g.drawString(font, msg, left + 6, top + 6, DIM, false);
		} else {
			int y = top + 4 - scrollOffset;
			for (Row row : visible) {
				if (y + LINE_HEIGHT >= top && y <= bottom)
					renderRow(g, row, left, right, y, mouseX, mouseY);
				y += LINE_HEIGHT;
			}
		}
		g.disableScissor();

		// scrollbar
		int max = maxScroll(visible.size());
		if (max > 0) {
			int trackX = right - 5;
			int trackTop = top + 2;
			int trackH = bottom - top - 4;
			g.fill(trackX, trackTop, trackX + 3, trackTop + trackH, 0xFF161C24);
			int barH = Math.max(10, trackH * (bottom - top) / (visible.size() * LINE_HEIGHT + 8));
			int barY = trackTop + (int) ((long) (trackH - barH) * scrollOffset / max);
			boolean hot = draggingScrollbar || (mouseX >= trackX && mouseX <= trackX + 3
				&& mouseY >= trackTop && mouseY <= trackTop + trackH);
			g.fill(trackX, barY, trackX + 3, barY + barH, hot ? ACCENT : 0xFF333A44);
		}

		if (toast == null || System.currentTimeMillis() - toastAt >= 2500)
			g.drawString(font, "click a value to copy its Lua path", leftPos + 12, bottom + 4, DIM, false);
	}

	private void renderRow(GuiGraphics g, Row row, int left, int right, int y, int mouseX, int mouseY) {
		Node n = row.node();
		int x = left + 4 + n.depth() * INDENT;
		boolean hover = mouseX >= left && mouseX <= right - 6 && mouseY >= y && mouseY < y + LINE_HEIGHT;
		if (hover)
			g.fill(left + 1, y - 1, right - 1, y + LINE_HEIGHT - 1, n.container() ? 0x30FFFFFF : 0x304F8CFF);

		if (n.container())
			g.drawString(font, expandedPaths.contains(row.path()) ? "▾" : "▸", x, y, COL_ARROW, false);

		int tx = x + ARROW_GUTTER;
		g.drawString(font, n.key(), tx, y, COL_KEY, false);
		tx += font.width(n.key());
		g.drawString(font, " = ", tx, y, COL_SEP, false);
		tx += font.width(" = ");
		g.drawString(font, clip(n.value(), right - 8 - tx), tx, y, n.container() ? COL_TABLE : COL_SCALAR, false);
	}

	// --- receiver rendering ---

	private void renderReceiverPanel(GuiGraphics g) {
		int left = leftPos + 12;
		int right = leftPos + imageWidth - 12;
		int top = topPos + 52;
		int bottom = topPos + imageHeight - 22;
		g.fill(left, top, right, bottom, 0xFF0E141B);
		g.renderOutline(left, top, right - left, bottom - top, BORDER);

		ReceiverBlockEntity be = menu.getBlockEntity() instanceof ReceiverBlockEntity r ? r : null;
		if (be == null)
			return;

		String channel = be.getChannelName();
		int y = top + 8;
		if (channel.isEmpty()) {
			g.drawString(font, "No channel set.", left + 8, y, DIM, false);
			g.drawString(font, "Pick a channel above to start listening.", left + 8, y + 12, DIM, false);
			return;
		}

		// live value
		String preview = be.getValuePreview();
		g.drawString(font, "value", left + 8, y, DIM, false);
		g.drawString(font, preview.isEmpty() ? "(nothing published yet)" : preview,
			left + 50, y, preview.isEmpty() ? DIM : COL_SCALAR, false);
		y += 14;

		// redstone meter
		int output = be.getOutput();
		g.drawString(font, "signal", left + 8, y, DIM, false);
		String num = output + "/15";
		g.drawString(font, num, left + 50, y, output > 0 ? 0xFFFF5555 : DIM, false);
		y += 12;
		int meterX = left + 8;
		int segW = Math.max(6, (right - left - 16 - 15 * 2) / 16);
		for (int i = 0; i <= 15; i++) {
			int sx = meterX + i * (segW + 2);
			boolean on = output >= i && output > 0 || (i == 0 && output == 0);
			int color = i <= output && output > 0 ? lerpRed(i) : 0xFF1A2028;
			g.fill(sx, y, sx + segW, y + 8, color);
			if (on && i == output)
				g.renderOutline(sx, y, segW, 8, 0xFFFFFFFF);
		}
		y += 16;

		// conversion rules
		g.drawString(font, "How values become redstone:", left + 8, y, TEXT, false);
		y += 12;
		g.drawString(font, "number  -> clamped to 0-15", left + 8, y, DIM, false);
		y += 10;
		g.drawString(font, "true / false  ->  15 / 0", left + 8, y, DIM, false);
		y += 10;
		g.drawString(font, "table or nothing  ->  0", left + 8, y, DIM, false);
	}

	/** Redstone meter colour ramp: dark red to bright red. */
	private static int lerpRed(int i) {
		int v = 120 + i * 9;
		return 0xFF000000 | (Math.min(255, v) << 16) | (20 << 8) | 20;
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
		g.drawString(font, title, 8, 4, 0xFFFFFFFF, false);
	}
}
