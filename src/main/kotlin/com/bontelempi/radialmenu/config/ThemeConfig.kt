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
    val innerSegment  get() = ring0
    val innerFolder   get() = ring0Folder
    val outerSegment  get() = ring1
}

@Serializable
data class NamedTheme(
    val name: String,
    val colors: ThemeColors,
    val builtin: Boolean = false
)

@Serializable
data class ThemeLibrary(
    val customThemes: MutableList<NamedTheme> = mutableListOf()
)

object ThemeManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = FabricLoader.getInstance().configDir.resolve("radial-menu-theme.json").toFile()

    var library: ThemeLibrary = ThemeLibrary()
        private set

    val builtinThemes = listOf(
        NamedTheme("Default", ThemeColors(), builtin = true),
        NamedTheme("Ocean",   ThemeColors(ring0=0xCC_0A2A4AL,ring0Folder=0xCC_0A1F3CL,ring1=0xD1_0A3050L,ring2=0xD1_082840L,ring3=0xD1_062030L,hoverHighlight=0xD9_00BFFFL,backgroundDim=0x77_000A14L,textPrimary=0xFF_E0F8FFL,textDim=0xFF_5599AAL), builtin = true),
        NamedTheme("Forest",  ThemeColors(ring0=0xCC_0A2A0AL,ring0Folder=0xCC_071F07L,ring1=0xD1_0A3010L,ring2=0xD1_082808L,ring3=0xD1_062006L,hoverHighlight=0xD9_44FF44L,backgroundDim=0x77_00140AL,textPrimary=0xFF_E8FFE8L,textDim=0xFF_55AA66L), builtin = true),
        NamedTheme("Sunset",  ThemeColors(ring0=0xCC_2A0A1AL,ring0Folder=0xCC_1F0710L,ring1=0xD1_3A1010L,ring2=0xD1_2A0808L,ring3=0xD1_200606L,hoverHighlight=0xD9_FF6644L,backgroundDim=0x77_140500L,textPrimary=0xFF_FFE8E0L,textDim=0xFF_AA6655L), builtin = true),
        NamedTheme("Void",    ThemeColors(ring0=0xCC_0D0D0DL,ring0Folder=0xCC_080808L,ring1=0xD1_111111L,ring2=0xD1_0A0A0AL,ring3=0xD1_080808L,hoverHighlight=0xD9_FFFFFFL,backgroundDim=0x99_000000L,textPrimary=0xFF_FFFFFFL,textDim=0xFF_666666L), builtin = true),
        NamedTheme("Candy",   ThemeColors(ring0=0xCC_2A0A2AL,ring0Folder=0xCC_1F071FL,ring1=0xD1_3A0A3AL,ring2=0xD1_280828L,ring3=0xD1_1E061EL,hoverHighlight=0xD9_FF44FFL,backgroundDim=0x77_140014L,textPrimary=0xFF_FFE0FFL,textDim=0xFF_AA55AAL), builtin = true),
    )

    val allThemes get() = builtinThemes + library.customThemes

    // The theme actually used for rendering — driven by active preset
    val theme: ThemeColors get() {
        val preset = ConfigManager.activePreset
        // 1. Per-preset override takes priority
        preset.themeOverride?.let { return it }
        // 2. Named theme reference
        preset.themeRef?.let { ref ->
            allThemes.find { it.name == ref }?.colors?.let { return it }
        }
        // 3. Default
        return ThemeColors()
    }

    fun load() {
        if (!file.exists()) { saveLibrary(); return }
        runCatching { library = json.decodeFromString(file.readText()) }
            .onFailure { library = ThemeLibrary(); saveLibrary() }
    }

    fun saveLibrary() {
        runCatching {
            Files.createDirectories(file.parentFile.toPath())
            file.writeText(json.encodeToString(library))
        }
    }

    fun addCustomTheme(name: String, colors: ThemeColors) {
        library.customThemes.removeAll { it.name == name }
        library.customThemes.add(NamedTheme(name, colors))
        saveLibrary()
    }

    fun deleteCustomTheme(name: String) {
        library.customThemes.removeAll { it.name == name }
        saveLibrary()
    }

    fun c(v: Long) = v.toInt()
}
