package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ConfigManager
import com.bontelempi.radialmenu.config.ThemeManager
import com.bontelempi.radialmenu.model.MenuItem
import com.bontelempi.radialmenu.util.HeadUtil
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.*

/**
 * Multi-level radial menu — up to 4 rings deep.
 *
 * Ring 0 (innermost) = root items          INNER_R → RING_RADII[0]
 * Ring 1             = level-1 sub-menu    RING_RADII[0]+GAP → RING_RADII[1]
 * Ring 2             = level-2 sub-menu    RING_RADII[1]+GAP → RING_RADII[2]
 * Ring 3 (outermost) = level-3 sub-menu    RING_RADII[2]+GAP → RING_RADII[3]
 *
 * Hovering a folder on ring N opens ring N+1 aligned to that segment's arc.
 * Moving the mouse back to ring N collapses rings N+1 and deeper.
 */
class RadialMenuScreen : Screen(Text.literal("Radial Menu")) {

    // ── Geometry ──────────────────────────────────────────────────────────────
    private val CENTER_R = 36f
    private val INNER_R  = 38f
    private val GAP      = 4f
    // Outer edge of each ring level (index 0 = innermost ring outer edge)
    private val RING_RADII = floatArrayOf(105f, 162f, 219f, 276f)
    private val MAX_LEVELS = 4
    private val ICON_SIZE  = 16

    // ── Colours ───────────────────────────────────────────────────────────────
    private val C_INNER_NORMAL    get() = ThemeManager.c(ThemeManager.theme.innerSegment)
    private val C_INNER_HOVER_BTN get() = ThemeManager.c(ThemeManager.theme.hoverHighlight)
    private val C_INNER_FOLDER    get() = ThemeManager.c(ThemeManager.theme.innerFolder)
    private val C_INNER_FOLDER_H  get() = blend(ThemeManager.c(ThemeManager.theme.innerFolder), ThemeManager.c(ThemeManager.theme.hoverHighlight), 0.4f)
    private val C_INNER_DIM       get() = blend(ThemeManager.c(ThemeManager.theme.innerSegment), 0x00_000000, 0.5f)
    private val C_OUTER_NORMAL    get() = ThemeManager.c(ThemeManager.theme.outerSegment)
    private val C_OUTER_HOVER     get() = ThemeManager.c(ThemeManager.theme.hoverHighlight)
    private val C_CENTER          get() = ThemeManager.c(ThemeManager.theme.centerCircle)
    private val C_TEXT_DIM        get() = ThemeManager.c(ThemeManager.theme.textDim)
    private val C_TEXT_BRIGHT     get() = ThemeManager.c(ThemeManager.theme.textPrimary)
    private val C_TRANSPARENT     = 0x00_000000

    // ── State ─────────────────────────────────────────────────────────────────
    private val rootItems get() = ConfigManager.rootItems

    /**
     * Active hover path through the menu tree.
     * Each entry = the index of the hovered item at that ring level.
     * activeIndices[0] = which root item is hovered/active
     * activeIndices[1] = which child of that root item is hovered, etc.
     * Length = number of rings currently showing (0 = only inner ring showing)
     */
    private val activeIndices = mutableListOf<Int>()

    /** Which ring level the mouse is currently in (-1 = none/center) */
    private var mouseRingLevel = -1

    // ── Ring geometry helpers ─────────────────────────────────────────────────

    private fun ringInnerR(level: Int) = if (level == 0) INNER_R else RING_RADII[level - 1] + GAP
    private fun ringOuterR(level: Int) = RING_RADII[level]

    private fun mouseZoneLevel(mx: Double, my: Double): Int {
        val d = dist(mx, my)
        return when {
            d < CENTER_R -> -2  // centre
            d < ringInnerR(0) -> -1  // dead zone between centre and ring 0
            else -> {
                for (level in 0 until MAX_LEVELS) {
                    if (d < ringOuterR(level)) return level
                }
                MAX_LEVELS  // outside all rings
            }
        }
    }

    private fun dist(mx: Double, my: Double) =
        sqrt((mx - width / 2.0).pow(2) + (my - height / 2.0).pow(2))

    private fun ringAngleOf(dx: Double, dy: Double) =
        (Math.toDegrees(atan2(dy, dx)) + 90 + 360) % 360

    /**
     * Given an angle and the arc geometry of a ring level,
     * return the segment index the angle falls in, or -1 if outside the arc.
     *
     * @param arcStartDeg  Start of the arc in top-based degrees
     * @param segDeg       Degrees per segment
     * @param count        Number of segments
     */
    private fun segmentAtAngle(angleDeg: Double, arcStartDeg: Double, segDeg: Double, count: Int): Int {
        var rel = (angleDeg - arcStartDeg + 360) % 360
        if (rel >= segDeg * count) return -1
        return (rel / segDeg).toInt().coerceIn(0, count - 1)
    }

    /**
     * Compute the arc geometry for a given ring level.
     * Returns (arcStartDeg, segDeg) or null if that level isn't active.
     *
     * Level 0 always spans full 360°.
     * Deeper levels span the arc of the parent segment, expanded if needed.
     */
    private fun arcGeometry(level: Int): Pair<Double, Double>? {
        val items = itemsAtLevel(level) ?: return null
        if (items.isEmpty()) return null

        if (level == 0) {
            val segDeg = 360.0 / items.size
            return Pair(0.0, segDeg)
        }

        // Parent arc
        val parentItems = itemsAtLevel(level - 1) ?: return null
        val parentIdx   = activeIndices.getOrNull(level - 1) ?: return null
        val (parentArcStart, parentSegDeg) = arcGeometry(level - 1) ?: return null
        val parentMid   = parentArcStart + parentSegDeg * parentIdx + parentSegDeg / 2

        val minSegDeg = 28.0
        val segDeg    = maxOf(parentSegDeg / items.size, minSegDeg)
        val totalArc  = segDeg * items.size
        val arcStart  = ((parentMid - totalArc / 2) % 360 + 360) % 360

        return Pair(arcStart, segDeg)
    }

    /**
     * Returns the list of items displayed at a given ring level,
     * or null if that level isn't currently active.
     */
    private fun itemsAtLevel(level: Int): List<MenuItem>? {
        if (level == 0) return rootItems
        // Need activeIndices[0..level-1] to navigate down the tree
        if (activeIndices.size < level) return null
        var items: List<MenuItem> = rootItems
        for (i in 0 until level) {
            val idx = activeIndices.getOrNull(i) ?: return null
            val item = items.getOrNull(idx) ?: return null
            if (!item.isFolder) return null
            items = item.children
        }
        return items.ifEmpty { null }
    }

    // ── Hover update ──────────────────────────────────────────────────────────

    private fun updateHover(mx: Double, my: Double) {
        val cx = width / 2.0; val cy = height / 2.0
        val level = mouseZoneLevel(mx, my)
        val angle = ringAngleOf(mx - cx, my - cy)

        mouseRingLevel = level

        when {
            level < 0 -> {
                // Centre or dead zone — collapse all
                activeIndices.clear()
            }
            level < MAX_LEVELS -> {
                val (arcStart, segDeg) = arcGeometry(level) ?: run {
                    // This level isn't active yet — do nothing
                    return
                }
                val items = itemsAtLevel(level) ?: return
                val idx = segmentAtAngle(angle, arcStart, segDeg, items.size)
                if (idx < 0) return

                // Truncate active path to this level and set hovered index
                while (activeIndices.size > level) activeIndices.removeLast()
                if (activeIndices.size == level) activeIndices.add(idx)
                else activeIndices[level] = idx

                // If hovered item is a folder, mark it active so next level renders
                // (don't push yet — next frame will compute the deeper arc)
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        updateHover(mouseX.toDouble(), mouseY.toDouble())
        context.fill(0, 0, width, height, ThemeManager.c(ThemeManager.theme.backgroundDim))

        val cx = width / 2; val cy = height / 2

        // Determine which level the mouse is in for dimming
        val activeLevelForDim = if (mouseRingLevel >= 0) mouseRingLevel else 0

        // Draw rings from outermost to innermost so inner overlaps outer
        val deepestActiveLevel = run {
            var d = 0
            for (l in 0 until MAX_LEVELS) {
                if (itemsAtLevel(l) != null) d = l else break
            }
            d
        }

        for (level in deepestActiveLevel downTo 0) {
            drawRingLevel(context, cx, cy, level, activeLevelForDim)
        }

        // Centre circle
        drawFilledCircle(context, cx, cy, CENTER_R.toInt(), C_CENTER)
        context.drawCenteredTextWithShadow(textRenderer, "✕", cx, cy - 10, C_TEXT_DIM)
        context.drawCenteredTextWithShadow(textRenderer, "E to edit", cx, cy + 2, C_TEXT_DIM)

        drawTooltip(context, cx)
        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawRingLevel(context: DrawContext, cx: Int, cy: Int, level: Int, activeLevel: Int) {
        val items = itemsAtLevel(level) ?: return
        val (arcStart, segDeg) = arcGeometry(level) ?: return
        val innerR = ringInnerR(level)
        val outerR = ringOuterR(level)
        val hoveredIdx = activeIndices.getOrNull(level) ?: -1
        val isInnermost = level == 0
        val isDimmed = mouseRingLevel >= 0 && level < activeLevel

        drawRingFast(context, cx, cy, innerR, outerR) { dx, dy ->
            val angle = ringAngleOf(dx.toDouble(), dy.toDouble())
            val idx = segmentAtAngle(angle, arcStart, segDeg, items.size)
            if (idx < 0) return@drawRingFast C_TRANSPARENT
            val item = items[idx]
            val isHov = idx == hoveredIdx && mouseRingLevel == level

            if (isInnermost) {
                when {
                    isDimmed -> blend(if (item.isFolder) C_INNER_FOLDER else C_INNER_NORMAL, C_INNER_DIM, 0.6f)
                    item.isFolder && isHov -> C_INNER_FOLDER_H
                    item.isFolder -> C_INNER_FOLDER
                    isHov -> C_INNER_HOVER_BTN
                    else -> C_INNER_NORMAL
                }
            } else {
                when {
                    isHov -> C_OUTER_HOVER
                    else  -> C_OUTER_NORMAL
                }
            }
        }

        // Labels
        items.forEachIndexed { i, item ->
            val midDeg = arcStart + segDeg * i + segDeg / 2
            val midRad = Math.toRadians(midDeg - 90)
            val midDist = (innerR + outerR) / 2
            val ix = cx + (cos(midRad) * midDist).toInt()
            val iy = cy + (sin(midRad) * midDist).toInt()
            val isHov = i == hoveredIdx && mouseRingLevel == level
            val alpha = if (isDimmed) 0.45f else 1.0f
            val tc = if (isDimmed) C_TEXT_DIM else if (isHov) C_TEXT_BRIGHT else C_TEXT_DIM

            drawItemIcon(context, item.iconItem, ix - ICON_SIZE / 2, iy - ICON_SIZE / 2 - 8, alpha, item.headTexture)
            context.drawCenteredTextWithShadow(textRenderer, item.label, ix, iy + 10, tc)
            if (item.isFolder) {
                val arrow = if (i == hoveredIdx && activeIndices.size > level + 1) "◀" else "▶"
                context.drawCenteredTextWithShadow(textRenderer, arrow, ix, iy + 20,
                    if (isHov) C_TEXT_BRIGHT else C_TEXT_DIM)
            }
        }
    }

    private fun drawTooltip(context: DrawContext, cx: Int) {
        val level = mouseRingLevel.coerceAtLeast(0)
        val idx   = activeIndices.getOrNull(level) ?: return
        val items = itemsAtLevel(level) ?: return
        val item  = items.getOrNull(idx) ?: return

        val hint = when {
            item.isFolder && item.command.isBlank() -> "▸ ${item.label}  (${item.children.size} items)"
            item.isFolder -> "/${item.command}  •  ▸ ${item.children.size} items"
            else -> "/${item.command}"
        }
        val outerY = (height / 2 + RING_RADII[minOf(level, RING_RADII.lastIndex)] + 14).toInt()
        context.drawCenteredTextWithShadow(textRenderer, hint, cx, outerY, C_TEXT_DIM)
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x(); val my = click.y()
        if (click.button() == 1) { close(); return true }

        val level = mouseZoneLevel(mx, my)
        when {
            level == -2 -> { close(); return true }  // centre
            level in 0 until MAX_LEVELS -> {
                val idx  = activeIndices.getOrNull(level) ?: return true
                val item = itemsAtLevel(level)?.getOrNull(idx) ?: return true
                executeCommand(item.command)
                return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    private fun executeCommand(cmd: String) {
        if (cmd.isBlank()) return
        close()
        MinecraftClient.getInstance().send {
            MinecraftClient.getInstance().player?.networkHandler?.sendChatCommand(cmd)
        }
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.key() == 69) {
            MinecraftClient.getInstance().setScreen(EditorScreen())
            return true
        }
        return super.keyPressed(input)
    }

    override fun shouldPause() = false

    // ── Scanline ring renderer ─────────────────────────────────────────────────

    private fun drawRingFast(
        context: DrawContext,
        cx: Int, cy: Int,
        innerR: Float, outerR: Float,
        colorAt: (dx: Int, dy: Int) -> Int
    ) {
        val outerR2 = outerR * outerR
        val innerR2 = innerR * innerR
        val iOuter  = outerR.toInt()

        for (dy in -iOuter..iOuter) {
            val dy2 = dy * dy.toFloat()
            if (dy2 > outerR2) continue
            val xOuter = sqrt((outerR2 - dy2).toDouble()).toInt()
            val xInner = if (dy2 < innerR2) sqrt((innerR2 - dy2).toDouble()).toInt() else 0
            val y = cy + dy

            for (side in 0..1) {
                val xStart = if (side == 0) -xOuter else xInner
                val xEnd   = if (side == 0) -xInner else xOuter
                var spanStart = xStart
                var spanColor = colorAt(xStart, dy)

                for (dx in (xStart + 1)..xEnd) {
                    val c = colorAt(dx, dy)
                    if (c != spanColor) {
                        if (spanColor != C_TRANSPARENT)
                            context.fill(cx + spanStart, y, cx + dx, y + 1, spanColor)
                        spanStart = dx; spanColor = c
                    }
                }
                if (spanColor != C_TRANSPARENT)
                    context.fill(cx + spanStart, y, cx + xEnd + 1, y + 1, spanColor)
            }
        }
    }

    private fun drawFilledCircle(context: DrawContext, cx: Int, cy: Int, radius: Int, color: Int) {
        for (y in -radius..radius) {
            val hw = sqrt((radius * radius - y * y).toDouble()).toInt()
            context.fill(cx - hw, cy + y, cx + hw, cy + y + 1, color)
        }
    }

    private fun drawItemIcon(context: DrawContext, itemId: String, x: Int, y: Int, alpha: Float, headTexture: String? = null) {
        val stack = if (!headTexture.isNullOrBlank()) HeadUtil.buildStack(headTexture)
                    else itemStackOf(itemId)
        context.drawItem(stack, x, y)
        if (alpha < 0.99f) context.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, argb(1f - alpha, 0f, 0f, 0f))
    }

    // ── Colour / item helpers ─────────────────────────────────────────────────

    private fun argb(a: Float, r: Float, g: Float, b: Float) =
        ((a * 255).toInt() shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()

    private fun blend(colorA: Int, colorB: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c ushr s and 0xFF)
        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return (lerp(ch(colorA,24),ch(colorB,24)) shl 24) or (lerp(ch(colorA,16),ch(colorB,16)) shl 16) or
               (lerp(ch(colorA,8),ch(colorB,8)) shl 8) or lerp(ch(colorA,0),ch(colorB,0))
    }

    private fun itemStackOf(id: String): ItemStack {
        if (id.length > 40 && !id.contains(':')) {
            val stack = HeadUtil.buildStack(id)
            if (stack.item != net.minecraft.item.Items.AIR) return stack
        }
        val ident = runCatching { Identifier.of(id) }.getOrElse { Identifier.of("minecraft:barrier") }
        return ItemStack(Registries.ITEM[ident])
    }
}
