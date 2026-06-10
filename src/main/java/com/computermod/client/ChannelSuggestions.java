package com.computermod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.computermod.channel.ChannelEntry;
import com.computermod.channel.KnownChannels;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import org.lwjgl.glfw.GLFW;

/**
 * Autocomplete for channel {@link EditBox}es. While a box is focused it drops down a filtered list
 * of the channels the server knows about (synced into {@link KnownChannels}), each row showing the
 * channel's name, a preview of its latest value, and coloured tags for who uses it: S sensors,
 * C computers, R receivers, P player controllers. Rows can be clicked, or picked with Up/Down +
 * Enter (route {@link #keyPressed} from the screen). A dot in the box flags whether the typed name
 * already exists (green) or would be new (blue).
 */
public class ChannelSuggestions {

	private static final int ROW_H = 12;
	private static final int MAX_ROWS = 7;

	private static final int COL_BG = 0xF8101620;
	private static final int COL_BORDER = 0xFF333A44;
	private static final int COL_NAME = 0xFFC8D0DA;
	private static final int COL_MATCH = 0xFF6FE36F;
	private static final int COL_PREVIEW = 0xFF6B7480;
	private static final int COL_TAG_SENSOR = 0xFF6FE36F;     // green: world input
	private static final int COL_TAG_COMPUTER = 0xFF4F8CFF;   // blue: computers
	private static final int COL_TAG_RECEIVER = 0xFFFFB04F;   // orange: world output
	private static final int COL_TAG_CONTROLLER = 0xFFE36F9C; // pink: player input

	private final Font font;
	private EditBox box;
	private final List<ChannelEntry> shown = new ArrayList<>();
	private int x, y, w, rows;
	private int selected = -1;
	private boolean visible;

	public ChannelSuggestions(Font font) {
		this.font = font;
	}

	/** Point the dropdown at whichever box is currently focused (or null for none). */
	public void setBox(EditBox box) {
		this.box = box;
		this.selected = -1;
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

	private void refilter() {
		shown.clear();
		if (box == null)
			return;
		String text = box.getValue().trim().toLowerCase(Locale.ROOT);
		for (ChannelEntry e : KnownChannels.get())
			if (text.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(text))
				shown.add(e);
		if (selected >= shown.size())
			selected = shown.isEmpty() ? -1 : 0;
	}

	/**
	 * Up/Down moves the highlight, Enter applies it (Tab applies the top match). Call from the
	 * screen's keyPressed <em>before</em> handing the key to widgets; returns true if consumed.
	 */
	public boolean keyPressed(int keyCode) {
		if (box == null || !box.isFocused())
			return false;
		refilter();
		if (shown.isEmpty())
			return false;
		switch (keyCode) {
			case GLFW.GLFW_KEY_DOWN -> {
				selected = (selected + 1) % Math.min(MAX_ROWS, shown.size());
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				int count = Math.min(MAX_ROWS, shown.size());
				selected = (selected <= 0 ? count : selected) - 1;
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
				int pick = selected >= 0 ? selected : 0;
				if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
					// Only consume Enter when the highlight was actively chosen, so plain typing
					// still lets the screen's default Enter behaviour (e.g. Apply) run.
					if (selected < 0)
						return false;
				}
				apply(shown.get(pick).name());
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void apply(String name) {
		box.setValue(name);
		box.moveCursorToEnd(false);
		box.setFocused(true);
		selected = -1;
	}

	public void render(GuiGraphics g, int mouseX, int mouseY, int screenHeight) {
		visible = box != null && box.isFocused();
		if (!visible)
			return;

		refilter();
		if (shown.isEmpty()) {
			visible = false;
			return;
		}

		rows = Math.min(MAX_ROWS, shown.size());
		w = Math.max(box.getWidth(), 170);
		x = box.getX();
		int h = rows * ROW_H + 2;
		int below = box.getY() + box.getHeight();
		boolean above = below + h > screenHeight - 2 && box.getY() - h > 0;
		y = above ? box.getY() - h : below;

		g.fill(x, y, x + w, y + h, COL_BG);
		g.renderOutline(x, y, w, h, COL_BORDER);

		String current = box.getValue().trim();
		for (int i = 0; i < rows; i++) {
			ChannelEntry e = shown.get(i);
			int ry = y + 1 + i * ROW_H;
			boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H;
			if (hover || i == selected)
				g.fill(x, ry, x + w, ry + ROW_H, i == selected ? 0x504F8CFF : 0x303A6AFF);

			// usage tags, right-aligned: who currently uses this channel
			int tagX = x + w - 3;
			tagX = drawTag(g, tagX, ry, e.controllers(), "P", COL_TAG_CONTROLLER);
			tagX = drawTag(g, tagX, ry, e.receivers(), "R", COL_TAG_RECEIVER);
			tagX = drawTag(g, tagX, ry, e.computers(), "C", COL_TAG_COMPUTER);
			tagX = drawTag(g, tagX, ry, e.sensors(), "S", COL_TAG_SENSOR);

			int nameColor = e.name().equalsIgnoreCase(current) ? COL_MATCH : COL_NAME;
			String name = clip(e.name(), Math.max(20, tagX - x - 8 - 40));
			g.drawString(font, name, x + 4, ry + 2, nameColor, false);

			// dimmed preview of the latest value, between name and tags
			if (!e.preview().isEmpty()) {
				int px = x + 4 + font.width(name) + 5;
				int room = tagX - px - 4;
				if (room > 12)
					g.drawString(font, clip(e.preview(), room), px, ry + 2, COL_PREVIEW, false);
			}
		}
		if (shown.size() > rows)
			g.drawString(font, "+" + (shown.size() - rows) + " more (type to filter)", x + 4, y + h + 2,
				0xFF6B7480, false);
	}

	/** Draw one "S2"-style usage tag right-to-left; returns the new right edge. */
	private int drawTag(GuiGraphics g, int rightX, int rowY, int count, String letter, int color) {
		if (count <= 0)
			return rightX;
		String tag = count == 1 ? letter : letter + count;
		int tw = font.width(tag);
		g.drawString(font, tag, rightX - tw, rowY + 2, color, false);
		return rightX - tw - 4;
	}

	/** Returns true if a suggestion was clicked (and applied to the box). */
	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!visible)
			return false;
		int h = rows * ROW_H + 2;
		if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
			int i = (int) ((mouseY - (y + 1)) / ROW_H);
			if (i >= 0 && i < rows && i < shown.size()) {
				apply(shown.get(i).name());
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
