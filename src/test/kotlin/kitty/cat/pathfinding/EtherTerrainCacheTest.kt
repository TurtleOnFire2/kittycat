package kitty.cat.pathfinding

import kotlin.test.*

class EtherTerrainCacheTest {
    @Test fun `background scanner can skip fully captured sections until invalidated`() {
        val cache = EtherTerrainCache()
        val section = EtherGeometry.Cell(-1, 0, 0)
        val buffer = ByteArray(16)
        assertFalse(cache.isSectionComplete(section))
        for (y in 0..15) for (z in 0..15) {
            cache.copyRow(-16, y, z, 16, buffer, 0) { _, _, _ -> 3 }
            // Re-reading a row must not count it twice toward completion.
            cache.copyRow(-16, y, z, 16, buffer, 0) { _, _, _ -> error("Already captured") }
            assertEquals(y == 15 && z == 15, cache.isSectionComplete(section))
        }
        cache.invalidateBlock(-8, 8, 8)
        assertFalse(cache.isSectionComplete(section))
    }

    @Test fun `retained terrain distinguishes uncaptured cells from known opaque blocks`() {
        val cache = EtherTerrainCache()
        val row = ByteArray(2)
        assertEquals(-1, cache.flags(-16, -1, -16))
        cache.copyRow(-16, -1, -16, 2, row, 0) { x, _, _ -> if (x == -16) 0 else 3 }
        assertEquals(0, cache.flags(-16, -1, -16))
        assertEquals(3, cache.flags(-15, -1, -16))
        assertEquals(-1, cache.flags(-14, -1, -16))
        cache.clear()
        assertEquals(-1, cache.flags(-15, -1, -16))
    }

    @Test fun `overlapping rows reuse terrain and snapshot copies remain immutable`() {
        val cache = EtherTerrainCache()
        val first = ByteArray(16)
        var captures = 0
        val capture: (Int, Int, Int) -> Int = { _, _, _ -> captures++; 3 }
        assertEquals(0, cache.copyRow(-16, -1, -16, 16, first, 0, capture))
        val second = ByteArray(8)
        assertEquals(8, cache.copyRow(-8, -1, -16, 8, second, 0, capture))
        assertEquals(16, captures)
        cache.invalidateBlock(-1, -1, -16)
        cache.copyRow(-8, -1, -16, 8, second, 0) { _, _, _ -> 4 }
        assertTrue(first.all { it == 3.toByte() })
        assertTrue(second.all { it == 4.toByte() })
    }

    @Test fun `block changes invalidate neighboring section boundaries`() {
        val cache = EtherTerrainCache()
        val buffer = ByteArray(1)
        cache.copyRow(15, 15, 15, 1, buffer, 0) { _, _, _ -> 3 }
        cache.invalidateBlock(16, 16, 16)
        assertEquals(0, cache.copyRow(15, 15, 15, 1, buffer, 0) { _, _, _ -> 4 })
        assertEquals(4, buffer[0].toInt())
    }

    @Test fun `chunk invalidation and clearing force fresh captures`() {
        val cache = EtherTerrainCache()
        val buffer = ByteArray(1)
        fun read(x: Int) = cache.copyRow(x, 0, 0, 1, buffer, 0) { _, _, _ -> 0 }
        read(0); read(64)
        cache.invalidateChunk(0, 0)
        assertEquals(0, read(0))
        assertEquals(1, read(64))
        cache.clear()
        assertEquals(0, read(64))
    }

    @Test fun `least recently used sections are evicted at capacity`() {
        val cache = EtherTerrainCache(2)
        val buffer = ByteArray(1)
        fun read(x: Int) = cache.copyRow(x, 0, 0, 1, buffer, 0) { _, _, _ -> 0 }
        read(0); read(16)
        assertEquals(1, read(0))
        read(32)
        assertEquals(1, read(0))
        assertEquals(0, read(16))
    }
}
