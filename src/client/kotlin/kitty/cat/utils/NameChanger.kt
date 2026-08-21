package kitty.cat.utils

import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


object NameChanger {
    var frosty: String? = null

    fun checkFrostyName() {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/0f888ec4cb044c2a95f78edc75ffcc55"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            println("Request failed: ${response.statusCode()}")
            return
        }

        val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
        frosty = nameRegex.find(response.body())?.groupValues?.get(1)
    }

    fun replace(text: String?): String? {
        if (text == null) return null
        val name = frosty ?: return text

        return text.replace(name, "autopy")
    }

    fun replace(sequence: FormattedCharSequence): FormattedCharSequence {
        val name = frosty ?: return sequence

        val styles = ArrayList<Style>()
        val text = StringBuilder()

        sequence.accept { _, style, codePoint ->
            text.appendCodePoint(codePoint)

            for (i in 0..<Character.charCount(codePoint)) {
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
                appendStyled(parts, original, styles, position, original.length)
                break
            }

            appendStyled(parts, original, styles, position, match)
            parts.add(FormattedCharSequence.forward("autopy", styles[match]))
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

            parts.add(FormattedCharSequence.forward(text.substring(runStart, index), style))
        }
    }
}
