package com.godmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GodModClient implements ClientModInitializer {
    public static KeyBinding TECLA_MENU;

    @Override
    public void onInitializeClient() {
        TECLA_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.godmod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.godmod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TECLA_MENU.wasPressed()) {
                if (client.player == null || client.currentScreen != null) continue;

                boolean comCetro = client.player.getMainHandStack().isOf(GodMod.CETRO)
                    || client.player.getOffHandStack().isOf(GodMod.CETRO);

                if (comCetro) {
                    client.setScreen(new MenuRadialScreen());
                } else {
                    client.player.sendMessage(Text.literal("Segure o Cetro da Realidade para abrir o menu."), true);
                }
            }
        });
    }
}
