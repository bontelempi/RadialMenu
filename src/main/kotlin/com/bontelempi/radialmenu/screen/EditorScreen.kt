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

class EditorScreen(
    private val targetList: MutableList<MenuItem>
) : Screen(Text.literal("Radial Menu Editor")) {

    // ── List state ─────────────────────────────────────────────────────────────
    // A "row" is either a root item or a child item shown when its parent is expanded.
    private data class ListRow(
        val item: MenuItem,
        val parentList: MutableList<MenuItem>,
        val indexInParent: Int,
        val depth: Int           // 0 = root, 1 = child
    )

    private val expandedFolders = mutableSetOf<String>() // item IDs of expanded folders
    private var selectedRow: ListRow? = null
    private var scrollOffset = 0

    // ── Form widgets ────────────────────────────────────────────────────────────
    private lateinit var labelField:   TextFieldWidget
    private lateinit var iconField:    TextFieldWidget
    private lateinit var commandField: TextFieldWidget

    // ── Geometry ────────────────────────────────────────────────────────────────
    private val LIST_X  get() = 10
    private val LIST_Y  get() = 30
    private val LIST_W  get() = (width * 0.40).toInt()
    private val LIST_H  get() = height - 80
    private val FORM_X  get() = LIST_X + LIST_W + 14
    private val FORM_Y  get() = LIST_Y
    private val ITEM_H  = 20
    private val VISIBLE get() = LIST_H / ITEM_H

    // ── Colours ─────────────────────────────────────────────────────────────────
    private val C_BG       = 0xCC_111122.toInt()
    private val C_PANEL    = 0xCC_1A1A2E.toInt()
    private val C_SELECTED = 0xCC_E94560.toInt()
    private val C_HOVER    = 0xCC_333355.toInt()
    private val C_CHILD_BG = 0x44_FFFFFF.toInt()
    private val C_TEXT     = 0xFF_FFFFFF.toInt()
    private val C_DIM      = 0xFF_AAAAAA.toInt()
    private val C_LABEL    = 0xFF_88AAFF.toInt()
    private val C_HINT     = 0xFF_E94560.toInt()
    private val C_SUCCESS  = 0xFF_55FF55.toInt()

    private var showIconTooltip = false

    // ── Build flat row list ──────────────────────────────────────────────────────
    private fun buildRows(): List<ListRow> {
        val rows = mutableListOf<ListRow>()
        fun addRows(list: MutableList<MenuItem>, depth: Int) {
            list.forEachIndexed { i, item ->
                rows.add(ListRow(item, list, i, depth))
                if (item.isFolder && item.id in expandedFolders) {
                    addRows(item.children, depth + 1)
                }
            }
        }
        addRows(targetList, 0)
        return rows
    }

    override fun init() {
        val fy = FORM_Y

        labelField = TextFieldWidget(textRenderer, FORM_X, fy + 20, 200, 18, Text.literal("Label"))
        labelField.setMaxLength(40)
        labelField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(label = v)
                // Refresh selected row reference
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(labelField)

        iconField = TextFieldWidget(textRenderer, FORM_X, fy + 60, 200, 18, Text.literal("Icon"))
        iconField.setMaxLength(8192)
        iconField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(iconItem = v, headTexture = null)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(iconField)

        commandField = TextFieldWidget(textRenderer, FORM_X, fy + 100, 200, 18, Text.literal("Command"))
        commandField.setMaxLength(256)
        commandField.setChangedListener { v: String ->
            selectedRow?.let { row ->
                row.parentList[row.indexInParent] = row.item.copy(command = v)
                selectedRow = selectedRow?.copy(item = row.parentList[row.indexInParent])
                ConfigManager.save()
            }
        }
        addDrawableChild(commandField)

        val bY = height - 44
        var bX = LIST_X

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add")) {
            val item = MenuItem(ConfigManager.newId(), "New Item", "minecraft:paper", "")
            val row = selectedRow
            when {
                // Selected item is a folder — add as child
                row != null && row.item.isFolder -> {
                    row.item.children.add(item)
                    expandedFolders.add(row.item.id)
                }
                // Selected item is a child — add sibling after it
                row != null && row.depth > 0 -> {
                    row.parentList.add(row.indexInParent + 1, item)
                }
                // Nothing selected or root item selected — add to root
                else -> targetList.add(item)
            }
            ConfigManager.save()
            val rows = buildRows()
            selectedRow = rows.find { it.item.id == item.id }
            populateFields()
        }.dimensions(bX, bY, 54, 20).build().also { bX += 58 })

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete")) {
            selectedRow?.let { row ->
                row.parentList.removeAt(row.indexInParent)
                selectedRow = null
                populateFields()
                ConfigManager.save()
            }
        }.dimensions(bX, bY, 54, 20).build().also { bX += 58 })

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
        }.dimensions(bX, bY, 44, 20).build().also { bX += 48 })

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
        }.dimensions(bX, bY, 54, 20).build().also { bX += 58 })

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Sub")) {
            selectedRow?.let { row ->
                val child = MenuItem(ConfigManager.newId(), "Sub Item", "minecraft:paper", "")
                row.item.children.add(child)
                expandedFolders.add(row.item.id)
                ConfigManager.save()
            }
        }.dimensions(bX, bY, 56, 20).build().also { bX += 60 })

        // Theme button
        addDrawableChild(ButtonWidget.builder(Text.literal("🎨 Theme")) {
            MinecraftClient.getInstance().setScreen(ThemeScreen(this))
        }.dimensions(width - 210, bY, 80, 20).build())

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close")) {
            ConfigManager.save(); close()
        }.dimensions(width - 124, bY, 120, 20).build())

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

    // ── Render ──────────────────────────────────────────────────────────────────
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, C_BG)
        context.fill(0, 0, width, 24, C_PANEL)
        context.drawCenteredTextWithShadow(textRenderer, "Radial Menu Editor", width / 2, 7, C_TEXT)
        context.drawTextWithShadow(textRenderer, "Escape to close", LIST_X, 8, C_DIM)

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

                // Row background
                val rowColor = when {
                    isSelected -> C_SELECTED
                    row.depth == 1 -> C_CHILD_BG
                    mouseX in LIST_X..(LIST_X + LIST_W) && mouseY in iy..(iy + ITEM_H) -> C_HOVER
                    else -> 0
                }
                if (rowColor != 0) context.fill(LIST_X, iy, LIST_X + LIST_W, iy + ITEM_H, rowColor)

                // Indent scales with depth
                val indent = row.depth * 12

                // Folder chevron or child dash
                val prefix = when {
                    row.item.isFolder && row.item.id in expandedFolders -> "▼ "
                    row.item.isFolder -> "▶ "
                    row.depth > 0 -> "└ "
                    else -> "  "
                }

                context.drawTextWithShadow(textRenderer,
                    "$prefix${row.item.label}",
                    LIST_X + 4 + indent, iy + 6,
                    if (isSelected) C_TEXT else if (row.depth == 1) C_DIM else C_TEXT)

                // Child count for folders
                if (row.item.isFolder) {
                    val cnt = "(${row.item.children.size})"
                    context.drawTextWithShadow(textRenderer, cnt,
                        LIST_X + LIST_W - textRenderer.getWidth(cnt) - 4, iy + 6, C_DIM)
                }
            }
        }

        // Form panel
        val fpX = FORM_X; val fpY = FORM_Y
        context.fill(fpX - 4, fpY - 4, fpX + 220, fpY + 130, C_PANEL)
        val item = selectedRow?.item
        if (item == null) {
            context.drawTextWithShadow(textRenderer, "Select an item to edit", fpX + 6, fpY + 10, C_DIM)
        } else {
            context.drawTextWithShadow(textRenderer, "Label",    fpX, fpY + 8,  C_LABEL)
            context.drawTextWithShadow(textRenderer, "Icon  ",   fpX, fpY + 48, C_LABEL)

            val hintX = fpX + textRenderer.getWidth("Icon  ")
            context.drawTextWithShadow(textRenderer, "?", hintX, fpY + 48, C_HINT)
            showIconTooltip = mouseX in hintX..(hintX + textRenderer.getWidth("?") + 2) &&
                              mouseY in (fpY + 48)..(fpY + 58)

            context.drawTextWithShadow(textRenderer, "Command (no /)", fpX, fpY + 88, C_LABEL)
            if (item.isFolder)
                context.drawTextWithShadow(textRenderer, "▸ Folder command runs on click",
                    fpX, fpY + 110, 0xFF_55FF55.toInt())
        }

        super.render(context, mouseX, mouseY, delta)

        // Tooltip drawn last (on top)
        if (showIconTooltip) drawIconTooltip(context)
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

    // ── Input ────────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        if (mx in LIST_X..(LIST_X + LIST_W) && my in LIST_Y..(LIST_Y + LIST_H)) {
            val rows = buildRows()
            val i = scrollOffset + ((my - LIST_Y) / ITEM_H)
            if (i in rows.indices) {
                val row = rows[i]
                if (row.item.isFolder) {
                    // Toggle expand
                    if (row.item.id in expandedFolders) expandedFolders.remove(row.item.id)
                    else expandedFolders.add(row.item.id)
                }
                selectedRow = row
                populateFields()
                return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val rows = buildRows()
        scrollOffset = (scrollOffset - verticalAmount.toInt())
            .coerceIn(0, (rows.size - VISIBLE).coerceAtLeast(0))
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.key() == 69 && !labelField.isFocused && !iconField.isFocused && !commandField.isFocused) {
            ConfigManager.save(); close(); return true
        }
        return super.keyPressed(input)
    }

    override fun shouldCloseOnEsc(): Boolean { ConfigManager.save(); return true }
    override fun shouldPause() = false
}
