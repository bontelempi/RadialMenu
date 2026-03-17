package com.bontelempi.radialmenu.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

@Serializable
data class ThemeColors(
    val backgroundDim:  Long = 0x77_000000L,
    val ring0:          Long = 0xCC_1A1A2EL,
    val ring0Folder:    Long = 0xCC_0F1633L,
    val ring1:          Long = 0xD1_0F2033L,
    val ring2:          Long = 0xD1_0A1A2AL,
    val ring3:          Long = 0xD1_071520L,
    val hoverHighlight: Long = 0xD9_E94560L,
    val centerCircle:   Long = 0x00_000000L,
    val textPrimary:    Long = 0xFF_FFFFFFL,
    val textDim:        Long = 0xFF_888899L,
) {
    // Legacy compat aliases used by RadialMenuScreen
    val innerSegment  get() = ring0
    val innerFolder   get() = ring0Folder
    val outerSegment  get() = ring1
}

object ThemeManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = FabricLoader.getInstance().configDir.resolve("radial-menu-theme.json").toFile()

    var theme: ThemeColors = ThemeColors()
        private set

    fun load() {
        if (!file.exists()) { save(); return }
        runCatching { theme = json.decodeFromString(file.readText()) }
            .onFailure { theme = ThemeColors(); save() }
    }

    fun save() {
        runCatching {
            Files.createDirectories(file.parentFile.toPath())
            file.writeText(json.encodeToString(theme))
        }
    }

    fun update(block: ThemeColors.() -> ThemeColors) {
        theme = theme.block()
        save()
    }

    fun c(v: Long) = v.toInt()
}
