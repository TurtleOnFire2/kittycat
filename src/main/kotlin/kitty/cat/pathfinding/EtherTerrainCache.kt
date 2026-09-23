package kitty.cat.pathfinding

/** Client-thread-only, bounded cache. Workers receive copies, never these mutable arrays. */
class EtherTerrainCache(private val capacity: Int = 2048) {
    private class Section(val cells: ByteArray = ByteArray(4096) { -1 }, var remaining: Int = 4096)
    private val sections = object : LinkedHashMap<EtherGeometry.Cell, Section>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<EtherGeometry.Cell, Section>) = size > capacity
    }

    init { require(capacity > 0) }

    /** -1 means never captured (or evicted); opaque is a distinct, known value of zero. */
    fun flags(x: Int, y: Int, z: Int): Int {
        val section = sections[EtherGeometry.Cell(x shr 4, y shr 4, z shr 4)] ?: return -1
        return section.cells[(x and 15) + 16 * ((y and 15) + 16 * (z and 15))].toInt()
    }

    fun isSectionComplete(section: EtherGeometry.Cell) = sections[section]?.remaining == 0

    /** Copies a row contained in one 16-block section; -1 is reserved for uncaptured cells. */
    fun copyRow(x: Int, y: Int, z: Int, count: Int, destination: ByteArray, offset: Int,
                capture: (Int, Int, Int) -> Int): Int {
        require(count in 1..(16 - (x and 15)))
        val key = EtherGeometry.Cell(x shr 4, y shr 4, z shr 4)
        val section = sections.getOrPut(key) { Section() }
        val first = (x and 15) + 16 * ((y and 15) + 16 * (z and 15))
        var reused = 0
        for (i in 0 until count) {
            if (section.cells[first + i] == (-1).toByte()) {
                section.cells[first + i] = capture(x + i, y, z).toByte()
                section.remaining--
            }
            else reused++
        }
        section.cells.copyInto(destination, offset, first, first + count)
        return reused
    }

    fun invalidateBlock(x: Int, y: Int, z: Int) {
        // Collision classification may depend on adjacent blocks, including across sections.
        for (sx in ((x - 1) shr 4)..((x + 1) shr 4))
            for (sy in ((y - 1) shr 4)..((y + 1) shr 4))
                for (sz in ((z - 1) shr 4)..((z + 1) shr 4))
                    sections.remove(EtherGeometry.Cell(sx, sy, sz))
    }

    fun invalidateChunk(x: Int, z: Int) {
        sections.keys.removeAll { kotlin.math.abs(it.x - x) <= 1 && kotlin.math.abs(it.z - z) <= 1 }
    }

    fun clear() = sections.clear()
}
