package kitty.cat.render.nanovg

import net.minecraft.client.Minecraft
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import java.net.JarURLConnection
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.round

object NVGRenderer {

    private val nvgColor = NVGColor.malloc()
    private val fontBounds = FloatArray(4)
    private val fontMap = HashMap<NVGFont, FontEntry>()
    private var drawing = false
    var vg = -1L
        private set

    private val availableFontsByMode: LinkedHashMap<String, NVGFont> by lazy { discoverFonts() }

    fun availableFontModes(): List<String> = availableFontsByMode.keys.toList()

    fun fontForMode(mode: String): NVGFont {
        return availableFontsByMode[mode] ?: defaultFont
    }

    val defaultFont: NVGFont
        get() = availableFontsByMode.values.first()

    fun ensureInit() {
        if (vg != -1L) return
        vg = nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)
        require(vg != -1L) { "Failed to initialize NanoVG" }
    }

    fun devicePixelRatio(): Float {
        return try {
            val window = Minecraft.getInstance().window
            val fbw = window.width
            val ww = window.screenWidth
            if (ww == 0) 1f else fbw.toFloat() / ww.toFloat()
        } catch (_: Throwable) { 1f }
    }

    fun beginFrame(width: Float, height: Float) {
        ensureInit()
        if (drawing) return
        val dpr = devicePixelRatio()
        nvgBeginFrame(vg, width / dpr, height / dpr, dpr)
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        drawing = true
    }

    fun endFrame() {
        if (!drawing) return
        nvgEndFrame(vg)
        drawing = false
    }

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: NVGFont = defaultFont) {
        prepareTextState(size, font)
        val snappedX = snapToPixel(x)
        val snappedY = snapToPixel(y)
        setColor(color)
        nvgFillColor(vg, nvgColor)
        nvgText(vg, snappedX, snappedY, text)
    }

    fun textCentered(text: String, cx: Float, y: Float, size: Float, color: Int, font: NVGFont = defaultFont) {
        val w = textWidth(text, size, font)
        text(text, cx - w / 2f, y, size, color, font)
    }

    fun textWidth(text: String, size: Float, font: NVGFont = defaultFont): Float {
        ensureInit()
        prepareTextState(size, font)
        return nvgTextBounds(vg, 0f, 0f, text, fontBounds)
    }

    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        if (width <= 0f || height <= 0f) return
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, width, height, radius.coerceAtLeast(0f))
        setColor(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun roundedRectStroke(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        strokeWidth: Float,
        color: Int
    ) {
        if (width <= 0f || height <= 0f || strokeWidth <= 0f) return
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, width, height, radius.coerceAtLeast(0f))
        nvgStrokeWidth(vg, strokeWidth)
        setColor(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    private fun setColor(argb: Int) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        nvgRGBAf(r, g, b, a, nvgColor)
    }

    private fun prepareTextState(size: Float, font: NVGFont): Float {
        val snappedSize = (round(size * 10f) / 10f).coerceAtLeast(1f)
        nvgFontSize(vg, snappedSize)
        nvgFontFaceId(vg, getFontID(font))
        nvgFontBlur(vg, 0f)
        nvgTextLetterSpacing(vg, 0f)
        return snappedSize
    }

    private fun snapToPixel(value: Float): Float {
        return round(value)
    }

    private fun getFontID(font: NVGFont): Int {
        return fontMap.getOrPut(font) {
            val buffer = font.buffer()
            FontEntry(nvgCreateFontMem(vg, font.name, buffer, false), buffer)
        }.id
    }

    private fun discoverFonts(): LinkedHashMap<String, NVGFont> {
        val resourcePaths = linkedSetOf<String>()
        collectFontResourcesInFolder("assets/kittycat/font", resourcePaths)
        collectFontResourcesInFolder("font", resourcePaths)

        if (resourcePaths.isEmpty()) {
            listOf(
                "assets/kittycat/font/onest_regular.ttf",
                "font/WinkySans-Regular.ttf"
            ).forEach { path ->
                if (findFontResource(path) != null) {
                    resourcePaths += path
                }
            }
        }

        val discovered = LinkedHashMap<String, NVGFont>()
        resourcePaths.sorted().forEach { resourcePath ->
            val stream = findFontResource(resourcePath) ?: return@forEach
            val mode = toModeName(resourcePath)
            if (mode in discovered) return@forEach
            val internalName = mode.replace(" ", "")
            discovered[mode] = NVGFont(internalName, stream)
        }

        if (discovered.isNotEmpty()) return discovered

        val fallback = requireFontResource(
            "assets/kittycat/font/onest_regular.ttf",
            "font/WinkySans-Regular.ttf"
        )
        discovered["Default"] = NVGFont("Default", fallback)
        return discovered
    }

    private fun collectFontResourcesInFolder(folderPath: String, out: MutableSet<String>) {
        val normalized = folderPath.trim('/')
        val resources = NVGRenderer::class.java.classLoader.getResources(normalized)
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            when (url.protocol.lowercase()) {
                "file" -> collectFontFilesFromDirectoryUrl(url, normalized, out)
                "jar" -> collectFontFilesFromJarUrl(url, normalized, out)
            }
        }
    }

    private fun collectFontFilesFromDirectoryUrl(
        url: java.net.URL,
        normalizedFolder: String,
        out: MutableSet<String>
    ) {
        val directoryPath = runCatching { Paths.get(url.toURI()) }.getOrNull() ?: return
        if (!Files.isDirectory(directoryPath)) return

        val files = runCatching { Files.list(directoryPath) }.getOrNull() ?: return
        try {
            val iterator = files.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path)) continue
                val fileName = path.fileName?.toString() ?: continue
                if (!isFontFileName(fileName)) continue
                out += "$normalizedFolder/$fileName"
            }
        } finally {
            files.close()
        }
    }

    private fun collectFontFilesFromJarUrl(
        url: java.net.URL,
        normalizedFolder: String,
        out: MutableSet<String>
    ) {
        val connection = runCatching { url.openConnection() as? JarURLConnection }.getOrNull() ?: return
        val jarFile = connection.jarFile
        val prefix = (connection.entryName?.trim('/') ?: normalizedFolder) + "/"

        val entries = jarFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
            val localName = entry.name.removePrefix(prefix)
            if ('/' in localName || !isFontFileName(localName)) continue
            out += entry.name
        }
    }

    private fun toModeName(resourcePath: String): String {
        val fileName = resourcePath.substringAfterLast('/').substringBeforeLast('.')
        val normalized = fileName.replace('_', ' ').replace('-', ' ')
        val words = normalized.split(' ').filter { it.isNotBlank() }
        return words.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }.ifBlank { "Font" }
    }

    private fun isFontFileName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }

    private fun requireFontResource(vararg paths: String) =
        findFontResource(*paths) ?: error("Could not load font: ${paths.joinToString(" or ")}")

    private fun findFontResource(vararg paths: String) =
        paths.asSequence().mapNotNull { path ->
            val normalized = path.trimStart('/')
            NVGRenderer::class.java.classLoader.getResourceAsStream(normalized)
                ?: NVGRenderer::class.java.getResourceAsStream("/$normalized")
        }.firstOrNull()

    private data class FontEntry(val id: Int, val buffer: ByteBuffer)
}
