package kitty.cat.pathfinding

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import kotlin.math.floor

/** Try a sparse visibility graph before paying for every block on every surface. */
object EtherRoutePlanner {
    fun search(
        terrain: EtherGeometry.Terrain,
        candidates: List<EtherGeometry.Cell>,
        start: EtherGeometry.Point,
        goal: EtherGeometry.Cell,
        range: Double,
        eyeHeight: Double,
        expansionLimit: Int,
        detailed: Boolean,
        fast: Boolean,
        executor: ExecutorService?,
        threads: Int,
        cancelled: () -> Boolean = { false }
    ): EtherRouteSearch {
        fun checkCancelled() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
        }
        fun run(nodes: List<EtherGeometry.Cell>, limit: Int, detail: Boolean): EtherRouteSearch {
            checkCancelled()
            return EtherRouteSearch(terrain, nodes, start, goal, range, eyeHeight, limit, detail).also {
                it.runParallel(executor, threads, cancelled)
            }
        }
        if (fast && candidates.size > 256) {
            // Scale spacing with range so short-range searches do not lose all useful links.
            val spacing = (range / 7).toInt().coerceIn(2, 8)
            val representatives = LinkedHashMap<EtherGeometry.Cell, EtherGeometry.Cell>()
            fun distance(cell: EtherGeometry.Cell): Double {
                val dx = cell.x.toDouble() - goal.x
                val dy = cell.y.toDouble() - goal.y
                val dz = cell.z.toDouble() - goal.z
                return dx * dx + dy * dy + dz * dz
            }
            for ((index, cell) in candidates.withIndex()) {
                if (index and 1023 == 0) checkCancelled()
                val key = EtherGeometry.Cell(Math.floorDiv(cell.x, spacing), Math.floorDiv(cell.y, spacing), Math.floorDiv(cell.z, spacing))
                val previous = representatives[key]
                if (previous == null || distance(cell) < distance(previous)) representatives[key] = cell
            }
            // Preserve the exact goal and nearby takeoff surfaces regardless of representative selection.
            val sparse = LinkedHashSet(representatives.values)
            val sx = floor(start.x).toInt(); val sz = floor(start.z).toInt()
            candidates.filterTo(sparse) { kotlin.math.abs(it.x - sx) <= 1 && kotlin.math.abs(it.z - sz) <= 1 }
            if (goal in candidates) sparse.add(goal)
            if (sparse.size < candidates.size) {
                val quick = run(sparse.toList(), minOf(expansionLimit, 128), false)
                if (quick.status == EtherRouteSearch.Status.FOUND) return quick
            }
        }
        // A sparse failure says nothing about reachability. Restore all landings and requested aiming.
        return run(candidates, expansionLimit, detailed)
    }
}
