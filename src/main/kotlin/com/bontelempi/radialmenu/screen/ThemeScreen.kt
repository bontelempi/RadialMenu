package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ThemeColors
import com.bontelempi.radialmenu.config.ThemeManager
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

/**
 * Theme editor screen.
 * Each colour is edited as a hex string (AARRGGBB).
 * Live preview swatch shown next to each field.
 */
class ThemeScreen(private val parent: Screen) : Screen(Text.literal("Theme Settings")) {

    private val C_BG    = 0xCC_111122.toInt()
    private val C_PANEL = 0xCC_1A1A2E.toInt()
    private val C_TEXT  = 0xFF_FFFFFF.toInt()
    private val C_DIM   = 0xFF_AAAAAA.toInt()
    private val C_LABEL = 0xFF_88AAFF.toInt()
    private val C_ERROR = 0xFF_FF5555.toInt()

    private data class ColorEntry(
        val label: String,
        val get: (ThemeColors) -> Long,
        val set: (ThemeColors, Long) -> ThemeColors
    )

    private val entries = listOf(
        ColorEntry("Background dim",    { it.backgroundDim  }, { t, v -> t.copy(backgroundDim  = v) }),
        ColorEntry("Inner segments",    { it.innerSegment   }, { t, v -> t.copy(innerSegment   = v) }),
        ColorEntry("Folder segments",   { it.innerFolder    }, { t, v -> t.copy(innerFolder    = v) }),
        ColorEntry("Outer segments",    { it.outerSegment   }, { t, v -> t.copy(outerSegment   = v) }),
        ColorEntry("Hover highlight",   { it.hoverHighlight }, { t, v -> t.copy(hoverHighlight = v) }),
        ColorEntry("Centre circle",     { it.centerCircle   }, { t, v -> t.copy(centerCircle   = v) }),
        ColorEntry("Text primary",      { it.textPrimary    }, { t, v -> t.copy(textPrimary    = v) }),
        ColorEntry("Text dim",          { it.textDim        }, { t, v -> t.copy(textDim        = v) }),
    )

    private val fields = mutableListOf<TextFieldWidget>()
    private val errors = mutableListOf<Boolean>()

    override fun init() {
        fields.clear()
        errors.clear()

        val startY = 40
        val rowH   = 26
        val fieldX = width / 2 - 80
        val fieldW = 130

        entries.forEachIndexed { i, entry ->
            val y = startY + i * rowH
            val field = TextFieldWidget(textRenderer, fieldX, y, fieldW, 16, Text.literal(entry.label))
            field.setMaxLength(8)
            field.text = String.format("%08X", entry.get(ThemeManager.theme) and 0xFFFFFFFFL)
            field.setChangedListener { v ->
                val valid = v.length == 8 && v.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' }
                errors[i] = !valid
                if (valid) {
                    val parsed = v.toLong(16) or (if (v.length == 8) 0L else 0xFF000000L)
                    ThemeManager.update { entry.set(this, parsed) }
                }
            }
            addDrawableChild(field)
            fields.add(field)
            errors.add(false)
        }

        // Reset to defaults
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults")) {
            ThemeManager.update { ThemeColors() }
            fields.forEachIndexed { i, field ->
                field.text = String.format("%08X", entries[i].get(ThemeManager.theme) and 0xFFFFFFFFL)
                errors[i] = false
            }
        }.dimensions(width / 2 - 105, height - 32, 100, 20).build())

        addDrawableChild(ButtonWidget.builder(Text.literal("← Back")) {
            client?.setScreen(parent)
        }.dimensions(width / 2 + 5, height - 32, 100, 20).build())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, C_BG)
        context.fill(0, 0, width, 28, C_PANEL)
        context.drawCenteredTextWithShadow(textRenderer, "Theme Settings", width / 2, 9, C_TEXT)
        context.drawTextWithShadow(textRenderer, "Format: AARRGGBB hex", width / 2 - 60, height - 50, C_DIM)

        val startY = 40
        val rowH   = 26
        val labelX = width / 2 - 200
        val swatchX = width / 2 + 56

        entries.forEachIndexed { i, entry ->
            val y = startY + i * rowH
            // Label
            context.drawTextWithShadow(textRenderer, entry.label, labelX, y + 4, C_LABEL)
            // Colour swatch
            val color = entries[i].get(ThemeManager.theme).toInt()
            context.fill(swatchX, y, swatchX + 20, y + 16, 0xFF_333333.toInt())
            context.fill(swatchX + 1, y + 1, swatchX + 19, y + 15, color)
            // Error indicator
            if (errors.getOrElse(i) { false })
                context.drawTextWithShadow(textRenderer, "✗ invalid", swatchX + 24, y + 4, C_ERROR)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.key() == 256) { client?.setScreen(parent); return true } // Escape
        return super.keyPressed(input)
    }

    override fun shouldCloseOnEsc() = false
    override fun shouldPause() = false
}
