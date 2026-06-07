package com.computermod.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(FlashProgramC2S.TYPE, FlashProgramC2S.CODEC, FlashProgramC2S::handle);
		registrar.playToServer(ConfigureChannelC2S.TYPE, ConfigureChannelC2S.CODEC, ConfigureChannelC2S::handle);
	}

	private ModNetwork() {}
}
