package com.computermod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * A small multi-line code editor: cursor + selection, copy/cut/paste, Tab (2 spaces), line numbers,
 * Lua syntax highlighting, and vertical/horizontal scrolling. Built because vanilla
 * {@code MultiLineEditBox} supports none of selection/tab/highlighting well.
 *
 * <p>Text is stored as one string; the cursor and selection anchor are character indices into it.
 */
public class CodeEditor extends AbstractWidget {

	private static final int LINE_H = 10;
	private static final int PAD = 4;

	private static final int COL_DEFAULT = 0xFFD4D4D4;
	private static final int COL_KEYWORD = 0xFF569CD6;
	private static final int COL_STRING = 0xFFCE9178;
	private static final int COL_COMMENT = 0xFF6A9955;
	private static final int COL_NUMBER = 0xFF4FC1FF;
	private static final int COL_GUTTER = 0xFF5A6470;
	private static final int COL_GUTTER_BG = 0xFF0C1118;
	private static final int COL_BG = 0xFF0E141B;
	private static final int COL_SELECTION = 0x804F8CFF;
	private static final int COL_CURSOR = 0xFFE8ECF0;

	private static final Set<String> KEYWORDS = Set.of(
		"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in",
		"local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");

	private final net.minecraft.client.gui.Font font;
	private String text = "";
	private int cursor = 0;
	private int anchor = 0;        // selection anchor; equals cursor when nothing is selected
	private int scrollLine = 0;    // first visible line
	private int scrollX = 0;       // horizontal pixel offset of the text area
	private int charLimit = 60_000;

	public CodeEditor(net.minecraft.client.gui.Font font, int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("Program"));
		this.font = font;
	}

	public void setCharacterLimit(int limit) {
		this.charLimit = limit;
	}

	public void setValue(String value) {
		this.text = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
		this.cursor = Math.min(cursor, text.length());
		this.anchor = cursor;
	}

	public String getValue() {
		return text;
	}

	// --- line helpers ---

	private List<String> lines() {
		List<String> out = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				out.add(text.substring(start, i));
				start = i + 1;
			}
		}
		out.add(text.substring(start));
		return out;
	}

	/** line number (0-based) containing character index. */
	private int lineOf(int index) {
		int line = 0;
		for (int i = 0; i < index && i < text.length(); i++)
			if (text.charAt(i) == '\n')
				line++;
		return line;
	}

	private int lineStart(int line) {
		if (line <= 0)
			return 0;
		int seen = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				seen++;
				if (seen == line)
					return i + 1;
			}
		}
		return text.length();
	}

	private int columnOf(int index) {
		return index - lineStart(lineOf(index));
	}

	// --- editing ---

	private boolean hasSelection() {
		return cursor != anchor;
	}

	private void deleteSelection() {
		if (!hasSelection())
			return;
		int a = Math.min(cursor, anchor);
		int b = Math.max(cursor, anchor);
		text = text.substring(0, a) + text.substring(b);
		cursor = a;
		anchor = a;
	}

	private void insert(String s) {
		deleteSelection();
		if (text.length() + s.length() > charLimit)
			s = s.substring(0, Math.max(0, charLimit - text.length()));
		text = text.substring(0, cursor) + s + text.substring(cursor);
		cursor += s.length();
		anchor = cursor;
	}

	private String selectedText() {
		if (!hasSelection())
			return "";
		return text.substring(Math.min(cursor, anchor), Math.max(cursor, anchor));
	}

	private void moveTo(int index, boolean select) {
		cursor = Mth.clamp(index, 0, text.length());
		if (!select)
			anchor = cursor;
		ensureVisible();
	}

	// --- input ---

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (!isFocused() || c < 32 || c == 127)
			return false;
		insert(String.valueOf(c));
		ensureVisible();
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (!isFocused())
			return false;
		boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_SUPER) != 0;
		boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
		Minecraft mc = Minecraft.getInstance();

		if (ctrl) {
			switch (key) {
				case GLFW.GLFW_KEY_A -> { anchor = 0; cursor = text.length(); return true; }
				case GLFW.GLFW_KEY_C -> { if (hasSelection()) mc.keyboardHandler.setClipboard(selectedText()); return true; }
				case GLFW.GLFW_KEY_X -> { if (hasSelection()) { mc.keyboardHandler.setClipboard(selectedText()); deleteSelection(); ensureVisible(); } return true; }
				case GLFW.GLFW_KEY_V -> { insert(mc.keyboardHandler.getClipboard().replace("\r\n", "\n").replace('\r', '\n')); ensureVisible(); return true; }
				default -> { return false; }
			}
		}

		switch (key) {
			case GLFW.GLFW_KEY_LEFT -> moveTo(cursor - 1, shift);
			case GLFW.GLFW_KEY_RIGHT -> moveTo(cursor + 1, shift);
			case GLFW.GLFW_KEY_UP -> moveVertical(-1, shift);
			case GLFW.GLFW_KEY_DOWN -> moveVertical(1, shift);
			case GLFW.GLFW_KEY_HOME -> moveTo(lineStart(lineOf(cursor)), shift);
			case GLFW.GLFW_KEY_END -> moveTo(lineEnd(lineOf(cursor)), shift);
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (hasSelection()) deleteSelection();
				else if (cursor > 0) { text = text.substring(0, cursor - 1) + text.substring(cursor); cursor--; anchor = cursor; }
				ensureVisible();
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (hasSelection()) deleteSelection();
				else if (cursor < text.length()) text = text.substring(0, cursor) + text.substring(cursor + 1);
				ensureVisible();
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { insert("\n" + newlineIndent()); ensureVisible(); }
			case GLFW.GLFW_KEY_TAB -> { insert("  "); ensureVisible(); }
			default -> { return false; }
		}
		return true;
	}

	/** Indentation for a new line: copy the current line's leading spaces, +2 if it opens a block. */
	private String newlineIndent() {
		int line = lineOf(Math.min(cursor, anchor));
		String full = lines().get(line);
		int w = 0;
		while (w < full.length() && (full.charAt(w) == ' ' || full.charAt(w) == '\t'))
			w++;
		String indent = full.substring(0, w);
		if (opensBlock(full.trim()))
			indent += "  ";
		return indent;
	}

	private static boolean opensBlock(String t) {
		if (t.endsWith("(") || t.endsWith("{"))
			return true;
		if (t.equals("do") || t.endsWith(" do"))
			return true;
		if (t.equals("then") || t.endsWith(" then"))
			return true;
		if (t.equals("else") || t.endsWith(" else"))
			return true;
		if (t.equals("repeat") || t.endsWith(" repeat"))
			return true;
		if (t.equals("function") || t.endsWith(" function"))
			return true;
		return t.endsWith(")") && (t.startsWith("function") || t.contains("function "));
	}

	private int lineEnd(int line) {
		int start = lineStart(line);
		int i = start;
		while (i < text.length() && text.charAt(i) != '\n')
			i++;
		return i;
	}

	private void moveVertical(int dir, boolean select) {
		int line = lineOf(cursor);
		int col = columnOf(cursor);
		int target = line + dir;
		if (target < 0 || target > lineOf(text.length()))
			return;
		int ts = lineStart(target);
		int te = lineEnd(target);
		moveTo(Math.min(ts + col, te), select);
	}

	// --- mouse ---

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY))
			return false;
		setFocused(true);
		int idx = indexAt(mouseX, mouseY);
		cursor = idx;
		anchor = idx;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (!isFocused())
			return false;
		cursor = indexAt(mouseX, mouseY);
		ensureVisible();
		return true;
	}

	private int indexAt(double mouseX, double mouseY) {
		List<String> ls = lines();
		int gutter = gutterWidth(ls.size());
		int line = Mth.clamp(scrollLine + (int) ((mouseY - getY() - PAD) / LINE_H), 0, ls.size() - 1);
		String s = ls.get(line);
		int px = (int) (mouseX - (getX() + gutter + PAD) + scrollX);
		int col = 0;
		int acc = 0;
		while (col < s.length()) {
			int w = font.width(s.substring(col, col + 1));
			if (acc + w / 2 >= px)
				break;
			acc += w;
			col++;
		}
		return lineStart(line) + col;
	}

	// --- scrolling ---

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
		List<String> ls = lines();
		int rows = visibleRows();
		scrollLine = Mth.clamp(scrollLine - (int) Math.signum(sy) * 3, 0, Math.max(0, ls.size() - rows));
		return true;
	}

	private int visibleRows() {
		return (height - 2 * PAD) / LINE_H;
	}

	private int gutterWidth(int lineCount) {
		return font.width(String.valueOf(Math.max(10, lineCount))) + 8;
	}

	private void ensureVisible() {
		int line = lineOf(cursor);
		int rows = visibleRows();
		if (line < scrollLine)
			scrollLine = line;
		else if (line >= scrollLine + rows)
			scrollLine = line - rows + 1;

		int col = columnOf(cursor);
		String s = lines().get(line);
		int caretX = font.width(s.substring(0, Math.min(col, s.length())));
		int viewW = width - gutterWidth(lines().size()) - 2 * PAD;
		if (caretX - scrollX < 0)
			scrollX = caretX;
		else if (caretX - scrollX > viewW - 6)
			scrollX = caretX - viewW + 6;
		if (scrollX < 0)
			scrollX = 0;
	}

	// --- rendering ---

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		List<String> ls = lines();
		int gutter = gutterWidth(ls.size());
		int x = getX();
		int y = getY();
		int rows = visibleRows();

		g.fill(x, y, x + width, y + height, COL_BG);
		g.renderOutline(x, y, width, height, isFocused() ? 0xFF4F8CFF : 0xFF333A44);

		int textX = x + gutter + PAD;
		g.enableScissor(textX, y + PAD, x + width - PAD, y + height - PAD);
		int selA = Math.min(cursor, anchor);
		int selB = Math.max(cursor, anchor);
		for (int row = 0; row < rows; row++) {
			int line = scrollLine + row;
			if (line >= ls.size())
				break;
			int ly = y + PAD + row * LINE_H;
			String s = ls.get(line);
			int lineStartIdx = lineStart(line);

			// selection highlight
			if (selA != selB) {
				int a = Mth.clamp(selA - lineStartIdx, 0, s.length());
				int b = Mth.clamp(selB - lineStartIdx, 0, s.length());
				boolean lineSelected = selA <= lineEnd(line) && selB >= lineStartIdx;
				if (lineSelected && (a < b || (selA < lineStartIdx && selB > lineEnd(line)) || selB > lineEnd(line))) {
					int sx = textX - scrollX + font.width(s.substring(0, a));
					int ex = textX - scrollX + font.width(s.substring(0, b));
					if (selB > lineEnd(line))
						ex += 4; // show the trailing newline as selected
					g.fill(sx, ly, Math.max(sx + 1, ex), ly + LINE_H, COL_SELECTION);
				}
			}

			drawHighlighted(g, s, textX - scrollX, ly);

			// cursor
			if (isFocused() && line == lineOf(cursor)) {
				int cx = textX - scrollX + font.width(s.substring(0, Math.min(columnOf(cursor), s.length())));
				g.fill(cx, ly - 1, cx + 1, ly + LINE_H - 1, COL_CURSOR);
			}
		}
		g.disableScissor();

		// gutter on top so scrolled text never bleeds under the line numbers
		g.fill(x + 1, y + 1, x + gutter, y + height - 1, COL_GUTTER_BG);
		for (int row = 0; row < rows; row++) {
			int line = scrollLine + row;
			if (line >= ls.size())
				break;
			String num = String.valueOf(line + 1);
			g.drawString(font, num, x + gutter - 4 - font.width(num), y + PAD + row * LINE_H, COL_GUTTER, false);
		}
	}

	/** Draw one line with simple Lua syntax colouring. */
	private void drawHighlighted(GuiGraphics g, String s, int x, int y) {
		int i = 0;
		int px = x;
		int n = s.length();
		while (i < n) {
			char c = s.charAt(i);
			int start = i;
			int color;
			if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
				i = n;
				color = COL_COMMENT;
			} else if (c == '"' || c == '\'') {
				i++;
				while (i < n && s.charAt(i) != c) {
					if (s.charAt(i) == '\\')
						i++;
					i++;
				}
				if (i < n)
					i++;
				color = COL_STRING;
			} else if (Character.isLetter(c) || c == '_') {
				while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_'))
					i++;
				color = KEYWORDS.contains(s.substring(start, i)) ? COL_KEYWORD : COL_DEFAULT;
			} else if (Character.isDigit(c)) {
				while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.'))
					i++;
				color = COL_NUMBER;
			} else {
				i++;
				color = COL_DEFAULT;
			}
			String seg = s.substring(start, i);
			g.drawString(font, seg, px, y, color, false);
			px += font.width(seg);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Code editor"));
	}
}
