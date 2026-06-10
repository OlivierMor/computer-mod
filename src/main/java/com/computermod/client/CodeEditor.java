package com.computermod.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * A multi-line code editor widget. Built because vanilla {@code MultiLineEditBox} supports almost
 * none of what editing code needs. Features: cursor + selection (mouse, shift+keys, double-click
 * word, triple-click line), copy/cut/paste, undo/redo, word-wise movement and deletion, Tab/Shift-Tab
 * block indent, Ctrl+/ comment toggle, Ctrl+D line duplication, Lua syntax highlighting, line
 * numbers, search highlighting, and a draggable scrollbar.
 *
 * <p>Text is stored as one string; the cursor and selection anchor are character indices into it.
 * Line boundaries are cached ({@link #lineStarts}) so all index/line math is O(log n) or better.
 */
public class CodeEditor extends AbstractWidget {

	private static final int LINE_H = 10;
	private static final int PAD = 4;
	private static final int SCROLLBAR_W = 4;
	private static final int MAX_UNDO = 256;

	private static final int COL_DEFAULT = 0xFFD4D4D4;
	private static final int COL_KEYWORD = 0xFF569CD6;
	private static final int COL_STRING = 0xFFCE9178;
	private static final int COL_COMMENT = 0xFF6A9955;
	private static final int COL_NUMBER = 0xFF4FC1FF;
	private static final int COL_API = 0xFFDCDCAA;
	private static final int COL_GUTTER = 0xFF5A6470;
	private static final int COL_GUTTER_BG = 0xFF0C1118;
	private static final int COL_BG = 0xFF0E141B;
	private static final int COL_SELECTION = 0x804F8CFF;
	private static final int COL_CURSOR = 0xFFE8ECF0;
	private static final int COL_CURSOR_LINE = 0x14FFFFFF;
	private static final int COL_SEARCH = 0x70E3B341;
	private static final int COL_SEARCH_CURRENT = 0xB0E3B341;

	private static final Set<String> KEYWORDS = Set.of(
		"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in",
		"local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");

	/** The mod's own globals, tinted like library calls so programs read at a glance. */
	private static final Set<String> API_NAMES = Set.of(
		"print", "emit", "channel", "channels", "sleep", "getLocation", "disk", "require",
		"math", "string", "table", "os", "pairs", "ipairs", "tostring", "tonumber", "type",
		"pcall", "error", "assert", "select", "setmetatable", "getmetatable", "next", "unpack");

	private record EditState(String text, int cursor, int anchor) {}

	private final net.minecraft.client.gui.Font font;
	private String text = "";
	private int cursor = 0;
	private int anchor = 0;        // selection anchor; equals cursor when nothing is selected
	private int scrollLine = 0;    // first visible line
	private int scrollX = 0;       // horizontal pixel offset of the text area
	private int charLimit = 120_000;
	private Runnable changeListener;

	// Cached line layout, rebuilt on every text change.
	private List<String> lines = List.of("");
	private int[] lineStarts = { 0 };

	// Undo/redo.
	private final Deque<EditState> undoStack = new ArrayDeque<>();
	private final Deque<EditState> redoStack = new ArrayDeque<>();
	private boolean lastEditCoalescable = false;

	// Mouse click bookkeeping for double/triple click.
	private long lastClickTime = 0;
	private int lastClickIndex = -1;
	private int clickCount = 0;
	private boolean draggingScrollbar = false;

	// Search highlighting (driven by the screen's find bar).
	private String searchQuery = "";
	private final List<Integer> searchMatches = new ArrayList<>();

	public CodeEditor(net.minecraft.client.gui.Font font, int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("Program"));
		this.font = font;
	}

	public void setCharacterLimit(int limit) {
		this.charLimit = limit;
	}

	/** Called after every text mutation (typing, paste, undo, ...). */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	public void setValue(String value) {
		this.text = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
		this.cursor = Math.min(cursor, text.length());
		this.anchor = cursor;
		undoStack.clear();
		redoStack.clear();
		lastEditCoalescable = false;
		rebuildLines();
		refreshSearch();
	}

	public String getValue() {
		return text;
	}

	/** 1-based line of the cursor, for the status bar. */
	public int cursorLine() {
		return lineOf(cursor) + 1;
	}

	/** 1-based column of the cursor, for the status bar. */
	public int cursorColumn() {
		return columnOf(cursor) + 1;
	}

	// --- line cache ---

	private void rebuildLines() {
		List<String> out = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				out.add(text.substring(start, i));
				start = i + 1;
			}
		}
		out.add(text.substring(start));
		lines = out;
		lineStarts = new int[out.size()];
		int acc = 0;
		for (int i = 0; i < out.size(); i++) {
			lineStarts[i] = acc;
			acc += out.get(i).length() + 1;
		}
	}

	/** line number (0-based) containing character index. */
	private int lineOf(int index) {
		int lo = 0, hi = lineStarts.length - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (lineStarts[mid] <= index)
				lo = mid;
			else
				hi = mid - 1;
		}
		return lo;
	}

	private int lineStart(int line) {
		return lineStarts[Mth.clamp(line, 0, lineStarts.length - 1)];
	}

	private int lineEnd(int line) {
		line = Mth.clamp(line, 0, lines.size() - 1);
		return lineStarts[line] + lines.get(line).length();
	}

	private int lineCount() {
		return lines.size();
	}

	private int columnOf(int index) {
		return index - lineStart(lineOf(index));
	}

	// --- editing primitives ---

	private boolean hasSelection() {
		return cursor != anchor;
	}

	private int selStart() {
		return Math.min(cursor, anchor);
	}

	private int selEnd() {
		return Math.max(cursor, anchor);
	}

	private String selectedText() {
		return hasSelection() ? text.substring(selStart(), selEnd()) : "";
	}

	/** Snapshot the current state for undo. Consecutive coalescable edits keep the first snapshot. */
	private void pushUndo(boolean coalescable) {
		if (!(coalescable && lastEditCoalescable)) {
			undoStack.push(new EditState(text, cursor, anchor));
			while (undoStack.size() > MAX_UNDO)
				undoStack.removeLast();
		}
		redoStack.clear();
		lastEditCoalescable = coalescable;
	}

	private void changed() {
		rebuildLines();
		refreshSearch();
		if (changeListener != null)
			changeListener.run();
	}

	/** Replace [from, to) with {@code replacement} and put the cursor after it. */
	private void replaceRange(int from, int to, String replacement) {
		text = text.substring(0, from) + replacement + text.substring(to);
		cursor = from + replacement.length();
		anchor = cursor;
		changed();
	}

	private void insert(String s) {
		int from = selStart();
		int to = selEnd();
		if (text.length() - (to - from) + s.length() > charLimit)
			s = s.substring(0, Math.max(0, charLimit - text.length() + (to - from)));
		replaceRange(from, to, s);
	}

	private void moveTo(int index, boolean select) {
		cursor = Mth.clamp(index, 0, text.length());
		if (!select)
			anchor = cursor;
		lastEditCoalescable = false;
		ensureVisible();
	}

	public void undo() {
		if (undoStack.isEmpty())
			return;
		redoStack.push(new EditState(text, cursor, anchor));
		applyState(undoStack.pop());
	}

	public void redo() {
		if (redoStack.isEmpty())
			return;
		undoStack.push(new EditState(text, cursor, anchor));
		applyState(redoStack.pop());
	}

	private void applyState(EditState state) {
		text = state.text();
		cursor = Mth.clamp(state.cursor(), 0, text.length());
		anchor = Mth.clamp(state.anchor(), 0, text.length());
		lastEditCoalescable = false;
		changed();
		ensureVisible();
	}

	// --- word helpers ---

	private static boolean wordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/** Index of the next word boundary left of {@code index}. */
	private int wordLeft(int index) {
		int i = index;
		while (i > 0 && Character.isWhitespace(text.charAt(i - 1)) && text.charAt(i - 1) != '\n')
			i--;
		if (i > 0 && text.charAt(i - 1) == '\n')
			return i - 1;
		if (i > 0 && wordChar(text.charAt(i - 1)))
			while (i > 0 && wordChar(text.charAt(i - 1)))
				i--;
		else if (i > 0)
			i--;
		return i;
	}

	/** Index of the next word boundary right of {@code index}. */
	private int wordRight(int index) {
		int n = text.length();
		int i = index;
		if (i < n && text.charAt(i) == '\n')
			return i + 1;
		if (i < n && wordChar(text.charAt(i)))
			while (i < n && wordChar(text.charAt(i)))
				i++;
		else if (i < n && !Character.isWhitespace(text.charAt(i)))
			i++;
		while (i < n && Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '\n')
			i++;
		return i;
	}

	// --- search ---

	/** Set (or clear, with "") the highlighted search query. Case-insensitive. */
	public void setSearchQuery(String query) {
		this.searchQuery = query == null ? "" : query;
		refreshSearch();
	}

	public int searchMatchCount() {
		return searchMatches.size();
	}

	private void refreshSearch() {
		searchMatches.clear();
		if (searchQuery.isEmpty())
			return;
		String haystack = text.toLowerCase(Locale.ROOT);
		String needle = searchQuery.toLowerCase(Locale.ROOT);
		int from = 0;
		while (true) {
			int at = haystack.indexOf(needle, from);
			if (at < 0 || searchMatches.size() >= 2000)
				break;
			searchMatches.add(at);
			from = at + Math.max(1, needle.length());
		}
	}

	/** Jump to and select the next/previous match relative to the cursor. Returns true if one exists. */
	public boolean findNext(boolean forward) {
		if (searchMatches.isEmpty())
			return false;
		int target = -1;
		if (forward) {
			for (int at : searchMatches)
				if (at >= (hasSelection() ? selStart() + 1 : cursor)) {
					target = at;
					break;
				}
			if (target < 0)
				target = searchMatches.get(0); // wrap
		} else {
			for (int i = searchMatches.size() - 1; i >= 0; i--)
				if (searchMatches.get(i) < selStart()) {
					target = searchMatches.get(i);
					break;
				}
			if (target < 0)
				target = searchMatches.get(searchMatches.size() - 1); // wrap
		}
		anchor = target;
		cursor = target + searchQuery.length();
		ensureVisible();
		return true;
	}

	// --- input ---

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (!isFocused() || c < 32 || c == 127)
			return false;
		pushUndo(true);
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
				case GLFW.GLFW_KEY_X -> {
					if (hasSelection()) {
						mc.keyboardHandler.setClipboard(selectedText());
						pushUndo(false);
						insert("");
						ensureVisible();
					}
					return true;
				}
				case GLFW.GLFW_KEY_V -> {
					pushUndo(false);
					insert(mc.keyboardHandler.getClipboard().replace("\r\n", "\n").replace('\r', '\n'));
					ensureVisible();
					return true;
				}
				case GLFW.GLFW_KEY_Z -> { if (shift) redo(); else undo(); return true; }
				case GLFW.GLFW_KEY_Y -> { redo(); return true; }
				case GLFW.GLFW_KEY_D -> { duplicateSelection(); return true; }
				case GLFW.GLFW_KEY_SLASH -> { toggleComment(); return true; }
				case GLFW.GLFW_KEY_LEFT -> { moveTo(wordLeft(cursor), shift); return true; }
				case GLFW.GLFW_KEY_RIGHT -> { moveTo(wordRight(cursor), shift); return true; }
				case GLFW.GLFW_KEY_HOME -> { moveTo(0, shift); return true; }
				case GLFW.GLFW_KEY_END -> { moveTo(text.length(), shift); return true; }
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!hasSelection())
						anchor = wordLeft(cursor);
					pushUndo(false);
					insert("");
					ensureVisible();
					return true;
				}
				case GLFW.GLFW_KEY_DELETE -> {
					if (!hasSelection())
						cursor = wordRight(cursor);
					pushUndo(false);
					insert("");
					ensureVisible();
					return true;
				}
				default -> { return false; }
			}
		}

		switch (key) {
			case GLFW.GLFW_KEY_LEFT -> {
				if (hasSelection() && !shift)
					moveTo(selStart(), false);
				else
					moveTo(cursor - 1, shift);
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (hasSelection() && !shift)
					moveTo(selEnd(), false);
				else
					moveTo(cursor + 1, shift);
			}
			case GLFW.GLFW_KEY_UP -> moveVertical(-1, shift);
			case GLFW.GLFW_KEY_DOWN -> moveVertical(1, shift);
			case GLFW.GLFW_KEY_PAGE_UP -> moveVertical(-Math.max(1, visibleRows() - 1), shift);
			case GLFW.GLFW_KEY_PAGE_DOWN -> moveVertical(Math.max(1, visibleRows() - 1), shift);
			case GLFW.GLFW_KEY_HOME -> moveTo(firstNonSpaceOrStart(lineOf(cursor)), shift);
			case GLFW.GLFW_KEY_END -> moveTo(lineEnd(lineOf(cursor)), shift);
			case GLFW.GLFW_KEY_BACKSPACE -> {
				pushUndo(true);
				if (!hasSelection() && cursor > 0)
					anchor = cursor - 1;
				insert("");
				ensureVisible();
			}
			case GLFW.GLFW_KEY_DELETE -> {
				pushUndo(false);
				if (!hasSelection() && cursor < text.length())
					cursor = cursor + 1;
				insert("");
				ensureVisible();
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				pushUndo(false);
				insert("\n" + newlineIndent());
				ensureVisible();
			}
			case GLFW.GLFW_KEY_TAB -> {
				if (shift || spansMultipleLines())
					indentSelection(!shift);
				else {
					pushUndo(false);
					insert("  ");
				}
				ensureVisible();
			}
			default -> { return false; }
		}
		return true;
	}

	/** Home goes to the first non-space character, or column 0 if already there (editor convention). */
	private int firstNonSpaceOrStart(int line) {
		int start = lineStart(line);
		String s = lines.get(line);
		int i = 0;
		while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'))
			i++;
		int firstNonSpace = start + i;
		return cursor == firstNonSpace ? start : firstNonSpace;
	}

	private boolean spansMultipleLines() {
		return hasSelection() && lineOf(selStart()) != lineOf(selEnd());
	}

	/** Indent (or unindent) every line the selection touches by two spaces. */
	private void indentSelection(boolean indent) {
		pushUndo(false);
		int firstLine = lineOf(selStart());
		int lastLine = lineOf(hasSelection() ? selEnd() - 1 : selEnd());
		int a = selStart();
		int b = selEnd();
		StringBuilder out = new StringBuilder(text.length() + 64);
		out.append(text, 0, lineStart(firstLine));
		for (int line = firstLine; line <= lastLine; line++) {
			String s = lines.get(line);
			int delta;
			if (indent) {
				out.append("  ").append(s);
				delta = 2;
			} else {
				int strip = Math.min(2, countLeadingSpaces(s));
				out.append(s, strip, s.length());
				delta = -strip;
			}
			if (line < lineCount() - 1)
				out.append('\n');
			if (line == firstLine) {
				a = Math.max(lineStart(firstLine), a + delta);
			}
			b += delta;
		}
		int tailStart = lineEnd(lastLine) + 1;
		if (tailStart <= text.length())
			out.append(text, Math.min(tailStart, text.length()), text.length());
		text = out.toString();
		changed();
		anchor = Mth.clamp(a, 0, text.length());
		cursor = Mth.clamp(b, 0, text.length());
		ensureVisible();
	}

	private static int countLeadingSpaces(String s) {
		int i = 0;
		while (i < s.length() && s.charAt(i) == ' ')
			i++;
		return i;
	}

	/** Ctrl+/: comment the selected lines with "-- ", or uncomment if they all already are. */
	private void toggleComment() {
		pushUndo(false);
		int firstLine = lineOf(selStart());
		int lastLine = lineOf(hasSelection() ? selEnd() - 1 : selEnd());
		boolean allCommented = true;
		for (int line = firstLine; line <= lastLine; line++) {
			String t = lines.get(line).trim();
			if (!t.isEmpty() && !t.startsWith("--")) {
				allCommented = false;
				break;
			}
		}
		StringBuilder out = new StringBuilder(text.length() + 64);
		out.append(text, 0, lineStart(firstLine));
		for (int line = firstLine; line <= lastLine; line++) {
			String s = lines.get(line);
			if (allCommented) {
				int at = s.indexOf("--");
				if (at >= 0) {
					int cut = at + 2;
					if (cut < s.length() && s.charAt(cut) == ' ')
						cut++;
					s = s.substring(0, at) + s.substring(cut);
				}
			} else if (!s.trim().isEmpty()) {
				s = "-- " + s;
			}
			out.append(s);
			if (line < lineCount() - 1)
				out.append('\n');
		}
		int tailStart = lineEnd(lastLine) + 1;
		if (tailStart <= text.length())
			out.append(text, Math.min(tailStart, text.length()), text.length());
		int anchorLineStart = lineStart(firstLine);
		text = out.toString();
		changed();
		// Select the whole affected block; precise per-line cursor math isn't worth the complexity.
		anchor = anchorLineStart;
		cursor = Math.min(text.length(), lineEnd(Math.min(lastLine, lineCount() - 1)));
		ensureVisible();
	}

	/** Ctrl+D: duplicate the selection, or the whole current line when nothing is selected. */
	private void duplicateSelection() {
		pushUndo(false);
		if (hasSelection()) {
			String sel = selectedText();
			int end = selEnd();
			text = text.substring(0, end) + sel + text.substring(end);
			anchor = end;
			cursor = end + sel.length();
		} else {
			int line = lineOf(cursor);
			String s = lines.get(line);
			int insertAt = lineEnd(line);
			text = text.substring(0, insertAt) + "\n" + s + text.substring(insertAt);
			cursor = Math.min(text.length(), cursor + s.length() + 1);
			anchor = cursor;
		}
		changed();
		ensureVisible();
	}

	/** Indentation for a new line: copy the current line's leading spaces, +2 if it opens a block. */
	private String newlineIndent() {
		int line = lineOf(Math.min(cursor, anchor));
		String full = lines.get(line);
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

	private void moveVertical(int dir, boolean select) {
		int line = lineOf(cursor);
		int col = columnOf(cursor);
		int target = Mth.clamp(line + dir, 0, lineCount() - 1);
		if (target == line)
			return;
		moveTo(Math.min(lineStart(target) + col, lineEnd(target)), select);
	}

	// --- mouse ---

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!active || !visible || !isMouseOver(mouseX, mouseY))
			return false;
		setFocused(true);

		// scrollbar hit?
		if (mouseX >= getX() + width - SCROLLBAR_W - 2 && lineCount() > visibleRows()) {
			draggingScrollbar = true;
			scrollToMouse(mouseY);
			return true;
		}

		int idx = indexAt(mouseX, mouseY);
		long now = System.currentTimeMillis();
		clickCount = (now - lastClickTime < 350 && idx == lastClickIndex) ? clickCount + 1 : 1;
		lastClickTime = now;
		lastClickIndex = idx;

		if (clickCount >= 3) {
			// triple click: whole line (newline included, so repeated copies stack)
			int line = lineOf(idx);
			anchor = lineStart(line);
			cursor = Math.min(text.length(), lineEnd(line) + 1);
		} else if (clickCount == 2) {
			// double click: word under cursor
			int a = idx, b = idx;
			while (a > 0 && wordChar(text.charAt(a - 1)))
				a--;
			while (b < text.length() && wordChar(text.charAt(b)))
				b++;
			anchor = a;
			cursor = b;
		} else {
			boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
			cursor = idx;
			if (!shift)
				anchor = idx;
		}
		lastEditCoalescable = false;
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (!isFocused())
			return false;
		if (draggingScrollbar) {
			scrollToMouse(mouseY);
			return true;
		}
		cursor = indexAt(mouseX, mouseY);
		ensureVisible();
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingScrollbar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void scrollToMouse(double mouseY) {
		int rows = visibleRows();
		int max = Math.max(0, lineCount() - rows);
		double f = (mouseY - getY() - PAD) / Math.max(1, height - 2 * PAD);
		scrollLine = Mth.clamp((int) Math.round(f * max), 0, max);
	}

	private int indexAt(double mouseX, double mouseY) {
		int gutter = gutterWidth(lineCount());
		int line = Mth.clamp(scrollLine + (int) ((mouseY - getY() - PAD) / LINE_H), 0, lineCount() - 1);
		String s = lines.get(line);
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
		if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
			scrollX = Math.max(0, scrollX - (int) Math.signum(sy) * 24);
			return true;
		}
		int rows = visibleRows();
		scrollLine = Mth.clamp(scrollLine - (int) Math.signum(sy) * 3, 0, Math.max(0, lineCount() - rows));
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
		String s = lines.get(line);
		int caretX = font.width(s.substring(0, Math.min(col, s.length())));
		int viewW = width - gutterWidth(lineCount()) - 2 * PAD - SCROLLBAR_W;
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
		int gutter = gutterWidth(lineCount());
		int x = getX();
		int y = getY();
		int rows = visibleRows();
		scrollLine = Mth.clamp(scrollLine, 0, Math.max(0, lineCount() - rows));

		g.fill(x, y, x + width, y + height, COL_BG);
		g.renderOutline(x, y, width, height, isFocused() ? 0xFF4F8CFF : 0xFF333A44);

		int textX = x + gutter + PAD;
		int cursorLine = lineOf(cursor);
		g.enableScissor(textX, y + 1, x + width - SCROLLBAR_W - 1, y + height - 1);
		int selA = selStart();
		int selB = selEnd();
		for (int row = 0; row < rows; row++) {
			int line = scrollLine + row;
			if (line >= lineCount())
				break;
			int ly = y + PAD + row * LINE_H;
			String s = lines.get(line);
			int start = lineStart(line);
			int end = start + s.length();

			// subtle band on the cursor's line
			if (line == cursorLine && !hasSelection() && isFocused())
				g.fill(textX - PAD, ly - 1, x + width - SCROLLBAR_W - 1, ly + LINE_H - 1, COL_CURSOR_LINE);

			// search match highlights
			if (!searchQuery.isEmpty()) {
				for (int at : searchMatches) {
					if (at > end)
						break;
					int matchEnd = at + searchQuery.length();
					if (matchEnd <= start)
						continue;
					int a = Mth.clamp(at - start, 0, s.length());
					int b = Mth.clamp(matchEnd - start, 0, s.length());
					if (a >= b)
						continue;
					int sx = textX - scrollX + font.width(s.substring(0, a));
					int ex = textX - scrollX + font.width(s.substring(0, b));
					boolean current = hasSelection() && selA == at && selB == matchEnd;
					g.fill(sx, ly - 1, ex, ly + LINE_H - 1, current ? COL_SEARCH_CURRENT : COL_SEARCH);
				}
			}

			// selection highlight: overlap of [selA, selB) with this line (+1 for the newline)
			if (selA != selB && selA <= end && selB >= start) {
				int a = Mth.clamp(selA - start, 0, s.length());
				int b = Mth.clamp(selB - start, 0, s.length());
				int sx = textX - scrollX + font.width(s.substring(0, a));
				int ex = textX - scrollX + font.width(s.substring(0, b));
				if (selB > end)
					ex += 4; // show the trailing newline as selected
				if (ex > sx)
					g.fill(sx, ly - 1, ex, ly + LINE_H - 1, COL_SELECTION);
			}

			drawHighlighted(g, s, textX - scrollX, ly);

			// cursor
			if (isFocused() && line == cursorLine && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = textX - scrollX + font.width(s.substring(0, Math.min(columnOf(cursor), s.length())));
				g.fill(cx, ly - 1, cx + 1, ly + LINE_H - 1, COL_CURSOR);
			}
		}
		g.disableScissor();

		// gutter on top so scrolled text never bleeds under the line numbers
		g.fill(x + 1, y + 1, x + gutter, y + height - 1, COL_GUTTER_BG);
		for (int row = 0; row < rows; row++) {
			int line = scrollLine + row;
			if (line >= lineCount())
				break;
			String num = String.valueOf(line + 1);
			int color = line == cursorLine ? 0xFF9AA6B5 : COL_GUTTER;
			g.drawString(font, num, x + gutter - 4 - font.width(num), y + PAD + row * LINE_H, color, false);
		}

		// scrollbar
		if (lineCount() > rows) {
			int trackX = x + width - SCROLLBAR_W - 1;
			int trackTop = y + 1;
			int trackH = height - 2;
			g.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, 0xFF161C24);
			int barH = Math.max(10, trackH * rows / lineCount());
			int max = lineCount() - rows;
			int barY = trackTop + (trackH - barH) * scrollLine / Math.max(1, max);
			boolean hot = draggingScrollbar || (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_W
				&& mouseY >= trackTop && mouseY <= trackTop + trackH);
			g.fill(trackX, barY, trackX + SCROLLBAR_W, barY + barH, hot ? 0xFF4F8CFF : 0xFF333A44);
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
				String word = s.substring(start, i);
				color = KEYWORDS.contains(word) ? COL_KEYWORD
					: API_NAMES.contains(word) ? COL_API : COL_DEFAULT;
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
