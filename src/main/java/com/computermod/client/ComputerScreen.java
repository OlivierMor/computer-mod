package com.computermod.client;

import java.util.List;

import com.computermod.content.computer.ComputerBlockEntity;
import com.computermod.content.computer.ComputerMenu;
import com.computermod.network.FlashProgramC2S;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * The computer GUI: a Code editor and a Console, plus a button linking to the (external) wiki. The
 * editor keeps focus so typing 'e' no longer closes the screen.
 */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {

	private enum Tab { CODE, CONSOLE }

	/** External documentation link. */
	private static final String WIKI_URL = "https://oliviermor.github.io/computer-mod/";

	private static final int MAX_SOURCE_CHARS = 60_000;
	private static final int BG = 0xF00B0E13;
	private static final int HEADER = 0xFF161B22;
	private static final int FIELD = 0xFF0E141B;
	private static final int BORDER = 0xFF333A44;
	private static final int ACCENT = 0xFF4F8CFF;
	private static final int TEXT = 0xFFC8D0DA;
	private static final int DIM = 0xFF6B7480;

	private Tab tab = Tab.CODE;
	private CodeEditor editor;
	private Button flashButton;
	private int consoleScroll = 0; // lines scrolled up from the bottom

	private int tabCodeX, tabConsoleX, tabW;

	public ComputerScreen(ComputerMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 440;
		this.imageHeight = 248;
	}

	private int contentTop() {
		return topPos + 40;
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

	@Override
	protected void init() {
		super.init();

		tabW = 70;
		tabCodeX = leftPos + 8;
		tabConsoleX = tabCodeX + tabW + 4;

		editor = new CodeEditor(font, contentLeft(), contentTop(), imageWidth - 16,
			contentBottom() - contentTop());
		editor.setCharacterLimit(MAX_SOURCE_CHARS);
		editor.setValue(menu.getBlockEntity().getProgramSource());
		addRenderableWidget(editor);

		flashButton = Button.builder(Component.literal("⚡ Flash"), b ->
			PacketDistributor.sendToServer(new FlashProgramC2S(menu.getBlockEntity().getBlockPos(), editor.getValue())))
			.bounds(contentRight() - 70, topPos + imageHeight - 22, 70, 18).build();
		addRenderableWidget(flashButton);

		addRenderableWidget(Button.builder(Component.literal("Wiki ↗"), b -> openWiki())
			.bounds(contentRight() - 60, topPos + 18, 60, 18).build());

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
		setFocused(code ? editor : null);
		if (editor != null)
			editor.setFocused(code);
	}

	// --- Input ---

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
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
		return super.mouseClicked(mouseX, mouseY, button);
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
		return (contentBottom() - contentTop()) / 10;
	}

	// --- Rendering ---

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
		if (tab == Tab.CONSOLE)
			renderConsole(g, be);
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

	private void renderConsole(GuiGraphics g, ComputerBlockEntity be) {
		List<String> lines = be.getClientTerminal();
		int vis = visibleLines();
		if (lines.isEmpty()) {
			g.drawString(font, "(no output — print(...) appears here)", contentLeft() + 4, contentTop() + 2, DIM, false);
			return;
		}
		int start = Math.max(0, lines.size() - vis - consoleScroll);
		int y = contentTop() + 2;
		for (int i = start; i < lines.size() && y < contentBottom() - 2; i++) {
			g.drawString(font, lines.get(i), contentLeft() + 4, y, 0xA8E8A8, false);
			y += 10;
		}
		if (lines.size() > vis) {
			int trackX = contentRight() - 3;
			int trackTop = contentTop() + 2;
			int trackH = contentBottom() - contentTop() - 4;
			g.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0xFF20262E);
			int max = lines.size() - vis;
			int barH = Math.max(8, trackH * vis / lines.size());
			int pos = max - consoleScroll;
			int barY = trackTop + (trackH - barH) * pos / Math.max(1, max);
			g.fill(trackX, barY, trackX + 2, barY + barH, ACCENT);
		}
	}

	private void renderStatusBar(GuiGraphics g, ComputerBlockEntity be) {
		int y = topPos + imageHeight - 19;
		String state = be.getClientState();
		int color = switch (state) {
			case "RUNNING" -> 0x55FF55;
			case "ERROR" -> 0xFF6060;
			case "FINISHED" -> 0x55D0FF;
			default -> 0x808890;
		};
		g.drawString(font, "●", leftPos + 8, y, color, false);
		g.drawString(font, state, leftPos + 18, y, color, false);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, "Computer", 8, 5, 0xFFFFFFFF, false);
	}
}
