package com.behindqou.spear;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class SpearMod implements ClientModInitializer {
    public static final String MOD_ID = "behind_qou_spear";

    @Override
    public void onInitializeClient() {
        SpearKeybind.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SpearKeybind.SPEAR_KEY.wasPressed()) {
                SpearKeybind.toggleSpear();
            }
            SpearEngine.onClientTick(client);
        });
    }
}
