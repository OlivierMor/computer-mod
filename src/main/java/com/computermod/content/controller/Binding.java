package com.computermod.content.controller;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * One row of a {@link ControllerConfig}: a single physical input mapped to a channel.
 *
 * <p>The input is stored as a plain string so this record stays free of any client-only key classes
 * (it is read on the server too). It is either {@link #SCROLL}, an empty string (unassigned), or an
 * {@code InputConstants} key name like {@code key.keyboard.w} / {@code key.mouse.middle} — the client
 * resolves it to a real key. Digital inputs publish a boolean; the scroll wheel walks a number through
 * {@code [min, max]} in {@code step} increments (so {@code min/max/step} only matter for {@link Mode#ANALOG}).
 */
public record Binding(String key, String channel, Mode mode, double min, double max, double step) {

	/** Sentinel {@link #key} value meaning "the mouse scroll wheel" (the only analog-capable input). */
	public static final String SCROLL = "scroll";

	/** How an input drives its channel. */
	public enum Mode implements StringRepresentable {
		/** Each press flips the channel between {@code false} and {@code true}. */
		TOGGLE("toggle"),
		/** Channel is {@code true} while the key is held, {@code false} when released. */
		HOLD("hold"),
		/** Channel is a number moved through {@code [min, max]} by {@code step} (scroll only). */
		ANALOG("analog");

		public static final Mode[] VALUES = values();
		private final String name;

		Mode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("key", "").forGetter(Binding::key),
		Codec.STRING.fieldOf("channel").forGetter(Binding::channel),
		StringRepresentable.fromEnum(Mode::values).fieldOf("mode").forGetter(Binding::mode),
		Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(Binding::min),
		Codec.DOUBLE.optionalFieldOf("max", 15.0).forGetter(Binding::max),
		Codec.DOUBLE.optionalFieldOf("step", 1.0).forGetter(Binding::step))
		.apply(instance, Binding::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Binding> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, Binding::key,
		ByteBufCodecs.STRING_UTF8, Binding::channel,
		enumStream(Mode.VALUES), Binding::mode,
		ByteBufCodecs.DOUBLE, Binding::min,
		ByteBufCodecs.DOUBLE, Binding::max,
		ByteBufCodecs.DOUBLE, Binding::step,
		Binding::new);

	private static <T extends Enum<T>> StreamCodec<ByteBuf, T> enumStream(T[] values) {
		return ByteBufCodecs.VAR_INT.map(i -> values[i], Enum::ordinal);
	}

	/** The modes that make sense for a given input: analog for the scroll wheel, digital for keys. */
	public static Mode[] modesFor(String key) {
		return SCROLL.equals(key) ? new Mode[] { Mode.ANALOG } : new Mode[] { Mode.TOGGLE, Mode.HOLD };
	}

	/** A blank new row: nothing assigned yet, defaulting to hold. */
	public static Binding blank() {
		return new Binding("", "", Mode.HOLD, 0.0, 15.0, 1.0);
	}

	public boolean isScroll() {
		return SCROLL.equals(key);
	}

	public boolean isAssigned() {
		return !key.isEmpty();
	}

	/** True once this binding is complete enough to actually do something. */
	public boolean isValid() {
		return isAssigned() && !channel.isEmpty();
	}

	public Binding withKey(String key) {
		// Keep the mode consistent with the new input: scroll is analog, keys are digital.
		Mode m = SCROLL.equals(key) ? Mode.ANALOG : (mode == Mode.ANALOG ? Mode.HOLD : mode);
		return new Binding(key, channel, m, min, max, step);
	}

	public Binding withChannel(String channel) {
		return new Binding(key, channel, mode, min, max, step);
	}

	public Binding withMode(Mode mode) {
		return new Binding(key, channel, mode, min, max, step);
	}

	public Binding withRange(double min, double max, double step) {
		return new Binding(key, channel, mode, min, max, step);
	}
}
