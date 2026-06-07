package com.computermod.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.computermod.content.channel.ChannelConfigurable;
import com.computermod.content.channel.ChannelMenu;
import com.computermod.content.channel.SensorBlockEntity;
import com.computermod.content.channel.SensorBlockEntity.Node;
import com.computermod.network.ConfigureChannelC2S;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Configuration GUI for the sensor and receiver blocks. */
public class ChannelScreen extends AbstractContainerScreen<ChannelMenu> {

	/** Wiki landing page. */
	private static final String WIKI_URL = "https://oliviermor.github.io/computer-mod/";

	private static final int LINE_HEIGHT = 11;
	private static final int INDENT = 10;
	private static final int ARROW_GUTTER = 8;

	private static final int COL_ARROW = 0x8AA0FF;
	private static final int COL_KEY = 0xBFE6FF;
	private static final int COL_SEP = 0x6A6A6A;
	private static final int COL_SCALAR = 0x80FF80;
	private static final int COL_TABLE = 0x6FA8FF;

	private EditBox channelBox;
	private int scrollOffset = 0;
	/** Indices (into the flat node list) of expanded container rows. */
	private final Set<Integer> expanded = new HashSet<>();

	public ChannelScreen(ChannelMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 280;
		this.imageHeight = menu.isSensor() ? 240 : 150;
	}

	@Override
	protected void init() {
		super.init();
		ChannelConfigurable config = menu.getConfigurable();

		channelBox = new EditBox(font, leftPos + 12, topPos + 34, imageWidth - 24, 18, Component.literal("channel"));
		channelBox.setMaxLength(64);
		channelBox.setHint(Component.literal("channel name"));
		if (config != null)
			channelBox.setValue(config.getChannelName());
		addRenderableWidget(channelBox);

		Button apply = Button.builder(Component.literal("Apply"), b -> {
			PacketDistributor.sendToServer(new ConfigureChannelC2S(menu.getBlockEntity().getBlockPos(),
				channelBox.getValue()));
		}).bounds(leftPos + imageWidth - 76, topPos + imageHeight - 26, 64, 18).build();
		addRenderableWidget(apply);

		Button wiki = Button.builder(Component.literal("Wiki ↗"), b -> openWiki())
			.bounds(leftPos + 12, topPos + imageHeight - 26, 64, 18).build();
		wiki.active = !WIKI_URL.isEmpty();
		addRenderableWidget(wiki);
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

	private int panelTop() {
		return topPos + 72;
	}

	private int panelBottom() {
		return topPos + imageHeight - 34;
	}

	private List<Node> readings() {
		if (menu.getBlockEntity() instanceof SensorBlockEntity sensor)
			return sensor.getReadings();
		return List.of();
	}

	/** Node indices currently visible (an entry is shown only when all its ancestors are expanded). */
	private List<Integer> visibleRows() {
		List<Node> nodes = readings();
		List<Integer> visible = new ArrayList<>();
		int collapseDepth = Integer.MAX_VALUE;
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (n.depth() > collapseDepth)
				continue; // hidden under a collapsed ancestor
			collapseDepth = Integer.MAX_VALUE;
			visible.add(i);
			if (n.container() && !expanded.contains(i))
				collapseDepth = n.depth();
		}
		return visible;
	}

	private int maxScroll(int visibleCount) {
		return Math.max(0, visibleCount * LINE_HEIGHT - (panelBottom() - panelTop()));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (menu.isSensor() && mouseX >= leftPos + 12 && mouseX <= leftPos + imageWidth - 12
			&& mouseY >= panelTop() && mouseY <= panelBottom()) {
			List<Node> nodes = readings();
			List<Integer> visible = visibleRows();
			int row = (int) ((mouseY - (panelTop() + 4) + scrollOffset) / LINE_HEIGHT);
			if (row >= 0 && row < visible.size()) {
				int index = visible.get(row);
				if (nodes.get(index).container()) {
					if (!expanded.remove(index))
						expanded.add(index);
					scrollOffset = Math.min(scrollOffset, maxScroll(visibleRows().size()));
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (menu.isSensor() && mouseX >= leftPos + 12 && mouseX <= leftPos + imageWidth - 12
			&& mouseY >= panelTop() && mouseY <= panelBottom()) {
			int max = maxScroll(visibleRows().size());
			scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (deltaY * LINE_HEIGHT)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101418);
		g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF3A3F45);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);

		g.drawString(font, "Channel name:", leftPos + 12, topPos + 22, 0xC0C0C0, false);

		if (menu.isSensor())
			renderTree(g, mouseX, mouseY);
		else {
			g.drawString(font, "Emits redstone from the channel value:", leftPos + 12, topPos + 64, 0xC0C0C0, false);
			g.drawString(font, "number -> 0-15, true -> 15, false/none -> 0", leftPos + 12, topPos + 78, 0x808080, false);
		}
	}

	private void renderTree(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, "This sensor sees:", leftPos + 12, topPos + 60, 0x8AA0FF, false);

		int panelTop = panelTop();
		int panelBottom = panelBottom();
		int panelLeft = leftPos + 12;
		int panelRight = leftPos + imageWidth - 12;
		g.fill(panelLeft, panelTop, panelRight, panelBottom, 0x40000000);

		List<Node> nodes = readings();
		List<Integer> visible = visibleRows();

		g.enableScissor(panelLeft, panelTop, panelRight, panelBottom);
		if (visible.isEmpty()) {
			g.drawString(font, "Nothing in front of the sensor.", panelLeft + 4, panelTop + 4, 0x808080, false);
		} else {
			int y = panelTop + 4 - scrollOffset;
			for (int index : visible) {
				if (y + LINE_HEIGHT >= panelTop && y <= panelBottom)
					renderRow(g, nodes.get(index), index, panelLeft, panelRight, y, mouseX, mouseY);
				y += LINE_HEIGHT;
			}
		}
		g.disableScissor();

		if (maxScroll(visible.size()) > 0)
			g.drawString(font, "↕ scroll", panelRight - font.width("↕ scroll"), topPos + 60, 0x606060, false);
	}

	private void renderRow(GuiGraphics g, Node n, int index, int panelLeft, int panelRight, int y,
		int mouseX, int mouseY) {
		int x = panelLeft + 4 + n.depth() * INDENT;
		boolean hover = n.container() && mouseX >= panelLeft && mouseX <= panelRight
			&& mouseY >= y && mouseY < y + LINE_HEIGHT;
		if (hover)
			g.fill(panelLeft, y - 1, panelRight, y + LINE_HEIGHT - 1, 0x30FFFFFF);

		if (n.container())
			g.drawString(font, expanded.contains(index) ? "▾" : "▸", x, y, COL_ARROW, false);

		int tx = x + ARROW_GUTTER;
		g.drawString(font, n.key(), tx, y, COL_KEY, false);
		tx += font.width(n.key());
		g.drawString(font, " = ", tx, y, COL_SEP, false);
		tx += font.width(" = ");
		g.drawString(font, n.value(), tx, y, n.container() ? COL_TABLE : COL_SCALAR, false);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, 8, 6, 0xFFFFFF, false);
	}
}
