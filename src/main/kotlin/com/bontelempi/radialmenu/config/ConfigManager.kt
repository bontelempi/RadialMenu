package com.bontelempi.radialmenu.config

import com.bontelempi.radialmenu.model.MenuItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.util.UUID

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val keybindKey: Int = -1,   // GLFW key code, -1 = unbound
    val rootItems: MutableList<MenuItem> = mutableListOf()
)

@Serializable
data class MenuConfig(
    val presets: MutableList<Preset> = mutableListOf(),
    val activePresetIndex: Int = 0,
    val editTipDismissed: Boolean = false,
    // Legacy field for migration
    val rootItems: MutableList<MenuItem>? = null
)

object ConfigManager {

    const val MAX_PRESETS = 10

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val configFile = FabricLoader.getInstance()
        .configDir
        .resolve("radial-menu.json")
        .toFile()

    var config: MenuConfig = MenuConfig()
        private set

    var activePresetIndex: Int = 0
        private set

    val activePreset: Preset
        get() = config.presets.getOrElse(activePresetIndex) {
            config.presets.firstOrNull() ?: Preset(newId(), "Default")
        }

    val rootItems get() = activePreset.rootItems

    fun load() {
        if (!configFile.exists()) {
            config = defaultConfig()
            activePresetIndex = 0
            save()
            return
        }
        runCatching {
            val loaded = json.decodeFromString<MenuConfig>(configFile.readText())
            // Migrate old single-menu format
            config = if (loaded.presets.isEmpty() && loaded.rootItems != null) {
                MenuConfig(
                    presets = mutableListOf(Preset(newId(), "Default", rootItems = loaded.rootItems)),
                    activePresetIndex = 0
                )
            } else {
                loaded
            }
            activePresetIndex = config.activePresetIndex.coerceIn(0, (config.presets.size - 1).coerceAtLeast(0))
        }.onFailure {
            println("[RadialMenu] Failed to load config, resetting: $it")
            config = defaultConfig()
            activePresetIndex = 0
            save()
        }
    }

    fun save() {
        val toSave = config.copy(activePresetIndex = activePresetIndex)
        runCatching {
            Files.createDirectories(configFile.parentFile.toPath())
            configFile.writeText(json.encodeToString(toSave))
        }.onFailure {
            println("[RadialMenu] Failed to save config: $it")
        }
    }

    fun switchPreset(index: Int) {
        activePresetIndex = index.coerceIn(0, config.presets.lastIndex)
        save()
    }

    fun addPreset(name: String): Int {
        if (config.presets.size >= MAX_PRESETS) return -1
        config.presets.add(Preset(newId(), name))
        activePresetIndex = config.presets.lastIndex
        save()
        return activePresetIndex
    }

    fun deletePreset(index: Int) {
        if (config.presets.size <= 1) return // always keep at least one
        config.presets.removeAt(index)
        activePresetIndex = activePresetIndex.coerceIn(0, config.presets.lastIndex)
        save()
    }

    fun renamePreset(index: Int, name: String) {
        val preset = config.presets.getOrNull(index) ?: return
        config.presets[index] = preset.copy(name = name)
        save()
    }

    fun setPresetKeybind(index: Int, keyCode: Int) {
        val preset = config.presets.getOrNull(index) ?: return
        config.presets[index] = preset.copy(keybindKey = keyCode)
        save()
    }

    fun dismissEditTip() {
        config = config.copy(editTipDismissed = true)
        save()
    }

    fun newId() = UUID.randomUUID().toString()

    private fun defaultConfig() = MenuConfig(
        presets = mutableListOf(
            Preset(
                id = newId(),
                name = "Default",
                rootItems = mutableListOf(
                    MenuItem(
                        id = newId(),
                        label = "Warps",
                        iconItem = "minecraft:ender_pearl",
                        children = mutableListOf(
                            MenuItem(newId(), "Hub",         "minecraft:grass_block", "warp hub"),
                            MenuItem(newId(), "Garden",      "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQ4ODBkMmMxZTdiODZlODc1MjJlMjA4ODI2NTZmNDViYWZkNDJmOTQ5MzJiMmM1ZTBkNmVjYWE0OTBjYjRjIn19fQ==", "warp garden"),
                            MenuItem(newId(), "Dungeon Hub", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWI3MzhmYTA1YjI3ZjE4ODc0ZTU3ZDk1YjBiNmM5ZmFkZDFiNTU4ODk0ZjgyZTAyNjFiYTE1NDRlNmZiYmYxZCJ9fX0=", "warp dungeon_hub"),
                            MenuItem(newId(), "Park",        "minecraft:oak_sapling", "warp park"),
                        )
                    ),
                    MenuItem(newId(), "Island",  "minecraft:red_bed",    "warp home"),
                    MenuItem(
                        id = newId(),
                        label = "Party Commands",
                        iconItem = "minecraft:player_head",
                        children = mutableListOf(
                            MenuItem(newId(), "Disband",           "minecraft:tnt",       "p disband"),
                            MenuItem(newId(), "Leave",             "minecraft:barrier",   "p leave"),
                            MenuItem(newId(), "Say \"r\"",         "minecraft:lime_wool", "pc r"),
                            MenuItem(newId(), "Say \"!dt arrows\"","minecraft:arrow",     "pc !dt arrows"),
                        )
                    ),
                    MenuItem(newId(), "Storage", "minecraft:chest", "storage"),
                    MenuItem(
                        id = newId(),
                        label = "Inventory",
                        iconItem = "minecraft:ender_chest",
                        children = mutableListOf(
                            MenuItem(newId(), "Wardrobe", "minecraft:netherite_chestplate", "wd"),
                            MenuItem(newId(), "Pet",      "minecraft:bone",                 "pet"),
                            MenuItem(newId(), "Bags",     "minecraft:bundle",               "bags"),
                        )
                    ),
                )
            )
        ),
        activePresetIndex = 0
    )
}
