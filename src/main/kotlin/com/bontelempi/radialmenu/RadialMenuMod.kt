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
    private lateinit var cycleKey: KeyBinding
    private val presetKeys = mutableListOf<KeyBinding>()

    private val CATEGORY = KeyBinding.Category.create(Identifier.of("radialmenu", "main"))

    override fun onInitializeClient() {
        ConfigManager.load()
        ThemeManager.load()

        // Main open key
        openKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.radialmenu.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
        )

        // Cycle through presets
        cycleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.radialmenu.cycle", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.code, CATEGORY)
        )

        // One keybind slot per preset (max 10), all unbound by default
        for (i in 0 until ConfigManager.MAX_PRESETS) {
            presetKeys.add(
                KeyBindingHelper.registerKeyBinding(
                    KeyBinding("key.radialmenu.preset_${i + 1}", InputUtil.Type.KEYSYM,
                        InputUtil.UNKNOWN_KEY.code, CATEGORY)
                )
            )
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Open menu
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null)
                    client.setScreen(RadialMenuScreen())
            }
            // Cycle preset
            while (cycleKey.wasPressed()) {
                val next = (ConfigManager.activePresetIndex + 1) % ConfigManager.config.presets.size
                ConfigManager.switchPreset(next)
                client.player?.sendMessage(
                    net.minecraft.text.Text.literal("§bRadial Menu: §f${ConfigManager.activePreset.name}"), true
                )
            }
            // Individual preset keys
            presetKeys.forEachIndexed { i, key ->
                while (key.wasPressed()) {
                    if (i < ConfigManager.config.presets.size) {
                        ConfigManager.switchPreset(i)
                        client.player?.sendMessage(
                            net.minecraft.text.Text.literal("§bRadial Menu: §f${ConfigManager.activePreset.name}"), true
                        )
                    }
                }
            }
        }
    }
}
