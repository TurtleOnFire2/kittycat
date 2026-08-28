package kitty.cat.utils

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object NameChanger {

    val names = listOf(
        Name(
            checkName("0f888ec4cb044c2a95f78edc75ffcc55"),
            smoothGradient("autopy", 0x664a00, Color.YELLOW.rgb)
        ),
        Name(
            checkName("e51175776ad44a6fb1b4ecaf94bf4246"),
            smoothGradient("legitcatgirl", Color.PINK.rgb, Color.MAGENTA.rgb)
        )
        ,
        Name(
            checkName("1b524fe49c5c4fe599840bdd7c790bf0"),
            smoothGradient("LarpingJob", Color.GREEN.rgb, 0x056e00)
        )
    )

    /**
     * Creates a left-to-right gradient whose colors are interpolated inside
     * glyphs by the text renderer instead of changing once per character.
     * Three or more colors create evenly spaced color stops.
     */
    fun smoothGradient(text: String, vararg colors: Int): Component {
        require(colors.size >= 2) { "A gradient needs at least two colors" }

        val codePoints = text.codePoints().toArray()
        if (codePoints.isEmpty()) return Component.empty()

        val result = Component.empty()

        codePoints.forEachIndexed { index, codePoint ->
            val startProgress = index.toFloat() / codePoints.size
            val endProgress = (index + 1).toFloat() / codePoints.size
            val startColor = interpolateGradient(colors, startProgress)
            val endColor = interpolateGradient(colors, endProgress)
            val style = Style.EMPTY
                .withColor(startColor)
                .withInsertion(SmoothGradientRenderer.marker(startColor, endColor))

            result.append(
                Component.literal(String(Character.toChars(codePoint)))
                    .withStyle(style)
            )
        }

        return result
    }

    private fun interpolateGradient(colors: IntArray, progress: Float): Int {
        val scaled = progress.coerceIn(0f, 1f) * (colors.size - 1)
        val leftIndex = scaled.toInt().coerceAtMost(colors.lastIndex - 1)
        val amount = scaled - leftIndex
        val left = colors[leftIndex]
        val right = colors[leftIndex + 1]

        fun channel(shift: Int): Int {
            val start = left shr shift and 0xFF
            val end = right shr shift and 0xFF
            return (start + (end - start) * amount).toInt().coerceIn(0, 255)
        }

        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun checkName(uuid: String): String {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$uuid"))
            .GET()
            .build()

        val response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )

        if (response.statusCode() != 200) {
            println("Request failed: ${response.statusCode()}")
            return "REALLYLONGIMPOSSIBLESTRING"
        }

        val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")

        return nameRegex
            .find(response.body())
            ?.groupValues
            ?.get(1)
            ?: "REALLYLONGIMPOSSIBLESTRING"
    }

    fun replace(text: String?): String? {
        if (text == null) return null

        var result = text

        for ((name, replacement) in names) {
            result = result?.replace(name, replacement.string)
        }

        return result
    }

    fun replace(sequence: FormattedCharSequence): FormattedCharSequence {
        var result = sequence

        for ((name, replacement) in names) {
            result = replaceSingle(result, name, replacement)
        }

        return result
    }

    private fun replaceSingle(
        sequence: FormattedCharSequence,
        name: String,
        replacement: Component
    ): FormattedCharSequence {
        val styles = ArrayList<Style>()
        val text = StringBuilder()

        sequence.accept { _, style, codePoint ->
            text.appendCodePoint(codePoint)

            repeat(Character.charCount(codePoint)) {
                styles.add(style)
            }

            true
        }

        val original = text.toString()
        if (!original.contains(name)) return sequence

        val parts = ArrayList<FormattedCharSequence>()
        var position = 0

        while (position < original.length) {
            val match = original.indexOf(name, position)
            if (match < 0) {
                appendStyled(
                    parts,
                    original,
                    styles,
                    position,
                    original.length
                )
                break
            }

            appendStyled(
                parts,
                original,
                styles,
                position,
                match
            )

            parts.add(replacement.visualOrderText)

            position = match + name.length
        }

        return FormattedCharSequence.composite(parts)
    }

    private fun appendStyled(
        parts: MutableList<FormattedCharSequence>,
        text: String,
        styles: List<Style>,
        start: Int,
        end: Int
    ) {
        var index = start

        while (index < end) {
            val style = styles[index]
            val runStart = index

            index += Character.charCount(text.codePointAt(index))

            while (index < end && styles[index] == style) {
                index += Character.charCount(text.codePointAt(index))
            }

            parts.add(
                FormattedCharSequence.forward(
                    text.substring(runStart, index),
                    style
                )
            )
        }
    }
}

data class Name(
    val name: String,
    val replacement: Component
)
