package com.bontelempi.radialmenu.config

import com.bontelempi.radialmenu.model.MenuItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.util.UUID

@Serializable
data class MenuConfig(
    val rootItems: MutableList<MenuItem> = mutableListOf()
)

object ConfigManager {

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

    fun load() {
        if (!configFile.exists()) {
            config = defaultConfig()
            save()
            return
        }
        runCatching {
            config = json.decodeFromString(configFile.readText())
        }.onFailure {
            println("[RadialMenu] Failed to load config, resetting: $it")
            config = defaultConfig()
            save()
        }
    }

    fun save() {
        runCatching {
            Files.createDirectories(configFile.parentFile.toPath())
            configFile.writeText(json.encodeToString(config))
        }.onFailure {
            println("[RadialMenu] Failed to save config: $it")
        }
    }

    fun newId() = UUID.randomUUID().toString()

    // ── Default starter menu ──────────────────────────────────────────────────

    private fun defaultConfig() = MenuConfig(
        rootItems = mutableListOf(
            MenuItem(
                id = newId(),
                label = "Warps",
                iconItem = "minecraft:ender_pearl",
                children = mutableListOf(
                    MenuItem(newId(), "Hub", "minecraft:grass_block", "warp hub"),
                    MenuItem(newId(), "Dungeon Hub", "minecraft:skeleton_skull", "warp dungeon_hub"),
                    MenuItem(newId(), "Park", "minecraft:oak_sapling", "warp park"),
                    MenuItem(newId(), "Farming Islands", "minecraft:wheat", "warp farming_1"),
                )
            ),
            MenuItem(newId(), "Skyblock Menu", "minecraft:nether_star", "sbmenu"),
            MenuItem(newId(), "Warp Home", "minecraft:red_bed", "warp home"),
            MenuItem(newId(), "Co-op", "minecraft:player_head", "coop"),
        )
    )
}
