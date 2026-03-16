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
                    MenuItem(newId(), "Disband",        "minecraft:tnt",       "p disband"),
                    MenuItem(newId(), "Leave",          "minecraft:barrier",   "p leave"),
                    MenuItem(newId(), "Say \"r\"",      "minecraft:lime_wool", "pc r"),
                    MenuItem(newId(), "Say \"!dt arrows\"", "minecraft:arrow", "pc !dt arrows"),
                )
            ),
            MenuItem(newId(), "Storage", "minecraft:chest",      "storage"),
            MenuItem(
                id = newId(),
                label = "Call",
                iconItem = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY3Mzk3ODNkZDlhYjBjNDczMGYzYjRlZDU3N2U5NGFhM2ZiOTEyNDZjYWI5MmU1YmU0ZmEyZTVjNWQyNGE4NCJ9fX0=",
                children = mutableListOf(
                    MenuItem(newId(), "Maddox",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMzNmQ3Y2M5NWNiZjY2ODlmNWU4Yzk1NDI5NGVjOGQxZWZjNDk0YTQwMzEzMjViYjQyN2JjODFkNTZhNDg0ZCJ9fX0=", "call maddox"),
                    MenuItem(newId(), "Jax",     "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDNkYWM3YzNjMWU2YzljNzA3Yjg1NWI4NDM3YjEzNWY5M2Y5M2Y5MmEzMmQ0MzAwODIzMmRmMjcxZjg1YmVhIn19fQ==", "call jax"),
                    MenuItem(newId(), "Maxwell", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2I3Y2E3YmE2MzU3ZTRhY2E4NzQyNTA5YjQ3NDU1Mjk2N2VjMjA4YjZlY2Q5YzNiZWEyM2Y5N2Y4NDg1NzM1In19fQ==", "call maxwell"),
                )
            ),
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
}
