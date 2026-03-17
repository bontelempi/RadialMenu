package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ConfigManager
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

    private val expandedFolders = mutableSetOf<String>()
    private var selectedRow: ListRow? = null
    private var scrollOffset = 0

    private lateinit var labelField:   TextFieldWidget
    private lateinit var iconField:    TextFieldWidget
    private lateinit var commandField: TextFieldWidget
    private var renameField: TextFieldWidget? = null
    private var renamingPreset = false
    private var confirmDeletePreset = false

    // ── Layout constants (all computed after init so width/height are valid) ──
    // Title bar: y=0..16
    // Preset bar: y=18..40
    // Content: y=44..height-44
    // Button bar: y=height-36..height
    private val TITLE_H  = 18
    private val PRESET_H = 22
    private val CONTENT_Y get() = TITLE_H + PRESET_H + 4
    private val BTN_Y    get() = height - 36

    private val LIST_X   get() = 10
    private val LIST_Y   get() = CONTENT_Y
    private val LIST_W   get() = minOf((width * 0.42).toInt(), 420)
    private val LIST_H   get() = BTN_Y - LIST_Y - 4
    private val FORM_X   get() = LIST_X + LIST_W + 12
    private val FORM_Y   get() = CONTENT_Y
    private val FORM_W   get() = width - FORM_X - 10
    private val ITEM_H   = 20
    private val VISIBLE  get() = LIST_H / ITEM_H

    private val C_BG        = 0xCC_111122.toInt()
    private val C_PANEL     = 0xCC_1A1A2E.toInt()
    private val C_PRESET_BG = 0xFF_0A0A18.toInt()
    private val C_SELECTED  = 0xCC_E94560.toInt()
    private val C_HOVER     = 0xCC_333355.toInt()
    private val C_CHILD_BG  = 0x33_FFFFFF.toInt()
    private val C_TEXT      = 0xFF_FFFFFF.toInt()
    private val C_DIM       = 0xFF_AAAAAA.toInt()
    private val C_LABEL     = 0xFF_88AAFF.toInt()
    private val C_HINT      = 0xFF_E94560.toInt()
    private val C_ACTIVE    = 0xFF_E94560.toInt()

    private var showIconTooltip = false

    private fun buildRows(): List<ListRow> {
        val rows = mutableListOf<ListRow>()
        fun addRows(list: MutableList<MenuItem>, depth: Int) {
            list.forEachIndexed { i, item ->
                rows.add(ListRow(item, list, i, depth))
                if (item.isFolder && item.id in expandedFolders)
                    addRows(item.children, depth + 1)
            }
        }
        addRows(ConfigManager.rootItems, 0)
        return rows
    }

    override fun init() {
        val fy = FORM_Y
        val fx = FORM_X

        labelField = TextFieldWidget(textRenderer, fx, fy + 20, FORM_W, 18, Text.literal("Label"))
        labelField.setMaxLength(40)
        labelField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(label = v)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(labelField)

        iconField = TextFieldWidget(textRenderer, fx, fy + 60, FORM_W, 18, Text.literal("Icon"))
        iconField.setMaxLength(8192)
        iconField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(iconItem = v, headTexture = null)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(iconField)

        commandField = TextFieldWidget(textRenderer, fx, fy + 100, FORM_W, 18, Text.literal("Command"))
        commandField.setMaxLength(256)
        commandField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(command = v)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(commandField)

        var bX = LIST_X

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add")) {
            val item = MenuItem(ConfigManager.newId(), "New Item", "minecraft:paper", "")
            val row = selectedRow
            when {
                row != null && row.item.isFolder -> {
                    row.item.children.add(item)
                    expandedFolders.add(row.item.id)
                }
                row != null && row.depth > 0 -> row.parentList.add(row.indexInParent + 1, item)
                else -> ConfigManager.rootItems.add(item)
            }
            ConfigManager.save()
            selectedRow = buildRows().find { it.item.id == item.id }
            populateFields()
        }.dimensions(bX, BTN_Y, 54, 20).build().also { bX += 58 })

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete")) {
            selectedRow?.let { row ->
                row.parentList.removeAt(row.indexInParent)
                selectedRow = null; populateFields(); ConfigManager.save()
            }
        }.dimensions(bX, BTN_Y, 54, 20).build().also { bX += 58 })

        addDrawableChild(ButtonWidget.builder(Text.literal("▲ Up")) {
            selectedRow?.let { row ->
                if (row.indexInParent > 0) {
                    val list = row.parentList
                    val tmp = list[row.indexInParent]; list[row.indexInParent] = list[row.indexInParent - 1]
                    list[row.indexInParent - 1] = tmp
                    selectedRow = row.copy(indexInParent = row.indexInParent - 1)
                    ConfigManager.save()
                }
            }
        }.dimensions(bX, BTN_Y, 44, 20).build().also { bX += 48 })

        addDrawableChild(ButtonWidget.builder(Text.literal("▼ Down")) {
            selectedRow?.let { row ->
                if (row.indexInParent < row.parentList.lastIndex) {
                    val list = row.parentList
                    val tmp = list[row.indexInParent]; list[row.indexInParent] = list[row.indexInParent + 1]
                    list[row.indexInParent + 1] = tmp
                    selectedRow = row.copy(indexInParent = row.indexInParent + 1)
                    ConfigManager.save()
                }
            }
        }.dimensions(bX, BTN_Y, 54, 20).build().also { bX += 58 })

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Sub")) {
            selectedRow?.let { row ->
                val child = MenuItem(ConfigManager.newId(), "Sub Item", "minecraft:paper", "")
                row.item.children.add(child)
                expandedFolders.add(row.item.id)
                ConfigManager.save()
            }
        }.dimensions(bX, BTN_Y, 56, 20).build().also { bX += 60 })

        addDrawableChild(ButtonWidget.builder(Text.literal("Open Sub ▶")) {
            if (selectedRow?.item?.isFolder == true)
                MinecraftClient.getInstance().setScreen(EditorScreen())
        }.dimensions(bX, BTN_Y, 80, 20).build())

        addDrawableChild(ButtonWidget.builder(Text.literal("🎨 Theme")) {
            MinecraftClient.getInstance().setScreen(ThemeScreen(this))
        }.dimensions(width - 220, BTN_Y, 80, 20).build())

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close")) {
            ConfigManager.save(); close()
        }.dimensions(width - 136, BTN_Y, 126, 20).build())

        populateFields()
    }

    private fun populateFields() {
        val item = selectedRow?.item
        labelField.text   = item?.label ?: ""
        iconField.text    = item?.headTexture ?: item?.iconItem ?: ""
        commandField.text = item?.command ?: ""
        labelField.setEditable(item != null)
        iconField.setEditable(item != null)
        commandField.setEditable(item != null)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, C_BG)

        // Title bar
        context.fill(0, 0, width, TITLE_H, C_PRESET_BG)
        context.drawCenteredTextWithShadow(textRenderer, "Radial Menu Editor", width / 2, 4, C_TEXT)
        context.drawTextWithShadow(textRenderer, "Escape to close", LIST_X, 4, C_DIM)

        // Preset bar
        val pby = TITLE_H + 2
        context.fill(0, pby, width, pby + PRESET_H, C_PRESET_BG)
        drawPresetBar(context, mouseX, mouseY, pby)

        // List panel
        context.fill(LIST_X - 2, LIST_Y - 2, LIST_X + LIST_W + 2, LIST_Y + LIST_H + 2, C_PANEL)
        val rows = buildRows()
        if (rows.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No items – click + Add", LIST_X + 6, LIST_Y + 8, C_DIM)
        } else {
            val end = minOf(scrollOffset + VISIBLE, rows.size)
            for (i in scrollOffset until end) {
                val row = rows[i]
                val iy = LIST_Y + (i - scrollOffset) * ITEM_H
                val isSelected = row.item.id == selectedRow?.item?.id
                val rowColor = when {
                    isSelected -> C_SELECTED
                    mouseX in LIST_X..(LIST_X + LIST_W) && mouseY in iy..(iy + ITEM_H) -> C_HOVER
                    row.depth > 0 -> C_CHILD_BG
                    else -> 0
                }
                if (rowColor != 0) context.fill(LIST_X, iy, LIST_X + LIST_W, iy + ITEM_H, rowColor)
                val indent = row.depth * 12
                val prefix = when {
                    row.item.isFolder && row.item.id in expandedFolders -> "▼ "
                    row.item.isFolder -> "▶ "
                    row.depth > 0 -> "└ "
                    else -> "  "
                }
                context.drawTextWithShadow(textRenderer, "$prefix${row.item.label}",
                    LIST_X + 4 + indent, iy + 6,
                    if (isSelected) C_TEXT else if (row.depth > 0) C_DIM else C_TEXT)
                if (row.item.isFolder) {
                    val cnt = "(${row.item.children.size})"
                    context.drawTextWithShadow(textRenderer, cnt,
                        LIST_X + LIST_W - textRenderer.getWidth(cnt) - 4, iy + 6, C_DIM)
                }
            }
        }

        // Form panel
        val fx = FORM_X; val fy = FORM_Y
        context.fill(fx - 6, fy, fx + FORM_W + 6, fy + 136, C_PANEL)

        val item = selectedRow?.item
        if (item == null) {
            context.drawTextWithShadow(textRenderer, "Select an item to edit", fx, fy + 10, C_DIM)
        } else {
            context.drawTextWithShadow(textRenderer, "Label",          fx, fy + 8,  C_LABEL)
            context.drawTextWithShadow(textRenderer, "Icon  ",         fx, fy + 48, C_LABEL)
            val hintX = fx + textRenderer.getWidth("Icon  ")
            context.drawTextWithShadow(textRenderer, "?", hintX, fy + 48, C_HINT)
            showIconTooltip = mouseX in hintX..(hintX + 8) && mouseY in (fy + 48)..(fy + 58)
            context.drawTextWithShadow(textRenderer, "Command (no /)", fx, fy + 88, C_LABEL)
            if (item.isFolder)
                context.drawTextWithShadow(textRenderer, "▸ Folder command runs on click",
                    fx, fy + 118, 0xFF_55FF55.toInt())
        }

        super.render(context, mouseX, mouseY, delta)
        if (showIconTooltip) drawIconTooltip(context)
    }

    private fun drawPresetBar(context: DrawContext, mouseX: Int, mouseY: Int, barY: Int) {
        val presets = ConfigManager.config.presets
        val active  = ConfigManager.activePresetIndex
        var x = LIST_X

        // ◀
        val prevHov = mouseX in x..(x + 14) && mouseY in barY..(barY + PRESET_H)
        context.drawTextWithShadow(textRenderer, "◀", x + 2, barY + 7, if (prevHov) C_TEXT else C_DIM)
        x += 16

        // Tabs
        presets.forEachIndexed { i, preset ->
            val label = if (preset.name.length > 12) preset.name.take(11) + "…" else preset.name
            val tw = textRenderer.getWidth(label) + 10
            val isActive = i == active
            val isHov = mouseX in x..(x + tw) && mouseY in barY..(barY + PRESET_H)
            context.fill(x, barY + 2, x + tw, barY + PRESET_H - 1,
                if (isActive) C_ACTIVE else if (isHov) C_HOVER else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, label, x + 5, barY + 7,
                if (isActive) C_TEXT else C_DIM)
            x += tw + 3
        }

        // ▶
        val nextHov = mouseX in x..(x + 14) && mouseY in barY..(barY + PRESET_H)
        context.drawTextWithShadow(textRenderer, "▶", x + 2, barY + 7, if (nextHov) C_TEXT else C_DIM)
        x += 18

        // + New
        if (presets.size < ConfigManager.MAX_PRESETS) {
            val newHov = mouseX in x..(x + 38) && mouseY in barY..(barY + PRESET_H)
            context.fill(x, barY + 2, x + 38, barY + PRESET_H - 1,
                if (newHov) C_HOVER else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, "+ New", x + 4, barY + 7,
                if (newHov) C_TEXT else C_DIM)
            x += 42
        }

        // Delete
        if (presets.size > 1) {
            val delHov = mouseX in x..(x + 44) && mouseY in barY..(barY + PRESET_H)
            context.fill(x, barY + 2, x + 44, barY + PRESET_H - 1,
                if (delHov) 0xFF_3D0A14.toInt() else 0xFF_141428.toInt())
            context.drawTextWithShadow(textRenderer, "Delete", x + 4, barY + 7,
                if (delHov) C_HINT else C_DIM)
        }
    }

    private fun drawDeleteConfirm(context: DrawContext) {
        val mw = 200; val mh = 70
        val mx = width / 2 - mw / 2; val my = height / 2 - mh / 2
        context.fill(mx - 2, my - 2, mx + mw + 2, my + mh + 2, 0xFF_E94560.toInt())
        context.fill(mx, my, mx + mw, my + mh, 0xFF_0A0A1A.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Delete preset?", width / 2, my + 10, 0xFF_FFFFFF.toInt())
        val presetName = ConfigManager.config.presets.getOrNull(ConfigManager.activePresetIndex)?.name ?: ""
        context.drawCenteredTextWithShadow(textRenderer, "\"$presetName\"", width / 2, my + 22, 0xFF_E94560.toInt())
        context.fill(mx + 10, my + 38, mx + 84, my + 56, 0xFF_3D0A14.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Yes, delete", mx + 47, my + 44, 0xFF_FF4466.toInt())
        context.fill(mx + mw - 84, my + 38, mx + mw - 10, my + 56, 0xFF_1A1A2E.toInt())
        context.drawCenteredTextWithShadow(textRenderer, "Cancel", mx + mw - 47, my + 44, 0xFF_AAAAAA.toInt())
    }

    private fun drawIconTooltip(context: DrawContext) {
        val lines = listOf(
            "Icon field accepts:" to C_HINT,
            "" to C_DIM,
            "Minecraft item ID, e.g.:" to C_DIM,
            "  minecraft:diamond_sword" to C_TEXT,
            "  minecraft:nether_star" to C_TEXT,
            "" to C_DIM,
            "OR" to C_HINT,
            "" to C_DIM,
            "Base64 skin value from" to C_DIM,
            "minecraft-heads.com:" to C_DIM,
            "  Find a head → scroll to" to C_TEXT,
            "  'For Developers' → copy" to C_TEXT,
            "  the Value field & paste" to C_TEXT,
        )
        val tw = lines.maxOf { textRenderer.getWidth(it.first) }
        val th = lines.size * 10 + 8
        val tx = (FORM_X + textRenderer.getWidth("Icon  ") - 2).coerceAtMost(width - tw - 10)
        val ty = (FORM_Y + 58).coerceAtMost(height - th - 4)
        context.fill(tx - 2, ty - 2, tx + tw + 4, ty + th, 0xFF_0A0A1A.toInt())
        context.fill(tx - 2, ty - 2, tx + tw + 4, ty - 1, C_HINT)
        lines.forEachIndexed { i, (line, color) ->
            if (line.isNotEmpty())
                context.drawTextWithShadow(textRenderer, line, tx, ty + 2 + i * 10, color)
        }
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        val pby = TITLE_H + 2
        val presets = ConfigManager.config.presets

        // Preset bar
        if (my in pby..(pby + PRESET_H)) {
            var x = LIST_X
            if (mx in x..(x + 14)) {
                val prev = (ConfigManager.activePresetIndex - 1 + presets.size) % presets.size
                ConfigManager.switchPreset(prev); return true
            }
            x += 16
            presets.forEachIndexed { i, preset ->
                val label = if (preset.name.length > 12) preset.name.take(11) + "…" else preset.name
                val tw = textRenderer.getWidth(label) + 10
                if (mx in x..(x + tw)) {
                    if (i == ConfigManager.activePresetIndex && doubled) {
                        startRename(i, x, pby)
                    } else {
                        ConfigManager.switchPreset(i)
                        selectedRow = null; scrollOffset = 0; populateFields()
                    }
                    return true
                }
                x += tw + 3
            }
            if (mx in x..(x + 14)) {
                val next = (ConfigManager.activePresetIndex + 1) % presets.size
                ConfigManager.switchPreset(next); return true
            }
            x += 18
            if (presets.size < ConfigManager.MAX_PRESETS && mx in x..(x + 38)) {
                ConfigManager.addPreset("Preset ${presets.size + 1}")
                selectedRow = null; scrollOffset = 0; populateFields(); return true
            }
            x += 42
            if (presets.size > 1 && mx in x..(x + 44)) {
                confirmDeletePreset = true; return true
            }
            return true
        }

        // List
        if (mx in LIST_X..(LIST_X + LIST_W) && my in LIST_Y..(LIST_Y + LIST_H)) {
            val rows = buildRows()
            val i = scrollOffset + ((my - LIST_Y) / ITEM_H)
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
        renameField = field
        addDrawableChild(field)
        focused = field
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val rows = buildRows()
        scrollOffset = (scrollOffset - verticalAmount.toInt())
            .coerceIn(0, (rows.size - VISIBLE).coerceAtLeast(0))
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
        if (renamingPreset) {
            renameField?.let { remove(it) }; renameField = null; renamingPreset = false; return false
        }
        ConfigManager.save(); return true
    }

    override fun shouldPause() = false
}
