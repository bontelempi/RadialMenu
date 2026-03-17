package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ThemeColors
import com.bontelempi.radialmenu.config.ThemeManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import kotlin.math.*

class ThemeScreen(private val parent: Screen) : Screen(Text.literal("Theme Editor")) {

    data class Slot(val label: String, val get: (ThemeColors) -> Long, val set: (ThemeColors, Long) -> ThemeColors)

    private val slots = listOf(
        Slot("Ring 0 (inner)",  { it.ring0 },         { t, v -> t.copy(ring0 = v) }),
        Slot("Ring 0 folders",  { it.ring0Folder },    { t, v -> t.copy(ring0Folder = v) }),
        Slot("Ring 1",          { it.ring1 },          { t, v -> t.copy(ring1 = v) }),
        Slot("Ring 2",          { it.ring2 },          { t, v -> t.copy(ring2 = v) }),
        Slot("Ring 3",          { it.ring3 },          { t, v -> t.copy(ring3 = v) }),
        Slot("Hover highlight", { it.hoverHighlight }, { t, v -> t.copy(hoverHighlight = v) }),
        Slot("Background dim",  { it.backgroundDim },  { t, v -> t.copy(backgroundDim = v) }),
        Slot("Centre circle",   { it.centerCircle },   { t, v -> t.copy(centerCircle = v) }),
        Slot("Text primary",    { it.textPrimary },    { t, v -> t.copy(textPrimary = v) }),
        Slot("Text dim",        { it.textDim },        { t, v -> t.copy(textDim = v) }),
    )

    private data class Palette(val name: String, val colors: ThemeColors)
    private val palettes = listOf(
        Palette("Default", ThemeColors()),
        Palette("Ocean",   ThemeColors(ring0=0xCC_0A2A4AL,ring0Folder=0xCC_0A1F3CL,ring1=0xD1_0A3050L,ring2=0xD1_082840L,ring3=0xD1_062030L,hoverHighlight=0xD9_00BFFFL,backgroundDim=0x77_000A14L,textPrimary=0xFF_E0F8FFL,textDim=0xFF_5599AAL)),
        Palette("Forest",  ThemeColors(ring0=0xCC_0A2A0AL,ring0Folder=0xCC_071F07L,ring1=0xD1_0A3010L,ring2=0xD1_082808L,ring3=0xD1_062006L,hoverHighlight=0xD9_44FF44L,backgroundDim=0x77_00140AL,textPrimary=0xFF_E8FFE8L,textDim=0xFF_55AA66L)),
        Palette("Sunset",  ThemeColors(ring0=0xCC_2A0A1AL,ring0Folder=0xCC_1F0710L,ring1=0xD1_3A1010L,ring2=0xD1_2A0808L,ring3=0xD1_200606L,hoverHighlight=0xD9_FF6644L,backgroundDim=0x77_140500L,textPrimary=0xFF_FFE8E0L,textDim=0xFF_AA6655L)),
        Palette("Void",    ThemeColors(ring0=0xCC_0D0D0DL,ring0Folder=0xCC_080808L,ring1=0xD1_111111L,ring2=0xD1_0A0A0AL,ring3=0xD1_080808L,hoverHighlight=0xD9_FFFFFFL,backgroundDim=0x99_000000L,textPrimary=0xFF_FFFFFFL,textDim=0xFF_666666L)),
        Palette("Candy",   ThemeColors(ring0=0xCC_2A0A2AL,ring0Folder=0xCC_1F071FL,ring1=0xD1_3A0A3AL,ring2=0xD1_280828L,ring3=0xD1_1E061EL,hoverHighlight=0xD9_FF44FFL,backgroundDim=0x77_140014L,textPrimary=0xFF_FFE0FFL,textDim=0xFF_AA55AAL)),
    )

    // ── State ─────────────────────────────────────────────────────────────────
    private var selectedSlot = 0
    private var r = 0; private var g = 0; private var b = 0; private var a = 255

    // Sliders drag state
    private var draggingSlider = -1  // 0=R 1=G 2=B 3=A

    // Hex field
    private lateinit var hexField: TextFieldWidget
    private var suppressHexUpdate = false

    // ── Geometry ──────────────────────────────────────────────────────────────
    private val PAD = 10
    private val SLOT_H = 22
    private val LIST_W = 160
    private val LIST_X get() = PAD
    private val LIST_Y get() = 30
    private val PICKER_X get() = LIST_X + LIST_W + 20
    private val SLIDER_W = 180
    private val SLIDER_H = 12
    private val SLIDER_GAP = 28
    private val SLIDER_Y get() = LIST_Y + 20
    private val PREVIEW_X get() = PICKER_X + SLIDER_W + 54
    private val PREVIEW_SIZE = 40
    private val PALETTE_Y get() = LIST_Y + slots.size * SLOT_H + 14

    override fun init() {
        loadSlot(selectedSlot)

        hexField = TextFieldWidget(textRenderer, PICKER_X + 12, SLIDER_Y + 4 * SLIDER_GAP + 8, 80, 18, Text.literal("Hex"))
        hexField.setMaxLength(8)
        hexField.setChangedListener { v ->
            if (!suppressHexUpdate && v.length == 8) {
                runCatching {
                    val parsed = v.toLong(16)
                    a = ((parsed ushr 24) and 0xFF).toInt()
                    r = ((parsed ushr 16) and 0xFF).toInt()
                    g = ((parsed ushr 8) and 0xFF).toInt()
                    b = (parsed and 0xFF).toInt()
                    saveSlot(selectedSlot)
                }
            }
        }
        addDrawableChild(hexField)
        updateHexField()

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults")) {
            ThemeManager.update { ThemeColors() }
            loadSlot(selectedSlot)
        }.dimensions(width - 140, height - 28, 130, 20).build())

        addDrawableChild(ButtonWidget.builder(Text.literal("← Back")) {
            MinecraftClient.getInstance().setScreen(parent)
        }.dimensions(PAD, height - 28, 80, 20).build())
    }

    // ── Render ────────────────────────────────────────────────────────────────
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xCC_111122.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Theme Editor", width / 2, 10, 0xFFFFFFFF.toInt())

        drawSlotList(context, mouseX, mouseY)
        drawSliders(context, mouseX)
        drawPreview(context)
        drawPalettes(context, mouseX, mouseY)

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawSlotList(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.fill(LIST_X-2, LIST_Y-2, LIST_X+LIST_W+2, LIST_Y+slots.size*SLOT_H+2, 0xCC_1A1A2E.toInt())
        slots.forEachIndexed { i, slot ->
            val y = LIST_Y + i * SLOT_H
            val isSel = i == selectedSlot
            val isHov = mouseX in LIST_X..(LIST_X+LIST_W) && mouseY in y..(y+SLOT_H)
            if (isSel) context.fill(LIST_X, y, LIST_X+LIST_W, y+SLOT_H, 0xCC_E94560.toInt())
            else if (isHov) context.fill(LIST_X, y, LIST_X+LIST_W, y+SLOT_H, 0xCC_333355.toInt())
            // Swatch with checker for transparency
            context.fill(LIST_X+4, y+4, LIST_X+18, y+SLOT_H-4, checkerAt(LIST_X+4, y+4))
            context.fill(LIST_X+4, y+4, LIST_X+18, y+SLOT_H-4, slots[i].get(ThemeManager.theme).toInt())
            context.drawTextWithShadow(textRenderer, slot.label, LIST_X+22, y+7,
                if (isSel) 0xFFFFFFFF.toInt() else 0xFFAAAAAAL.toInt())
        }
    }

    private fun drawSliders(context: DrawContext, mouseX: Int) {
        val labels = listOf("R", "G", "B", "A")
        val values = listOf(r, g, b, a)
        val colors = listOf(0xFFFF4444L.toInt(), 0xFF44FF44L.toInt(), 0xFF4488FFL.toInt(), 0xFFCCCCCCL.toInt())

        context.drawTextWithShadow(textRenderer, slots[selectedSlot].label,
            PICKER_X, LIST_Y + 4, 0xFF_88AAFF.toInt())

        labels.forEachIndexed { i, label ->
            val sy = SLIDER_Y + i * SLIDER_GAP
            val value = values[i]
            val filled = (value.toFloat() / 255 * SLIDER_W).toInt()

            context.drawTextWithShadow(textRenderer, label, PICKER_X, sy + 2, colors[i])
            val sx = PICKER_X + 12

            // Track
            context.fill(sx, sy + 2, sx + SLIDER_W, sy + SLIDER_H + 2, 0xFF_222233.toInt())
            // Fill
            context.fill(sx, sy + 2, sx + filled, sy + SLIDER_H + 2, colors[i])
            // Thumb
            val tx = sx + filled
            context.fill(tx - 2, sy, tx + 2, sy + SLIDER_H + 4, 0xFF_FFFFFF.toInt())
            // Value label
            context.drawTextWithShadow(textRenderer, value.toString(), sx + SLIDER_W + 6, sy + 2, 0xFF_AAAAAAL.toInt())
        }

        context.drawTextWithShadow(textRenderer, "Hex (AARRGGBB)", PICKER_X + 98, SLIDER_Y + 4 * SLIDER_GAP + 11, 0xFF_AAAAAAL.toInt())
    }

    private fun drawPreview(context: DrawContext) {
        val px = PREVIEW_X
        val py = SLIDER_Y
        context.drawTextWithShadow(textRenderer, "Preview", px, py - 12, 0xFF_AAAAAAL.toInt())
        context.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, checkerAt(px, py))
        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
        context.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, argb)
    }

    private fun drawPalettes(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.drawTextWithShadow(textRenderer, "Presets", LIST_X, PALETTE_Y - 12, 0xFF_AAAAAAL.toInt())
        val sw = 46; val sh = 30; val gap = 5
        palettes.forEachIndexed { i, palette ->
            val col = i % 3
            val row = i / 3
            val px = LIST_X + col * (sw + gap)
            val py = PALETTE_Y + row * (sh + gap)
            val isHov = mouseX in px..(px+sw) && mouseY in py..(py+sh)
            context.fill(px, py, px+sw, py+sh, if (isHov) palette.colors.ring0.toInt() else blendInt(palette.colors.ring0.toInt(), 0xFF_000000.toInt(), 0.4f))
            context.fill(px, py, px+sw, py+3, palette.colors.hoverHighlight.toInt())
            context.drawCenteredTextWithShadow(textRenderer, palette.name, px+sw/2, py+10,
                if (isHov) 0xFFFFFFFF.toInt() else 0xFFAAAAAAL.toInt())
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()

        // Slot list
        if (mx in LIST_X..(LIST_X+LIST_W) && my in LIST_Y..(LIST_Y+slots.size*SLOT_H)) {
            val i = (my - LIST_Y) / SLOT_H
            if (i in slots.indices) { selectedSlot = i; loadSlot(i) }
            return true
        }

        // Sliders
        val sx = PICKER_X + 12
        for (i in 0..3) {
            val sy = SLIDER_Y + i * SLIDER_GAP
            if (mx in sx..(sx+SLIDER_W) && my in (sy-2)..(sy+SLIDER_H+6)) {
                draggingSlider = i
                updateSlider(i, mx)
                return true
            }
        }

        // Palettes
        val sw = 46; val sh = 30; val gap = 5
        palettes.forEachIndexed { i, palette ->
            val col = i % 3; val row = i / 3
            val px = LIST_X + col*(sw+gap); val py = PALETTE_Y + row*(sh+gap)
            if (mx in px..(px+sw) && my in py..(py+sh)) {
                ThemeManager.update { palette.colors }
                loadSlot(selectedSlot)
                return true
            }
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        if (draggingSlider >= 0) { updateSlider(draggingSlider, click.x().toInt()); return true }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        draggingSlider = -1
        return super.mouseReleased(click)
    }

    private fun updateSlider(idx: Int, mx: Int) {
        val sx = PICKER_X + 12
        val v = ((mx - sx).toFloat() / SLIDER_W * 255).toInt().coerceIn(0, 255)
        when (idx) { 0 -> r=v; 1 -> g=v; 2 -> b=v; 3 -> a=v }
        saveSlot(selectedSlot)
        updateHexField()
    }

    private fun loadSlot(idx: Int) {
        val argb = slots[idx].get(ThemeManager.theme)
        a = ((argb ushr 24) and 0xFF).toInt()
        r = ((argb ushr 16) and 0xFF).toInt()
        g = ((argb ushr 8) and 0xFF).toInt()
        b = (argb and 0xFF).toInt()
        updateHexField()
    }

    private fun saveSlot(idx: Int) {
        val argb = (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        ThemeManager.update { slots[idx].set(this, argb) }
    }

    private fun updateHexField() {
        if (!::hexField.isInitialized) return
        suppressHexUpdate = true
        hexField.text = String.format("%02X%02X%02X%02X", a, r, g, b)
        suppressHexUpdate = false
    }

    override fun shouldPause() = false

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun checkerAt(x: Int, y: Int) =
        if ((x/4 + y/4) % 2 == 0) 0xFF_999999.toInt() else 0xFF_CCCCCC.toInt()

    private fun blendInt(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
        fun lerp(x: Int, y: Int) = (x + (y-x)*t).toInt().coerceIn(0, 255)
        return (lerp(ch(a,24),ch(b,24)) shl 24) or (lerp(ch(a,16),ch(b,16)) shl 16) or
               (lerp(ch(a,8),ch(b,8)) shl 8) or lerp(ch(a,0),ch(b,0))
    }
}
