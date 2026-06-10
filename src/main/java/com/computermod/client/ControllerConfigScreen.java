package com.computermod.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.computermod.client.controller.ControllerHandler;
import com.computermod.content.controller.Binding;
import com.computermod.content.controller.Binding.Mode;
import com.computermod.content.controller.ControllerConfig;
import com.computermod.network.ConfigureControllerC2S;
import com.computermod.network.RequestChannelsC2S;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Configuration GUI for the Channel Controller: a list of editable binding rows. You assign an input
 * by clicking its key button and then pressing the key / mouse button / scroll wheel you want, pick a
 * channel and a mode (analog for scroll, toggle/hold for keys), and Apply saves it back onto the held
 * item. Pure client {@link Screen} — no server-side container needed.
 */
public class ControllerConfigScreen extends Screen {

	private static final String WIKI_URL = "https://oliviermor.github.io/computer-mod/";

	private static final int PANEL_W = 420;
	private static final int HEADER_H = 46;
	private static final int ROW_H = 24;
	private static final int FOOTER_H = 48;
	private static final int MAX_ROWS = 10;

	private final InteractionHand hand;
	private final List<Binding> working = new ArrayList<>();

	// Per-row editable widgets, rebuilt each init().
	private final List<EditBox> channelBoxes = new ArrayList<>();
	private final List<EditBox> minBoxes = new ArrayList<>();
	private final List<EditBox> maxBoxes = new ArrayList<>();
	private final List<EditBox> stepBoxes = new ArrayList<>();

	/** Row currently capturing a "press a key" input, or -1. */
	private int listening = -1;
	private String error = "";

	private ChannelSuggestions suggestions;
	private int refreshTimer = 0;

	private int left, top, panelH;

	public ControllerConfigScreen(InteractionHand hand, ControllerConfig config) {
		super(Component.translatable("computermod.controller.title"));
		this.hand = hand;
		this.working.addAll(config.bindings());
	}

	@Override
	protected void init() {
		channelBoxes.clear();
		minBoxes.clear();
		maxBoxes.clear();
		stepBoxes.clear();

		int rows = working.size();
		panelH = HEADER_H + Math.max(1, rows) * ROW_H + FOOTER_H;
		left = (width - PANEL_W) / 2;
		top = (height - panelH) / 2;

		for (int i = 0; i < rows; i++)
			buildRow(i);

		Button add = Button.builder(Component.translatable("computermod.controller.add"), b -> {
			captureAll();
			working.add(Binding.blank());
			rebuild();
		}).bounds(left + 10, top + panelH - FOOTER_H + 14, 120, 18).build();
		add.active = working.size() < MAX_ROWS;
		addRenderableWidget(add);

		addRenderableWidget(Button.builder(Component.literal("Wiki ↗"), b -> openWiki())
			.bounds(left + PANEL_W - 218, top + panelH - FOOTER_H + 14, 64, 18).build());

		addRenderableWidget(Button.builder(Component.translatable("computermod.controller.apply"), b -> apply())
			.bounds(left + PANEL_W - 144, top + panelH - FOOTER_H + 14, 134, 18).build());

		suggestions = new ChannelSuggestions(font);
		PacketDistributor.sendToServer(RequestChannelsC2S.INSTANCE); // pull the current channel list
	}

	@Override
	public void tick() {
		super.tick();
		if (++refreshTimer >= 20) { // keep the suggestion list fresh while the GUI is open
			refreshTimer = 0;
			PacketDistributor.sendToServer(RequestChannelsC2S.INSTANCE);
		}
	}

	private void buildRow(int i) {
		Binding b = working.get(i);
		int y = top + HEADER_H + i * ROW_H;

		String keyLabel = listening == i ? "> press <" : ControllerHandler.label(b.key());
		addRenderableWidget(Button.builder(Component.literal(keyLabel), btn -> {
			captureAll();
			listening = (listening == i) ? -1 : i;
			error = "";
			rebuild();
		}).bounds(left + 10, y, 80, 18).build());

		EditBox channel = new EditBox(font, left + 96, y, 120, 18, Component.literal("channel"));
		channel.setMaxLength(64);
		channel.setHint(Component.literal("channel"));
		channel.setValue(b.channel());
		addRenderableWidget(channel);
		channelBoxes.add(channel);

		addRenderableWidget(Button.builder(Component.literal(b.mode().getSerializedName()), btn -> {
			captureAll();
			working.set(i, working.get(i).withMode(nextMode(working.get(i))));
			rebuild();
		}).bounds(left + 220, y, 70, 18).build());

		// Analog (scroll) rows get min / max / step boxes; otherwise leave that gap empty.
		EditBox min = null, max = null, step = null;
		if (b.mode() == Mode.ANALOG) {
			min = numberBox(left + 296, y, b.min());
			max = numberBox(left + 326, y, b.max());
			step = numberBox(left + 356, y, b.step());
		}
		minBoxes.add(min);
		maxBoxes.add(max);
		stepBoxes.add(step);

		addRenderableWidget(Button.builder(Component.literal("✕"), btn -> {
			captureAll();
			working.remove(i);
			if (listening == i)
				listening = -1;
			rebuild();
		}).bounds(left + PANEL_W - 28, y, 18, 18).build());
	}

	private EditBox numberBox(int x, int y, double value) {
		EditBox box = new EditBox(font, x, y, 28, 18, Component.literal("n"));
		box.setMaxLength(8);
		box.setValue(trim(value));
		addRenderableWidget(box);
		return box;
	}

	/** Read every row's editable widgets back into {@link #working} before a rebuild or apply. */
	private void captureAll() {
		for (int i = 0; i < working.size() && i < channelBoxes.size(); i++) {
			Binding b = working.get(i).withChannel(channelBoxes.get(i).getValue().trim());
			if (b.mode() == Mode.ANALOG && minBoxes.get(i) != null)
				b = b.withRange(parse(minBoxes.get(i), b.min()), parse(maxBoxes.get(i), b.max()),
					parse(stepBoxes.get(i), b.step()));
			working.set(i, b);
		}
	}

	private void apply() {
		captureAll();
		for (Binding b : working) {
			if (!b.isAssigned()) {
				error = "Every binding needs an input — click its key and press one.";
				return;
			}
			if (b.channel().isEmpty()) {
				error = "Every binding needs a channel name.";
				return;
			}
		}
		PacketDistributor.sendToServer(new ConfigureControllerC2S(hand, new ControllerConfig(working)));
		onClose();
	}

	/** Assign the listening row's input, then stop listening. */
	private void assign(String keyName) {
		captureAll();
		working.set(listening, working.get(listening).withKey(keyName));
		listening = -1;
		error = "";
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	private static Mode nextMode(Binding b) {
		Mode[] allowed = Binding.modesFor(b.key());
		int i = 0;
		for (; i < allowed.length; i++)
			if (allowed[i] == b.mode())
				break;
		return allowed[(i + 1) % allowed.length];
	}

	// --- input capture while "listening" ---

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (listening >= 0) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				listening = -1;
				rebuild();
			} else {
				assign(InputConstants.getKey(keyCode, scanCode).getName());
			}
			return true;
		}
		// Only Escape closes the GUI — otherwise typing in a channel box could exit the screen.
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (suggestions != null && suggestions.keyPressed(keyCode))
			return true;
		if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
			return true;
		return keyCode == GLFW.GLFW_KEY_TAB && super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (listening >= 0) {
			if (button >= 2) // mouse buttons beyond left/right (those are reserved for use/operate)
				assign(InputConstants.Type.MOUSE.getOrCreate(button).getName());
			else {
				listening = -1;
				rebuild();
			}
			return true;
		}
		if (suggestions != null && suggestions.mouseClicked(mouseX, mouseY))
			return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (listening >= 0) {
			assign(Binding.SCROLL);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(left, top, left + PANEL_W, top + panelH, 0xE0101418);
		g.renderOutline(left, top, PANEL_W, panelH, 0xFF3A3F45);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawString(font, title, left + 10, top + 10, 0xFFFFFF, false);
		g.drawString(font, "input       channel                     mode          min  max  step",
			left + 12, top + 30, 0x6B7480, false);

		if (working.isEmpty())
			g.drawString(font, Component.translatable("computermod.controller.empty"),
				left + 12, top + HEADER_H + 4, 0x808080, false);

		// Flag incomplete rows with a red dot in the left margin.
		for (int i = 0; i < working.size(); i++) {
			if (!working.get(i).isValid()) {
				int y = top + HEADER_H + i * ROW_H + 5;
				g.drawString(font, "•", left + 2, y, 0xFF6B6B, false);
			}
		}

		if (!error.isEmpty())
			g.drawString(font, error, left + 12, top + panelH - 16, 0xFF6B6B, false);

		// Channel autocomplete: an existence dot in each channel box, plus a dropdown under the focused
		// one (drawn last so it overlays the rows below it).
		EditBox focused = null;
		for (EditBox box : channelBoxes) {
			ChannelSuggestions.statusDot(g, box);
			if (box.isFocused())
				focused = box;
		}
		if (suggestions != null) {
			suggestions.setBox(focused);
			suggestions.render(g, mouseX, mouseY, height);
		}
	}

	private static double parse(EditBox box, double fallback) {
		try {
			return Double.parseDouble(box.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String trim(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
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
}
