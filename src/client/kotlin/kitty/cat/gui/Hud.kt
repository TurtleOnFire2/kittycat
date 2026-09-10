package kitty.cat.gui

import kitty.cat.KittycatClient
import kitty.cat.KittycatClient.mc
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.render.skija.SkijaShapes as GuiUtils
import kitty.cat.render.skija.SkijaDraw
import kitty.cat.render.skija.SkijaRenderer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.resources.Identifier
import java.awt.Color
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.math.sign

// Based on the HUD system from legitcatmod by BladeMasterGabe's practical-config
object Hud : Screen(net.minecraft.network.chat.Component.literal("KittycatHud")) {
    private val components = mutableListOf<Component>()

    enum class Condition(val displayName: String, val predicate: () -> Boolean) {
        Always("Always", { true }),
        Alt("Hud Insight", { KittycatClient.keybindShowHud?.isDown == true })
    }

    abstract class Component {
        internal val staticRenderConditions: MutableList<Condition>
        internal val allowedStaticRenderConditions: MutableList<Condition>

        internal val identifier: String
        internal var x: Double
        internal var y: Double
        internal var scale: Float

        constructor(
            identifier: String,
            x: Double, y: Double,
            scale: Float = 1.0f,
            staticRenderConditions: MutableList<Condition> = mutableListOf(Condition.Always),
            allowedStaticRenderConditions: MutableList<Condition> = Condition.entries.toMutableList()
        ) {
            this.identifier = identifier
            this.x = x
            this.y = y
            this.scale = scale
            this.staticRenderConditions = staticRenderConditions
            this.allowedStaticRenderConditions = allowedStaticRenderConditions

            components.add(this)
            load(this)
        }

        internal fun position(context: GuiGraphicsExtractor): Pair<Int, Int> =
            position(context.guiWidth(), context.guiHeight())

        internal fun position(width: Int, height: Int) =
            Pair((x * width).toInt(), (y * height).toInt())

        internal fun shouldRender(): Boolean =
            staticRenderConditions.any { it.predicate() }

        abstract fun render(context: GuiGraphicsExtractor)
        abstract fun example(context: GuiGraphicsExtractor)
        abstract fun bounds(): Pair<Double, Double>

        internal fun offsetBounds(context: GuiGraphicsExtractor): Pair<Int, Int> =
            offsetBounds(context.guiWidth(), context.guiHeight())

        internal open fun offsetBounds(width: Int, height: Int): Pair<Int, Int> =
            Pair(0, 0)

        internal fun internalBounds(): Pair<Double, Double> {
            val bounds = bounds()
            return Pair(bounds.first * scale, bounds.second * scale)
        }

        internal fun internalRender(context: GuiGraphicsExtractor, example: Boolean) {
            if (!example && !shouldRender()) return

            val pose = context.pose()

            pose.pushMatrix()
            pose.translate(x.toFloat() * context.guiWidth(), y.toFloat() * context.guiHeight())
            pose.scale(scale)

            if (example) example(context)
            else render(context)

            pose.popMatrix()
        }
    }

    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("kittycat/hud")

    private fun save(component: Component) {
        if (!configPath.exists()) configPath.createDirectories()
        val file = configPath.resolve(component.identifier)
        val writer = file.bufferedWriter()
        writer.write("x:${component.x}\n")
        writer.write("y:${component.y}\n")
        writer.write("scale:${component.scale}\n")
        writer.write("conditions:${component.staticRenderConditions.joinToString(",") { it.name }}\n")
        writer.close()
    }

    internal fun load(component: Component) {
        val file = configPath.resolve(component.identifier)
        if (!file.exists()) return
        val reader = file.bufferedReader()
        reader.readLines().forEach {
            val spl = it.split(":")
            if (spl.size != 2) return@forEach
            when (spl[0]) {
                "x" -> component.x = spl[1].toDouble()
                "y" -> component.y = spl[1].toDouble()
                "scale" -> component.scale = spl[1].toFloat()
                "conditions" -> {
                    component.staticRenderConditions.clear()
                    component.staticRenderConditions.addAll(
                        spl[1].split(",").mapNotNull { modifier ->
                            Condition.entries.find { it.name == modifier }
                        }
                    )
                }
            }
        }
        reader.close()
    }

    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kittycat", "hud"), Renderer)
    }

    private var selected: Component? = null
    private var openedOptions: Component? = null
    private var isDragging = false
    private var returnScreen: Screen? = null

    fun open(parent: Screen? = null) {
        returnScreen = parent
        selected = null
        openedOptions = null
        isDragging = false
        mc.setScreen(this)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        if (selected != null) selected!!.scale += sign(verticalAmount).toFloat() * 0.02f
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val x = click.x()
        val y = click.y()
        if (click.button() == 0 && x >= width / 2 - 55 && x <= width / 2 + 55 && y >= 12 && y <= 36) {
            onClose()
            return true
        }

        optionAt(x, y)?.let { condition ->
            val opened = openedOptions ?: return@let
            if (condition in opened.staticRenderConditions) opened.staticRenderConditions.remove(condition)
            else opened.staticRenderConditions.add(condition)
            return true
        }

        val clicked = components.find {
            val bounds = it.internalBounds()
            var (posX, posY) = it.position(minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
            val offset = it.offsetBounds(minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight)
            posX += offset.first
            posY += offset.second

            posX <= x && posX + bounds.first >= x && posY <= y && posY + bounds.second >= y
        }

        selected = clicked
        if (selected == null || selected != openedOptions) openedOptions = null

        if (click.button() == 1) {
            openedOptions = if (clicked == openedOptions) null else clicked
            return super.mouseClicked(click, doubled)
        }

        if (selected != null) isDragging = true

        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (isDragging && selected != null) {
            checkNotNull(minecraft)
            val window = minecraft!!.window
            selected!!.x += offsetX / window.guiScaledWidth
            selected!!.y += offsetY / window.guiScaledHeight
        }

        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        isDragging = false
        return super.mouseReleased(click)
    }

    private val Renderer = HudElement { context, _ ->
        components.forEach { it.internalRender(context, false) }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val graphics = SkijaDraw()
        val width = context.guiWidth()
        val height = context.guiHeight()

        val accent = ClickGuiFeature.themeAccent
        val guideColor = Color(accent.red, accent.green, accent.blue, 90).rgb
        graphics.roundedRect(width / 2, 0, 1, height, 0, guideColor)
        graphics.roundedRect(0, height / 2, width, 1, 0, guideColor)

        components.forEach { it.internalRender(context, true) }

        if (selected != null) {
            val bounds = selected!!.internalBounds()
            val (posX, posY) = selected!!.position(context)
            val offset = selected!!.offsetBounds(context)
            graphics.roundedRect(posX + offset.first, posY + offset.second, bounds.first.toInt(), bounds.second.toInt(), 2, accent.rgb, 1)
        }

        renderOptions(graphics, mouseX, mouseY)
        GuiUtils.renderRoundedRectangle(graphics, width / 2 - 55, 12, 110, 24, 6, accent.rgb)
        graphics.centeredText( if (returnScreen != null) "Done / back to GUI" else "Done", width / 2, 20, Color(20, 15, 28).rgb)
        val help = "Drag to move / Scroll to resize / Right-click for options"
        val helpWidth = SkijaDraw.textWidth(help).toInt() + 20
        GuiUtils.renderRoundedRectangle(graphics, (width - helpWidth) / 2, height - 30, helpWidth, 22, 6, ClickGuiFeature.themeBase.rgb)
        graphics.centeredText( help, width / 2, height - 23, Color.WHITE.rgb)

        SkijaRenderer.submit(this, width, height, graphics)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        components.forEach(::save)
        selected = null
        openedOptions = null
        isDragging = false
        val parent = returnScreen
        returnScreen = null
        if (parent != null) mc.setScreen(parent) else super.onClose()
    }

    private const val OPTIONS_WIDTH = 150
    private const val OPTIONS_PADDING = 8
    private const val OPTION_HEIGHT = 18

    private data class OptionsRect(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun optionsRect(): OptionsRect? {
        val opened = openedOptions ?: return null
        val count = opened.allowedStaticRenderConditions.size
        val panelHeight = OPTIONS_PADDING * 2 + count * OPTION_HEIGHT
        val (componentX, componentY) = opened.position(width, height)
        val (offsetX, offsetY) = opened.offsetBounds(width, height)
        val componentWidth = opened.internalBounds().first.toInt()

        var panelX = componentX + offsetX - OPTIONS_WIDTH - 4
        if (panelX < 4) panelX = componentX + offsetX + componentWidth + 4
        panelX = panelX.coerceIn(4, (width - OPTIONS_WIDTH - 4).coerceAtLeast(4))
        val panelY = (componentY + offsetY).coerceIn(4, (height - panelHeight - 4).coerceAtLeast(4))
        return OptionsRect(panelX, panelY, OPTIONS_WIDTH, panelHeight)
    }

    private fun optionAt(mouseX: Double, mouseY: Double): Condition? {
        val opened = openedOptions ?: return null
        val panel = optionsRect() ?: return null
        if (mouseX < panel.x || mouseX > panel.x + panel.width ||
            mouseY < panel.y || mouseY > panel.y + panel.height) return null
        val index = ((mouseY - panel.y - OPTIONS_PADDING) / OPTION_HEIGHT).toInt()
        return opened.allowedStaticRenderConditions.getOrNull(index)
    }

    private fun renderOptions(context: SkijaDraw, mouseX: Int, mouseY: Int) {
        val opened = openedOptions ?: return
        val panel = optionsRect() ?: return
        GuiUtils.renderRoundedRectangle(context, panel.x, panel.y, panel.width, panel.height, 3, ClickGuiFeature.themeBase.rgb)
        GuiUtils.renderRoundedOutline(context, panel.x, panel.y, panel.width, panel.height, 3, 1, ClickGuiFeature.themeAccent.rgb)

        opened.allowedStaticRenderConditions.forEachIndexed { index, condition ->
            val rowY = panel.y + OPTIONS_PADDING + index * OPTION_HEIGHT
            val hovered = mouseX in panel.x..(panel.x + panel.width) && mouseY in rowY..(rowY + OPTION_HEIGHT)
            if (hovered) GuiUtils.renderRectangle(context, panel.x + 3, rowY, panel.width - 6, OPTION_HEIGHT, Color(75, 25, 40, 170).rgb)
            val active = condition in opened.staticRenderConditions
            val boxX = panel.x + OPTIONS_PADDING
            val boxY = rowY + 4
            GuiUtils.renderRoundedRectangle(context, boxX, boxY, 10, 10, 2,
                if (active) ClickGuiFeature.themeAccent.rgb else Color(45, 20, 28, 255).rgb)
            GuiUtils.renderRoundedOutline(context, boxX, boxY, 10, 10, 2, 1, Color(235, 140, 166, 220).rgb)
            if (active) GuiUtils.renderRectangle(context, boxX + 3, boxY + 3, 4, 4, Color.WHITE.rgb)
            context.text(condition.displayName, boxX + 16, rowY + 4, Color(246, 227, 233, 255).rgb)
        }
    }
}
