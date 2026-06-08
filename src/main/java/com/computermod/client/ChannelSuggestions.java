package com.computermod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.computermod.channel.KnownChannels;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

/**
 * A lightweight autocomplete for channel {@link EditBox}es: while a box is focused it drops down a
 * filtered list of the channels that currently exist (synced from the server via {@link KnownChannels}),
 * which can be clicked to fill the box. A small dot in the box flags whether the typed name already
 * exists (green) or would be new (blue). Capped in height so it never floods the screen.
 */
public class ChannelSuggestions {

	private static final int ROW_H = 11;
	private static final int MAX_ROWS = 6;

	private final Font font;
	private EditBox box;
	private final List<String> shown = new ArrayList<>();
	private int x, y, w, rows;
	private boolean visible;

	public ChannelSuggestions(Font font) {
		this.font = font;
	}

	/** Point the dropdown at whichever box is currently focused (or null for none). */
	public void setBox(EditBox box) {
		this.box = box;
	}

	/** Dot inside a channel box: green if the value names an existing channel, blue if it would be new. */
	public static void statusDot(GuiGraphics g, EditBox box) {
		String v = box.getValue().trim();
		if (v.isEmpty())
			return;
		int dx = box.getX() + box.getWidth() - 5;
		int dy = box.getY() + box.getHeight() / 2 - 1;
		g.fill(dx, dy, dx + 3, dy + 3, KnownChannels.exists(v) ? 0xFF6FE36F : 0xFF6FA8FF);
	}

	public void render(GuiGraphics g, int mouseX, int mouseY, int screenHeight) {
		visible = box != null && box.isFocused();
		if (!visible)
			return;

		shown.clear();
		String text = box.getValue().trim().toLowerCase(Locale.ROOT);
		for (String c : KnownChannels.get())
			if (text.isEmpty() || c.toLowerCase(Locale.ROOT).contains(text))
				shown.add(c);
		if (shown.isEmpty()) {
			visible = false;
			return;
		}

		rows = Math.min(MAX_ROWS, shown.size());
		w = Math.max(box.getWidth(), 90);
		x = box.getX();
		int h = rows * ROW_H + 2;
		int below = box.getY() + box.getHeight();
		boolean above = below + h > screenHeight - 2 && box.getY() - h > 0;
		y = above ? box.getY() - h : below;

		g.fill(x, y, x + w, y + h, 0xF00B0E13);
		g.renderOutline(x, y, w, h, 0xFF3A3F45);

		String current = box.getValue().trim();
		for (int i = 0; i < rows; i++) {
			String c = shown.get(i);
			int ry = y + 1 + i * ROW_H;
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H;
			if (hover)
				g.fill(x, ry, x + w, ry + ROW_H, 0x303A6AFF);
			int color = c.equalsIgnoreCase(current) ? 0x6FE36F : 0xC8D0DA;
			g.drawString(font, clip(c, w - 8), x + 4, ry + 2, color, false);
		}
		if (shown.size() > rows)
			g.drawString(font, "+" + (shown.size() - rows) + " more (type to filter)", x + 4, y + h + 1,
				0x6B7480, false);
	}

	/** Returns true if a suggestion was clicked (and applied to the box). */
	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!visible)
			return false;
		int h = rows * ROW_H + 2;
		if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
			int i = (int) ((mouseY - (y + 1)) / ROW_H);
			if (i >= 0 && i < rows) {
				box.setValue(shown.get(i));
				box.setFocused(true);
				return true;
			}
		}
		return false;
	}

	private String clip(String s, int maxWidth) {
		if (font.width(s) <= maxWidth)
			return s;
		while (s.length() > 1 && font.width(s + "…") > maxWidth)
			s = s.substring(0, s.length() - 1);
		return s + "…";
	}
}
