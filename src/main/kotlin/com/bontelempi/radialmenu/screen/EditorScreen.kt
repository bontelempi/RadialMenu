package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ConfigManager
import com.bontelempi.radialmenu.config.NamedTheme
import com.bontelempi.radialmenu.config.ThemeManager
import com.bontelempi.radialmenu.model.MenuItem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

class EditorScreen : Screen(Text.literal("Radial Menu Editor")) {

    private data class ListRow(
        val item: MenuItem,
        val parentList: MutableList<MenuItem>,
        val indexInParent: Int,
        val depth: Int
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    private val TITLE_H  = 20
    private val PRESET_H = 22
    private val CONTENT_Y get() = TITLE_H + PRESET_H + 4
    private val BTN_Y     get() = height - 30
    private val BTN_H     = 20
    private val C1X       = 10
    private val C1W       get() = minOf((width * 0.40).toInt(), 360)
    private val C2X       get() = C1X + C1W + 12
    private val C2W       get() = width - C2X - 10
    private val ITEM_H    = 20
    private val LIST_H    get() = BTN_Y - CONTENT_Y - 4
    private val VISIBLE   get() = LIST_H / ITEM_H

    // Form field Y positions
    private val F_LABEL_Y  get() = CONTENT_Y + 2
    private val F_LABEL_FY get() = CONTENT_Y + 14
    private val F_ICON_Y   get() = CONTENT_Y + 40
    private val F_ICON_FY  get() = CONTENT_Y + 52
    private val F_CMD_Y    get() = CONTENT_Y + 78
    private val F_CMD_FY   get() = CONTENT_Y + 90
    private val F_THEME_Y  get() = CONTENT_Y + 124

    // ── Colours — driven by active preset theme ───────────────────────────────
    private val C_BG     get() = ThemeManager.c(ThemeManager.theme.backgroundDim)
    private val C_PANEL  get() = ThemeManager.c(ThemeManager.theme.ring0)
    private val C_BAR    get() = blendColour(ThemeManager.c(ThemeManager.theme.ring0), 0xFF_000000.toInt(), 0.5f)
    private val C_SEL    get() = ThemeManager.c(ThemeManager.theme.hoverHighlight)
    private val C_HOV    get() = blendColour(ThemeManager.c(ThemeManager.theme.ring0), ThemeManager.c(ThemeManager.theme.hoverHighlight), 0.3f)
    private val C_CHILD  = 0x33_FFFFFF.toInt()
    private val C_TEXT   get() = ThemeManager.c(ThemeManager.theme.textPrimary)
    private val C_DIM    get() = ThemeManager.c(ThemeManager.theme.textDim)
    private val C_LABEL  get() = ThemeManager.c(ThemeManager.theme.textPrimary)
    private val C_ACCENT get() = ThemeManager.c(ThemeManager.theme.hoverHighlight)

    // ── State ─────────────────────────────────────────────────────────────────
    private val expandedFolders = mutableSetOf<String>()
    private var selectedRow: ListRow? = null
    private var scrollOffset = 0
    private var confirmDelete = false
    private var renamingPreset = false
    private var showIconTooltip = false

    private lateinit var labelField:   TextFieldWidget
    private lateinit var iconField:    TextFieldWidget
    private lateinit var commandField: TextFieldWidget
    private var renameField: TextFieldWidget? = null

    // ── Row builder ───────────────────────────────────────────────────────────
    private fun buildRows(): List<ListRow> {
        val rows = mutableListOf<ListRow>()
        fun add(list: MutableList<MenuItem>, depth: Int) {
            list.forEachIndexed { i, item ->
                rows.add(ListRow(item, list, i, depth))
                if (item.isFolder && item.id in expandedFolders) add(item.children, depth + 1)
            }
        }
        add(ConfigManager.rootItems, 0)
        return rows
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    override fun init() {
        labelField = TextFieldWidget(textRenderer, C2X, F_LABEL_FY, C2W, 20, Text.literal("Label"))
        labelField.setMaxLength(40)
        labelField.setChangedListener { v ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(label = v)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(labelField)

        iconField = TextFieldWidget(textRenderer, C2X, F_ICON_FY, C2W, 20, Text.literal("Icon"))
        iconField.setMaxLength(8192)
        iconField.setChangedListener { v ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(iconItem = v, headTexture = null)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(iconField)

        commandField = TextFieldWidget(textRenderer, C2X, F_CMD_FY, C2W, 20, Text.literal("Command"))
        commandField.setMaxLength(256)
        commandField.setChangedListener { v ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(command = v)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(commandField)

        // Bottom buttons
        var bx = C1X
        fun btn(label: String, w: Int, action: () -> Unit) =
            ButtonWidget.builder(Text.literal(label)) { action() }
                .dimensions(bx, BTN_Y, w, BTN_H).build()
                .also { addDrawableChild(it); bx += w + 4 }

        btn("+ Add", 50) {
            val item = MenuItem(ConfigManager.newId(), "New Item", "minecraft:paper", "")
            val row = selectedRow
            when {
                row != null && row.item.isFolder -> { row.item.children.add(item); expandedFolders.add(row.item.id) }
                row != null && row.depth > 0 -> row.parentList.add(row.indexInParent + 1, item)
                else -> ConfigManager.rootItems.add(item)
            }
            ConfigManager.save()
            selectedRow = buildRows().find { it.item.id == item.id }
            populateFields()
        }
        btn("Delete", 50) {
            selectedRow?.let { row ->
                row.parentList.removeAt(row.indexInParent)
                selectedRow = null; populateFields(); ConfigManager.save()
            }
        }
        btn("▲ Up", 40) {
            selectedRow?.let { row ->
                if (row.indexInParent > 0) {
                    val l = row.parentList
                    val t = l[row.indexInParent]; l[row.indexInParent] = l[row.indexInParent - 1]; l[row.indexInParent - 1] = t
                    selectedRow = row.copy(indexInParent = row.indexInParent - 1); ConfigManager.save()
                }
            }
        }
        btn("▼ Down", 50) {
            selectedRow?.let { row ->
                if (row.indexInParent < row.parentList.lastIndex) {
                    val l = row.parentList
                    val t = l[row.indexInParent]; l[row.indexInParent] = l[row.indexInParent + 1]; l[row.indexInParent + 1] = t
                    selectedRow = row.copy(indexInParent = row.indexInParent + 1); ConfigManager.save()
                }
            }
        }
        btn("+ Sub", 46) {
            selectedRow?.let { row ->
                row.item.children.add(MenuItem(ConfigManager.newId(), "Sub Item", "minecraft:paper", ""))
                expandedFolders.add(row.item.id); ConfigManager.save()
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close")) {
            ConfigManager.save(); close()
        }.dimensions(width - 90, BTN_Y, 80, BTN_H).build())

        populateFields()
    }

    private fun populateFields() {
        val item = selectedRow?.item
        val hasItem = item != null
        labelField.text    = item?.label ?: ""
        iconField.text     = item?.headTexture ?: item?.iconItem ?: ""
        commandField.text  = item?.command ?: ""
        labelField.visible    = hasItem
        iconField.visible     = hasItem
        commandField.visible  = hasItem
        labelField.setEditable(hasItem)
        iconField.setEditable(hasItem)
        commandField.setEditable(hasItem)
    }

    // ── Render ────────────────────────────────────────────────────────────────
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, C_BG)

        // Title bar
        context.fill(0, 0, width, TITLE_H, C_BAR)
        context.drawCenteredTextWithShadow(textRenderer, "Radial Menu Editor", width / 2, 5, C_TEXT)

        // Preset bar
        context.fill(0, TITLE_H, width, TITLE_H + PRESET_H, C_BAR)
        drawPresetBar(context, mouseX, mouseY)

        // List panel
        context.fill(C1X - 2, CONTENT_Y - 2, C1X + C1W + 2, CONTENT_Y + LIST_H + 2, C_PANEL)
        val rows = buildRows()
        if (rows.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No items — click + Add", C1X + 6, CONTENT_Y + 8, C_DIM)
        } else {
            val end = minOf(scrollOffset + VISIBLE, rows.size)
            for (i in scrollOffset until end) {
                val row = rows[i]
                val iy = CONTENT_Y + (i - scrollOffset) * ITEM_H
                val isSel = row.item.id == selectedRow?.item?.id
                val isHov = mouseX in C1X..(C1X + C1W) && mouseY in iy..(iy + ITEM_H)
                val bg = when { isSel -> C_SEL; isHov -> C_HOV; row.depth > 0 -> C_CHILD; else -> 0 }
                if (bg != 0) context.fill(C1X, iy, C1X + C1W, iy + ITEM_H, bg)
                val prefix = when {
                    row.item.isFolder && row.item.id in expandedFolders -> "▼ "
                    row.item.isFolder -> "▶ "
                    row.depth > 0 -> "└ "
                    else -> "  "
                }
                context.drawTextWithShadow(textRenderer, "$prefix${row.item.label}",
                    C1X + 4 + row.depth * 12, iy + 6,
                    if (isSel) C_TEXT else if (row.depth > 0) C_DIM else C_TEXT)
                if (row.item.isFolder) {
                    val cnt = "(${row.item.children.size})"
                    context.drawTextWithShadow(textRenderer, cnt,
                        C1X + C1W - textRenderer.getWidth(cnt) - 4, iy + 6, C_DIM)
                }
            }
        }

        // Form panel — always draw panel and theme picker
        context.fill(C2X - 4, CONTENT_Y - 2, C2X + C2W + 4, F_THEME_Y + 30, C_PANEL)
        drawThemePicker(context, mouseX, mouseY)

        val item = selectedRow?.item
        if (item == null) {
            context.drawTextWithShadow(textRenderer, "Select an item to edit", C2X, CONTENT_Y + 6, C_DIM)
        } else {
            context.drawTextWithShadow(textRenderer, "Label",          C2X, F_LABEL_Y, C_LABEL)
            context.drawTextWithShadow(textRenderer, "Icon",           C2X, F_ICON_Y,  C_LABEL)
            val hx = C2X + textRenderer.getWidth("Icon") + 4
            context.drawTextWithShadow(textRenderer, "?", hx, F_ICON_Y, C_ACCENT)
            showIconTooltip = mouseX in hx..(hx + 8) && mouseY in F_ICON_Y..(F_ICON_Y + 10)
            context.drawTextWithShadow(textRenderer, "Command (no /)", C2X, F_CMD_Y, C_LABEL)
            if (item.isFolder)
                context.drawTextWithShadow(textRenderer, "▸ Folder command runs on click",
                    C2X, F_THEME_Y + 32, 0xFF_55FF55.toInt())
        }

        if (confirmDelete) drawDeleteConfirm(context)
        super.render(context, mouseX, mouseY, delta)
        if (showIconTooltip) drawIconTooltip(context)
    }

    // ── Theme picker ──────────────────────────────────────────────────────────
    private fun drawThemePicker(context: DrawContext, mouseX: Int, mouseY: Int) {
        val themes: List<NamedTheme> = ThemeManager.allThemes
        val activeRef = ConfigManager.activePreset.themeRef
        val idx = themes.indexOfFirst { it.name == activeRef }.coerceAtLeast(0)
        val current = themes[idx]
        val ty = F_THEME_Y

        context.drawTextWithShadow(textRenderer, "Theme", C2X, ty, C_LABEL)
        val lhov = mouseX in C2X..(C2X + 10) && mouseY in (ty + 12)..(ty + 24)
        val rhov = mouseX in (C2X + 122)..(C2X + 132) && mouseY in (ty + 12)..(ty + 24)
        context.drawTextWithShadow(textRenderer, "◀", C2X, ty + 12, if (lhov) C_TEXT else C_DIM)
        context.fill(C2X + 14, ty + 10, C2X + 118, ty + 24, C_PANEL)
        context.drawCenteredTextWithShadow(textRenderer, current.name, C2X + 66, ty + 13, C_TEXT)
        context.drawTextWithShadow(textRenderer, "▶", C2X + 122, ty + 12, if (rhov) C_TEXT else C_DIM)
        context.fill(C2X + 14, ty + 24, C2X + 118, ty + 27, current.colors.hoverHighlight.toInt())
    }

    // ── Preset bar ────────────────────────────────────────────────────────────
    private fun drawPresetBar(context: DrawContext, mouseX: Int, mouseY: Int) {
        val barY = TITLE_H; val barH = PRESET_H
        val presets = ConfigManager.config.presets
        val active = ConfigManager.activePresetIndex
        var x = C1X

        fun hov(x1: Int, x2: Int) = mouseX in x1..x2 && mouseY in barY..(barY + barH)

        val lh = hov(x, x + 14)
        context.drawTextWithShadow(textRenderer, "◀", x + 2, barY + 7, if (lh) C_TEXT else C_DIM)
        x += 16

        presets.forEachIndexed { i, preset ->
            val label = if (preset.name.length > 12) preset.name.take(11) + "…" else preset.name
            val tw = textRenderer.getWidth(label) + 10
            val isAct = i == active; val isHov = hov(x, x + tw)
            context.fill(x, barY + 2, x + tw, barY + barH - 1,
                if (isAct) C_ACCENT else if (isHov) C_HOV else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, label, x + 5, barY + 7,
                if (isAct) C_TEXT else C_DIM)
            x += tw + 3
        }

        val rh = hov(x, x + 14)
        context.drawTextWithShadow(textRenderer, "▶", x + 2, barY + 7, if (rh) C_TEXT else C_DIM)
        x += 18

        if (presets.size < ConfigManager.MAX_PRESETS) {
            val nh = hov(x, x + 38)
            context.fill(x, barY + 2, x + 38, barY + barH - 1, if (nh) C_HOV else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, "+ New", x + 4, barY + 7, if (nh) C_TEXT else C_DIM)
            x += 42
        }

        if (presets.size > 1) {
            val dh = hov(x, x + 44)
            context.fill(x, barY + 2, x + 44, barY + barH - 1,
                if (dh) 0xFF_3D0A14.toInt() else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, "Delete", x + 4, barY + 7,
                if (dh) C_ACCENT else C_DIM)
        }
    }

    private fun drawDeleteConfirm(context: DrawContext) {
        val mw = 220; val mh = 72
        val mx = width / 2 - mw / 2; val my = height / 2 - mh / 2
        context.fill(mx - 2, my - 2, mx + mw + 2, my + mh + 2, C_ACCENT)
        context.fill(mx, my, mx + mw, my + mh, 0xFF_0A0A1A.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Delete preset?", width / 2, my + 10, C_TEXT)
        val name = ConfigManager.config.presets.getOrNull(ConfigManager.activePresetIndex)?.name ?: ""
        context.drawCenteredTextWithShadow(textRenderer, "\"$name\"", width / 2, my + 22, C_ACCENT)
        context.fill(mx + 10, my + 40, mx + 94, my + 58, 0xFF_3D0A14.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Yes, delete", mx + 52, my + 46, 0xFF_FF4466.toInt())
        context.fill(mx + mw - 94, my + 40, mx + mw - 10, my + 58, 0xFF_1A1A2E.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Cancel", mx + mw - 52, my + 46, C_DIM)
    }

    private fun drawIconTooltip(context: DrawContext) {
        val lines = listOf(
            "Icon field accepts:" to C_ACCENT,
            "" to C_DIM,
            "Minecraft item ID:" to C_DIM,
            "  minecraft:diamond_sword" to C_TEXT,
            "" to C_DIM,
            "OR base64 skin from" to C_DIM,
            "minecraft-heads.com" to C_DIM,
            "(For Developers → Value)" to C_TEXT,
        )
        val tw = lines.maxOf { textRenderer.getWidth(it.first) }
        val th = lines.size * 10 + 8
        val tx = (C2X + textRenderer.getWidth("Icon") + 6).coerceAtMost(width - tw - 10)
        val ty = (F_ICON_Y + 12).coerceAtMost(height - th - 4)
        context.fill(tx - 2, ty - 2, tx + tw + 4, ty + th, 0xFF_0A0A1A.toInt())
        context.fill(tx - 2, ty - 2, tx + tw + 4, ty - 1, C_ACCENT)
        lines.forEachIndexed { i, (line, color) ->
            if (line.isNotEmpty()) context.drawTextWithShadow(textRenderer, line, tx, ty + 2 + i * 10, color)
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        val barY = TITLE_H; val barH = PRESET_H
        val presets = ConfigManager.config.presets

        // Confirm delete modal
        if (confirmDelete) {
            val mw = 220; val mh = 72
            val modX = width / 2 - mw / 2; val modY = height / 2 - mh / 2
            if (mx in (modX + 10)..(modX + 94) && my in (modY + 40)..(modY + 58)) {
                ConfigManager.deletePreset(ConfigManager.activePresetIndex)
                selectedRow = null; scrollOffset = 0; populateFields()
            }
            confirmDelete = false; return true
        }

        // Preset bar
        if (my in barY..(barY + barH)) {
            var x = C1X
            if (mx in x..(x + 14)) {
                ConfigManager.switchPreset((ConfigManager.activePresetIndex - 1 + presets.size) % presets.size)
                return true
            }
            x += 16
            presets.forEachIndexed { i, preset ->
                val tw = textRenderer.getWidth(
                    if (preset.name.length > 12) preset.name.take(11) + "…" else preset.name) + 10
                if (mx in x..(x + tw)) {
                    if (i == ConfigManager.activePresetIndex && doubled) startRename(i, x, barY)
                    else { ConfigManager.switchPreset(i); selectedRow = null; scrollOffset = 0; populateFields() }
                    return true
                }
                x += tw + 3
            }
            if (mx in x..(x + 14)) {
                ConfigManager.switchPreset((ConfigManager.activePresetIndex + 1) % presets.size)
                return true
            }
            x += 18
            if (presets.size < ConfigManager.MAX_PRESETS && mx in x..(x + 38)) {
                ConfigManager.addPreset("Preset ${presets.size + 1}")
                selectedRow = null; scrollOffset = 0; populateFields(); return true
            }
            x += 42
            if (presets.size > 1 && mx in x..(x + 44)) { confirmDelete = true; return true }
            return true
        }

        // Theme picker arrows
        val themes: List<NamedTheme> = ThemeManager.allThemes
        val activeRef = ConfigManager.activePreset.themeRef
        val currentIdx = themes.indexOfFirst { it.name == activeRef }.coerceAtLeast(0)
        val ty = F_THEME_Y
        if (my in (ty + 10)..(ty + 26)) {
            if (mx in C2X..(C2X + 12)) {
                ConfigManager.setPresetThemeRef(ConfigManager.activePresetIndex,
                    themes[(currentIdx - 1 + themes.size) % themes.size].name)
                return true
            }
            if (mx in (C2X + 120)..(C2X + 134)) {
                ConfigManager.setPresetThemeRef(ConfigManager.activePresetIndex,
                    themes[(currentIdx + 1) % themes.size].name)
                return true
            }
        }

        // List
        if (mx in C1X..(C1X + C1W) && my in CONTENT_Y..(CONTENT_Y + LIST_H)) {
            val rows = buildRows()
            val i = scrollOffset + ((my - CONTENT_Y) / ITEM_H)
            if (i in rows.indices) {
                val row = rows[i]
                if (row.item.isFolder) {
                    if (row.item.id in expandedFolders) expandedFolders.remove(row.item.id)
                    else expandedFolders.add(row.item.id)
                }
                selectedRow = row; populateFields(); return true
            }
        }

        return super.mouseClicked(click, doubled)
    }

    private fun startRename(index: Int, x: Int, barY: Int) {
        renamingPreset = true
        val field = TextFieldWidget(textRenderer, x, barY + 3, 100, 16, Text.literal("Name"))
        field.setMaxLength(20)
        field.text = ConfigManager.config.presets[index].name
        field.setChangedListener { v -> ConfigManager.renamePreset(index, v) }
        renameField?.let { remove(it) }
        renameField = field; addDrawableChild(field); focused = field
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val rows = buildRows()
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, (rows.size - VISIBLE).coerceAtLeast(0))
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (renamingPreset && input.key() == 257) {
            renameField?.let { remove(it) }; renameField = null; renamingPreset = false; return true
        }
        if (input.key() == 69 && !labelField.isFocused && !iconField.isFocused &&
            !commandField.isFocused && !renamingPreset) {
            ConfigManager.save(); close(); return true
        }
        return super.keyPressed(input)
    }

    override fun shouldCloseOnEsc(): Boolean {
        if (renamingPreset) { renameField?.let { remove(it) }; renameField = null; renamingPreset = false; return false }
        ConfigManager.save(); return true
    }

    private fun blendColour(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
        fun lerp(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (lerp(ch(a,24),ch(b,24)) shl 24) or (lerp(ch(a,16),ch(b,16)) shl 16) or
               (lerp(ch(a,8),ch(b,8)) shl 8) or lerp(ch(a,0),ch(b,0))
    }

    override fun shouldPause() = false
}
