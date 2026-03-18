package com.bontelempi.radialmenu

import com.bontelempi.radialmenu.config.ConfigManager
import com.bontelempi.radialmenu.config.ThemeManager
import com.bontelempi.radialmenu.screen.RadialMenuScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

object RadialMenuMod : ClientModInitializer {

    private lateinit var openKey: KeyBinding
    private val CATEGORY = KeyBinding.Category.create(Identifier.of("radialmenu", "main"))

    override fun onInitializeClient() {
        ConfigManager.load()
        ThemeManager.load()

        openKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.radialmenu.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null)
                    client.setScreen(RadialMenuScreen())
            }
        }
    }
}
