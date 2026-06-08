package com.computermod.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(FlashProgramC2S.TYPE, FlashProgramC2S.CODEC, FlashProgramC2S::handle);
		registrar.playToServer(ConfigureChannelC2S.TYPE, ConfigureChannelC2S.CODEC, ConfigureChannelC2S::handle);
		registrar.playToServer(SetChannelValueC2S.TYPE, SetChannelValueC2S.CODEC, SetChannelValueC2S::handle);
		registrar.playToServer(ConfigureControllerC2S.TYPE, ConfigureControllerC2S.CODEC, ConfigureControllerC2S::handle);
		registrar.playToServer(RequestChannelsC2S.TYPE, RequestChannelsC2S.CODEC, RequestChannelsC2S::handle);
		registrar.playToClient(ChannelListS2C.TYPE, ChannelListS2C.CODEC, ChannelListS2C::handle);
	}

	private ModNetwork() {}
}
