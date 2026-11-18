package com.behindqou.spear;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpearKeybind {
    public static KeyBinding SPEAR_KEY;

    public static void init() {
        SPEAR_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.behind_qou_spear.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyBinding.Category.create(Identifier.of("behind_qou_spear", "general"))
        ));
    }

    public static boolean isSpearActive() {
        return SpearEngine.isActive;
    }

    public static void toggleSpear() {
        SpearEngine.isActive = !SpearEngine.isActive;
        SpearEngine.reset();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(
                SpearEngine.isActive ? "Spear Kill: ON" : "Spear Kill: OFF"), true);
        }
    }
}
