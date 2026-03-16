package kitty.cat.render.nanovg

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NVGFont(val name: String, inputStream: InputStream) {
    private val cachedBytes: ByteArray = inputStream.use { it.readBytes() }

    fun buffer(): ByteBuffer {
        return ByteBuffer.allocateDirect(cachedBytes.size)
            .order(ByteOrder.nativeOrder())
            .put(cachedBytes)
            .flip() as ByteBuffer
    }
}
