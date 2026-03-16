package com.bontelempi.radialmenu.screen

import com.bontelempi.radialmenu.config.ConfigManager
import com.bontelempi.radialmenu.model.MenuItem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import com.bontelempi.radialmenu.config.ThemeManager
import com.bontelempi.radialmenu.util.HeadUtil
import kotlin.math.*

class RadialMenuScreen : Screen(Text.literal("Radial Menu")) {

    private val CENTER_R  = 36f
    private val INNER_R   = 38f
    private val MID_R     = 105f
    private val OUTER_R   = 168f
    private val GAP       = 3f
    private val ICON_SIZE = 16

    // Minimum degrees per outer segment — prevents cramping with many children
    private val MIN_OUTER_SEG_DEG = 28.0

    // Colours from ThemeManager — read per-frame so theme changes apply live
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

    private val rootItems get() = ConfigManager.config.rootItems
    private var activeFolderIdx = -1
    private var hoveredOuterIdx = -1
    private var hoveredInnerIdx = -1
    private var mouseInOuter    = false

    private enum class Zone { CENTER, INNER, OUTER, OUTSIDE }

    // ── Angle convention ──────────────────────────────────────────────────────
    // All angles: 0–360° clockwise from TOP (12 o'clock).
    private fun ringAngleOf(dx: Double, dy: Double): Double =
        (Math.toDegrees(atan2(dy, dx)) + 90 + 360) % 360

    private fun dist(mx: Double, my: Double) =
        sqrt((mx - width / 2.0).pow(2) + (my - height / 2.0).pow(2))

    private fun mouseZone(mx: Double, my: Double): Zone {
        val d = dist(mx, my)
        return when {
            d < CENTER_R          -> Zone.CENTER
            d < MID_R - GAP / 2  -> Zone.INNER
            d < OUTER_R           -> Zone.OUTER
            else                  -> Zone.OUTSIDE
        }
    }

    private fun innerIdxForAngle(angleDeg: Double): Int {
        if (rootItems.isEmpty()) return -1
        val seg = 360.0 / rootItems.size
        return (angleDeg / seg).toInt().coerceIn(0, rootItems.lastIndex)
    }

    // ── Outer ring geometry ───────────────────────────────────────────────────
    /**
     * Returns (arcStartDeg, segDeg) for the outer ring of the active folder.
     *
     * The arc is at least (children.size * MIN_OUTER_SEG_DEG) wide.
     * If the folder's own inner-ring segment is wide enough, we use that.
     * Otherwise we expand the arc, keeping it centred on the folder's midpoint,
     * and clamping so it never wraps beyond 360°.
     */
    private fun outerArcGeometry(children: List<MenuItem>): Pair<Double, Double>? {
        if (children.isEmpty()) return null
        val innerSeg = 360.0 / rootItems.size
        val folderMid = innerSeg * activeFolderIdx + innerSeg / 2   // mid of folder in top-based degrees

        val segDeg = maxOf(innerSeg / children.size, MIN_OUTER_SEG_DEG)
        val totalArc = segDeg * children.size

        // Centre the arc on the folder's midpoint
        var arcStart = folderMid - totalArc / 2
        // Normalise to 0–360
        arcStart = ((arcStart % 360) + 360) % 360

        return Pair(arcStart, segDeg)
    }

    private fun outerIdxForAngle(angleDeg: Double): Int {
        val folder = rootItems.getOrNull(activeFolderIdx) ?: return -1
        val children = folder.children.ifEmpty { return -1 }
        val (arcStart, segDeg) = outerArcGeometry(children) ?: return -1
        val totalArc = segDeg * children.size

        // Compute angle relative to arc start (handles wrap-around)
        var rel = (angleDeg - arcStart + 360) % 360
        if (rel > 360 - segDeg * 0.5) rel -= 360  // handle tiny wrap at end
        if (rel < 0 || rel >= totalArc) return -1
        return (rel / segDeg).toInt().coerceIn(0, children.lastIndex)
    }

    private fun updateHover(mx: Double, my: Double) {
        val cx = width / 2.0; val cy = height / 2.0
        val zone = mouseZone(mx, my)
        val angle = ringAngleOf(mx - cx, my - cy)
        mouseInOuter = zone == Zone.OUTER
        when (zone) {
            Zone.INNER -> {
                hoveredInnerIdx = innerIdxForAngle(angle)
                hoveredOuterIdx = -1
                val item = rootItems.getOrNull(hoveredInnerIdx)
                if (item?.isFolder == true) activeFolderIdx = hoveredInnerIdx
                else if (item != null) activeFolderIdx = -1
            }
            Zone.OUTER -> {
                hoveredInnerIdx = -1
                hoveredOuterIdx = outerIdxForAngle(angle)
            }
            else -> { hoveredInnerIdx = -1; hoveredOuterIdx = -1 }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        updateHover(mouseX.toDouble(), mouseY.toDouble())
        context.fill(0, 0, width, height, ThemeManager.c(ThemeManager.theme.backgroundDim))
        val cx = width / 2; val cy = height / 2

        // Outer ring
        val folder = rootItems.getOrNull(activeFolderIdx)
        val children = folder?.children
        val outerGeo = if (!children.isNullOrEmpty()) outerArcGeometry(children) else null

        drawRingFast(context, cx, cy, MID_R + GAP, OUTER_R) { dx, dy ->
            if (outerGeo == null || children.isNullOrEmpty()) return@drawRingFast C_TRANSPARENT
            val angle = ringAngleOf(dx.toDouble(), dy.toDouble())
            val idx = outerIdxForAngle(angle)
            if (idx < 0) C_TRANSPARENT
            else if (idx == hoveredOuterIdx && mouseInOuter) C_OUTER_HOVER
            else C_OUTER_NORMAL
        }

        // Inner ring
        drawRingFast(context, cx, cy, INNER_R, MID_R) { dx, dy ->
            if (rootItems.isEmpty()) return@drawRingFast C_TRANSPARENT
            val angle = ringAngleOf(dx.toDouble(), dy.toDouble())
            val idx = innerIdxForAngle(angle)
            val item = rootItems[idx]
            val isHov = idx == hoveredInnerIdx && !mouseInOuter
            val isFolderOpen = idx == activeFolderIdx
            when {
                mouseInOuter -> blend(if (item.isFolder) C_INNER_FOLDER else C_INNER_NORMAL, C_INNER_DIM, 0.6f)
                item.isFolder && (isHov || isFolderOpen) -> C_INNER_FOLDER_H
                item.isFolder -> C_INNER_FOLDER
                isHov -> C_INNER_HOVER_BTN
                else -> C_INNER_NORMAL
            }
        }

        drawFilledCircle(context, cx, cy, CENTER_R.toInt(), C_CENTER)
        context.drawCenteredTextWithShadow(textRenderer, "✕", cx, cy - 10, C_TEXT_DIM)
        context.drawCenteredTextWithShadow(textRenderer, "E to edit", cx, cy + 2, C_TEXT_DIM)
        drawInnerLabels(context, cx, cy)
        drawOuterLabels(context, cx, cy)
        drawTooltip(context, cx)
        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawRingFast(
        context: DrawContext,
        cx: Int, cy: Int,
        innerR: Float, outerR: Float,
        colorAt: (dx: Int, dy: Int) -> Int
    ) {
        val outerR2 = outerR * outerR
        val innerR2 = innerR * innerR
        val iOuter = outerR.toInt()

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

    private fun drawInnerLabels(context: DrawContext, cx: Int, cy: Int) {
        if (rootItems.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No items – press E to edit", cx, cy - 4, C_TEXT_DIM)
            return
        }
        val segDeg = 360.0 / rootItems.size
        rootItems.forEachIndexed { i, item ->
            val midRad = Math.toRadians(segDeg * i + segDeg / 2 - 90)
            val midDist = (INNER_R + MID_R) / 2
            val ix = cx + (cos(midRad) * midDist).toInt()
            val iy = cy + (sin(midRad) * midDist).toInt()
            val isHov = i == hoveredInnerIdx && !mouseInOuter
            val isFolderOpen = i == activeFolderIdx
            val tc = if (mouseInOuter) C_TEXT_DIM else if (isHov || isFolderOpen) C_TEXT_BRIGHT else C_TEXT_DIM
            drawItemIcon(context, item.iconItem, ix - ICON_SIZE / 2, iy - ICON_SIZE / 2 - 8,
                if (mouseInOuter) 0.45f else 1.0f, item.headTexture)
            context.drawCenteredTextWithShadow(textRenderer, item.label, ix, iy + 10, tc)
            if (item.isFolder)
                context.drawCenteredTextWithShadow(textRenderer,
                    if (isFolderOpen) "◀" else "▶", ix, iy + 20,
                    if (isFolderOpen) C_TEXT_BRIGHT else C_TEXT_DIM)
        }
    }

    private fun drawOuterLabels(context: DrawContext, cx: Int, cy: Int) {
        val folder = rootItems.getOrNull(activeFolderIdx) ?: return
        val children = folder.children.ifEmpty { return }
        val (arcStart, segDeg) = outerArcGeometry(children) ?: return

        children.forEachIndexed { i, item ->
            val midRad = Math.toRadians(arcStart + segDeg * i + segDeg / 2 - 90)
            val midDist = (MID_R + GAP + OUTER_R) / 2
            val ix = cx + (cos(midRad) * midDist).toInt()
            val iy = cy + (sin(midRad) * midDist).toInt()
            val isHov = i == hoveredOuterIdx && mouseInOuter
            drawItemIcon(context, item.iconItem, ix - ICON_SIZE / 2, iy - ICON_SIZE / 2 - 8, 1.0f, item.headTexture)
            context.drawCenteredTextWithShadow(textRenderer, item.label, ix, iy + 10,
                if (isHov) C_TEXT_BRIGHT else C_TEXT_DIM)
        }
    }

    private fun drawTooltip(context: DrawContext, cx: Int) {
        val hint = when {
            mouseInOuter && hoveredOuterIdx >= 0 ->
                rootItems.getOrNull(activeFolderIdx)?.children?.getOrNull(hoveredOuterIdx)?.command?.let { "/$it" }
            !mouseInOuter && hoveredInnerIdx >= 0 -> {
                val item = rootItems.getOrNull(hoveredInnerIdx)
                when {
                    item == null  -> null
                    item.isFolder -> "▸ ${item.label}  (${item.children.size} items)"
                    else          -> "/${item.command}"
                }
            }
            else -> null
        } ?: return
        context.drawCenteredTextWithShadow(textRenderer, hint, cx, (height / 2 + OUTER_R + 14).toInt(), C_TEXT_DIM)
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x(); val my = click.y()
        if (click.button() == 1) { close(); return true }
        when (mouseZone(mx, my)) {
            Zone.CENTER -> { close(); return true }
            Zone.OUTER  -> {
                val child = rootItems.getOrNull(activeFolderIdx)?.children?.getOrNull(hoveredOuterIdx)
                if (child != null && !child.isFolder) executeCommand(child.command)
                return true
            }
            Zone.INNER  -> {
                val item = rootItems.getOrNull(hoveredInnerIdx)
                if (item != null && !item.isFolder) executeCommand(item.command)
                return true
            }
            else -> return super.mouseClicked(click, doubled)
        }
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
            MinecraftClient.getInstance().setScreen(EditorScreen(ConfigManager.config.rootItems))
            return true
        }
        return super.keyPressed(input)
    }

    override fun shouldPause() = false

    // ── Drawing helpers ───────────────────────────────────────────────────────
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

    private fun argb(a: Float, r: Float, g: Float, b: Float) =
        ((a * 255).toInt() shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()

    private fun blend(colorA: Int, colorB: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c ushr s and 0xFF)
        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return (lerp(ch(colorA,24),ch(colorB,24)) shl 24) or (lerp(ch(colorA,16),ch(colorB,16)) shl 16) or
               (lerp(ch(colorA,8),ch(colorB,8)) shl 8) or lerp(ch(colorA,0),ch(colorB,0))
    }

    private fun itemStackOf(id: String): ItemStack {
        // Detect base64 skin value — they're long, contain no colons, and decode to JSON
        if (id.length > 40 && !id.contains(':')) {
            val stack = HeadUtil.buildStack(id)
            if (stack.item != net.minecraft.item.Items.AIR) return stack
        }
        val ident = runCatching { Identifier.of(id) }.getOrElse { Identifier.of("minecraft:barrier") }
        return ItemStack(Registries.ITEM[ident])
    }
}
