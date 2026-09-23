package kitty.cat.pathfinding

import kitty.cat.pathfinding.EtherGeometry.Cell
import kotlin.math.max
import kotlin.math.min

/** Small initial scan, then progressively larger detours within the same local limits. */
object EtherSearchArea {
    data class Bounds(val low: Cell, val high: Cell)

    fun areas(start: Cell, goal: Cell, minY: Int, maxY: Int, automatic: Boolean,
              horizontal: Int, vertical: Int): List<Bounds> {
        if (goal.y !in minY until maxY) return emptyList()
        val margins = if (automatic) listOf(8 to 8, 16 to 12, 32 to 24, 48 to 36, 64 to 48)
            else listOf(horizontal to vertical)
        return margins.map { (pad, height) ->
            Bounds(
                Cell(min(start.x, goal.x) - pad, max(minY, min(start.y, goal.y) - height), min(start.z, goal.z) - pad),
                Cell(max(start.x, goal.x) + pad, min(maxY - 1, max(start.y, goal.y) + height + 4), max(start.z, goal.z) + pad)
            )
        }.takeWhile { (low, high) ->
            val dx = high.x.toLong() - low.x
            val dy = high.y.toLong() - low.y
            val dz = high.z.toLong() - low.z
            dx in 0..320 && dy in 0..160 && dz in 0..320 && (dx + 1) * (dy + 1) * (dz + 1) <= 4_000_000
        }.distinct()
    }
}
