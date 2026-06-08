package com.computermod.client.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.computermod.ComputerMod;
import com.computermod.content.controller.Binding;
import com.computermod.content.controller.Binding.Mode;
import com.computermod.content.controller.ChannelControllerItem;
import com.computermod.content.controller.ControllerConfig;
import com.computermod.network.SetChannelValueC2S;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side brain of the Channel Controller. Left-click opens the config screen; right-click toggles
 * "operate" mode. While operating, each bound input drives its channel and its normal game function is
 * suppressed (so a bound W no longer walks), while every <em>unbound</em> input keeps working — the
 * partial-passthrough behaviour that sets this apart from Create's Linked Controller.
 *
 * <p>Inputs are read straight from GLFW each tick, so any key or mouse button can be bound; the matching
 * vanilla key mappings are forced down=false while operating to neutralise their normal effect. Publishes
 * are edge-triggered (a packet only goes out when a binding's value actually changes), keyed by channel.
 */
@EventBusSubscriber(modid = ComputerMod.MODID, value = Dist.CLIENT)
public final class ControllerHandler {

	private static boolean active = false;
	private static InteractionHand hand = InteractionHand.MAIN_HAND;

	/** Last-known pressed state per channel, for press/release edge detection. */
	private static final Map<String, Boolean> down = new HashMap<>();
	/** Sticky toggle state per channel (TOGGLE bindings). */
	private static final Map<String, Boolean> toggled = new HashMap<>();
	/** Current analog value per channel (ANALOG bindings). */
	private static final Map<String, Double> analog = new HashMap<>();
	/** Last value published per channel, so we only send on change. */
	private static final Map<String, Object> lastSent = new HashMap<>();
	/** Channels currently held on by a HOLD binding, so they can be released even after the controller
	 *  leaves the hand (where its config is no longer readable). */
	private static final Set<String> heldChannels = new HashSet<>();
	/** Vanilla key mappings we are currently neutralising, so we can restore them on deactivate. */
	private static final Set<InputConstants.Key> suppressed = new HashSet<>();

	public static boolean isActive() {
		return active;
	}

	/** The latest value published on a channel, for the HUD (null if nothing sent yet). */
	public static Object liveValue(String channel) {
		return lastSent.get(channel);
	}

	/** Toggle operate mode for the controller in {@code usedHand}. */
	public static void toggle(InteractionHand usedHand) {
		if (active) {
			deactivate();
			return;
		}
		hand = usedHand;
		active = true;
		down.clear();
		Player player = Minecraft.getInstance().player;
		if (player != null)
			initialize(config(player)); // publish each channel's starting value right away
	}

	/** Publish every binding's resting value the moment we start operating. */
	private static void initialize(ControllerConfig config) {
		for (Binding b : config.bindings()) {
			if (b.channel().isEmpty())
				continue;
			switch (b.mode()) {
				case ANALOG -> {
					analog.put(b.channel(), b.min());
					send(b.channel(), b.min());
				}
				case TOGGLE -> send(b.channel(), toggled.getOrDefault(b.channel(), false));
				case HOLD -> send(b.channel(), false);
			}
		}
	}

	private static void deactivate() {
		// Release held channels and restore the keys we were neutralising.
		for (String channel : Set.copyOf(heldChannels))
			send(channel, false);
		heldChannels.clear();
		for (InputConstants.Key key : Set.copyOf(suppressed))
			setMappings(key, isPressed(key));
		suppressed.clear();
		active = false;
		down.clear();
		lastSent.clear(); // so re-activating re-publishes fresh starting values
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Pre event) {
		if (!active)
			return;
		Player player = Minecraft.getInstance().player;
		if (player == null || !(heldController(player).getItem() instanceof ChannelControllerItem)) {
			deactivate();
			return;
		}
		// Don't drive inputs while a screen (e.g. the config GUI) is capturing them.
		if (Minecraft.getInstance().screen != null)
			return;

		for (Binding b : config(player).bindings()) {
			if (!b.isValid() || b.isScroll())
				continue; // scroll is handled in onScroll
			InputConstants.Key key = parse(b.key());
			if (key == null)
				continue;
			setMappings(key, false); // neutralise the key's normal game function while operating
			suppressed.add(key);
			applyDigital(b, isPressed(key));
		}
	}

	@SubscribeEvent
	static void onScroll(InputEvent.MouseScrollingEvent event) {
		if (!active || Minecraft.getInstance().screen != null)
			return;
		Player player = Minecraft.getInstance().player;
		if (player == null)
			return;
		double dir = Math.signum(event.getScrollDeltaY());
		if (dir == 0)
			return;
		boolean handled = false;
		for (Binding b : config(player).bindings()) {
			if (!b.isScroll() || b.mode() != Mode.ANALOG || b.channel().isEmpty())
				continue;
			double cur = analog.getOrDefault(b.channel(), b.min());
			double next = clamp(cur + dir * b.step(), b.min(), b.max());
			analog.put(b.channel(), next);
			send(b.channel(), next);
			handled = true;
		}
		if (handled)
			event.setCanceled(true); // don't also scroll the hotbar
	}

	/** Apply a digital binding from the input's current pressed state, edge-detecting press/release. */
	private static void applyDigital(Binding b, boolean pressed) {
		String channel = b.channel();
		boolean was = down.getOrDefault(channel, false);
		down.put(channel, pressed);
		switch (b.mode()) {
			case HOLD -> {
				if (pressed != was) {
					if (pressed)
						heldChannels.add(channel);
					else
						heldChannels.remove(channel);
					send(channel, pressed);
				}
			}
			case TOGGLE -> {
				if (pressed && !was) { // rising edge only
					boolean flipped = !toggled.getOrDefault(channel, false);
					toggled.put(channel, flipped);
					send(channel, flipped);
				}
			}
			default -> {}
		}
	}

	/** Send only when the value changed since last time. */
	private static void send(String channel, Object value) {
		if (value.equals(lastSent.get(channel)))
			return;
		lastSent.put(channel, value);
		if (value instanceof Boolean bool)
			PacketDistributor.sendToServer(SetChannelValueC2S.ofBool(channel, bool));
		else if (value instanceof Number num)
			PacketDistributor.sendToServer(SetChannelValueC2S.ofNumber(channel, num.doubleValue()));
	}

	// --- raw input helpers (client only) ---

	/** Resolve a stored key name to a real key, or null if it isn't a key (empty / scroll / bad name). */
	static InputConstants.Key parse(String name) {
		if (name == null || name.isEmpty() || Binding.SCROLL.equals(name))
			return null;
		try {
			return InputConstants.getKey(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Human-readable label for a stored input, for the config screen and HUD. */
	public static String label(String name) {
		if (name == null || name.isEmpty())
			return "unset";
		if (Binding.SCROLL.equals(name))
			return "Scroll";
		InputConstants.Key key = parse(name);
		return key == null ? name : key.getDisplayName().getString();
	}

	private static boolean isPressed(InputConstants.Key key) {
		long window = Minecraft.getInstance().getWindow().getWindow();
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}

	/** Force every active vanilla key mapping bound to {@code key} to the given pressed state. */
	private static void setMappings(InputConstants.Key key, boolean value) {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
			if (key.equals(mapping.getKey()))
				mapping.setDown(value);
	}

	static ItemStack heldController(Player player) {
		if (player.getMainHandItem().getItem() instanceof ChannelControllerItem)
			return player.getMainHandItem();
		if (player.getOffhandItem().getItem() instanceof ChannelControllerItem)
			return player.getOffhandItem();
		return ItemStack.EMPTY;
	}

	static InteractionHand controllerHand(Player player) {
		return player.getMainHandItem().getItem() instanceof ChannelControllerItem
			? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	private static ControllerConfig config(Player player) {
		ItemStack stack = heldController(player);
		return stack.isEmpty() ? ControllerConfig.EMPTY : ChannelControllerItem.getConfig(stack);
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private ControllerHandler() {}
}
