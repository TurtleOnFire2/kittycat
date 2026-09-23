package kitty.cat.pathfinding

import java.util.PriorityQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ExecutionException
import kotlin.math.*

/** Standalone geometry/search code: no Minecraft world access and no teleport execution. */
object EtherGeometry {
    data class Point(val x: Double, val y: Double, val z: Double) {
        fun distance(other: Point) = sqrt((x - other.x).pow(2) + (y - other.y).pow(2) + (z - other.z).pow(2))
    }

    data class Cell(val x: Int, val y: Int, val z: Int) {
        fun center() = Point(x + 0.5, y + 0.5, z + 0.5)
        fun eye(height: Double) = Point(x + 0.5, y + 1.05 + height, z + 0.5)
    }

    data class Aim(val yaw: Float, val pitch: Float)
    interface Terrain {
        /** Unknown cells must return false. Passability is distinct from landing clearance. */
        fun transparent(cell: Cell): Boolean
        fun transparent(x: Int, y: Int, z: Int): Boolean = transparent(Cell(x, y, z))
        fun landing(cell: Cell): Boolean
    }

    fun rotation(from: Point, toward: Point): Aim {
        val dx = toward.x - from.x
        val dy = toward.y - from.y
        val dz = toward.z - from.z
        return Aim(Math.toDegrees(atan2(-dx, dz)).toFloat(), -Math.toDegrees(atan2(dy, hypot(dx, dz))).toFloat())
    }

    /** Grid traversal with an explicit distance cutoff, including the origin cell. */
    fun hit(terrain: Terrain, origin: Point, aim: Aim, range: Double): Cell? {
        val yaw = Math.toRadians(aim.yaw.toDouble())
        val pitch = Math.toRadians(aim.pitch.toDouble())
        val dx = -sin(yaw) * cos(pitch)
        val dy = -sin(pitch)
        val dz = cos(yaw) * cos(pitch)
        var x = floor(origin.x).toInt()
        var y = floor(origin.y).toInt()
        var z = floor(origin.z).toInt()
        fun step(d: Double) = if (d > 1e-12) 1 else if (d < -1e-12) -1 else 0
        val sx = step(dx); val sy = step(dy); val sz = step(dz)
        fun delta(d: Double, s: Int) = if (s == 0) Double.POSITIVE_INFINITY else abs(1.0 / d)
        val tx = delta(dx, sx); val ty = delta(dy, sy); val tz = delta(dz, sz)
        fun first(c: Int, p: Double, d: Double, s: Int) =
            if (s == 0) Double.POSITIVE_INFINITY else (c + (if (s > 0) 1.0 else 0.0) - p) / d
        var nx = first(x, origin.x, dx, sx)
        var ny = first(y, origin.y, dy, sy)
        var nz = first(z, origin.z, dz, sz)
        // A ray of this length cannot cross more than 3 * range + 6 cell boundaries.
        repeat(ceil(range * 3).toInt() + 6) {
            if (!terrain.transparent(x, y, z)) return Cell(x, y, z).takeIf(terrain::landing)
            if (min(nx, min(ny, nz)) > range) return null
            when {
                nx <= ny && nx <= nz -> { x += sx; nx += tx }
                ny <= nz -> { y += sy; ny += ty }
                else -> { z += sz; nz += tz }
            }
        }
        return null
    }

    /** Try the center, then inset points on the surface facing the eye. Every float aim is traced again. */
    fun connection(terrain: Terrain, origin: Point, target: Cell, range: Double, detailed: Boolean = true): Aim? {
        val dx = target.x + 0.5 - origin.x
        val dy = target.y + 0.5 - origin.y
        val dz = target.z + 0.5 - origin.z
        val radius = range + sqrt(0.75)
        if (dx * dx + dy * dy + dz * dz > radius * radius || !terrain.landing(target)) return null
        fun test(point: Point): Aim? = rotation(origin, point).takeIf { hit(terrain, origin, it, range) == target }
        test(target.center())?.let { return it }
        for (axis in 0..2) {
            val relative = when (axis) {
                0 -> origin.x - target.x
                1 -> origin.y - target.y
                else -> origin.z - target.z
            }
            val side = when {
                relative < 0 -> 0.02
                relative > 1 -> 0.98
                else -> continue
            }
            val samples = if (detailed) FACE_SAMPLES else CENTER_SAMPLE
            for (u in samples) for (v in samples) {
                val point = when (axis) {
                    0 -> Point(target.x + side, target.y + u, target.z + v)
                    1 -> Point(target.x + v, target.y + side, target.z + u)
                    else -> Point(target.x + u, target.y + v, target.z + side)
                }
                test(point)?.let { return it }
            }
        }
        return null
    }

    private val FACE_SAMPLES = doubleArrayOf(0.2, 0.5, 0.8)
    private val CENTER_SAMPLE = doubleArrayOf(0.5)
}

/**
 * A resumable A* search over surface cells. Edges are discovered by aiming at candidate
 * landings, rather than casting a fixed distribution of rays into the world. Unit cost
 * minimizes hops in the sampled visibility graph. Geometry is evaluated only as needed.
 */
class EtherRouteSearch(
    private val terrain: EtherGeometry.Terrain,
    private val candidates: List<EtherGeometry.Cell>,
    private val start: EtherGeometry.Point,
    private val goal: EtherGeometry.Cell,
    private val range: Double,
    private val eyeHeight: Double,
    private val expansionLimit: Int = 4096,
    private val detailed: Boolean = true
) {
    data class Hop(val block: EtherGeometry.Cell, val aim: EtherGeometry.Aim)
    enum class Status { SEARCHING, FOUND, NO_ROUTE, LIMIT }
    private data class Entry(val index: Int, val cost: Int, val estimate: Int, val order: Long)
    private val goalDistances = DoubleArray(candidates.size) { candidates[it].center().distance(goal.center()) }
    private val frontier = PriorityQueue(compareBy<Entry> { it.estimate }.thenByDescending { it.cost }
        .thenBy { if (it.index < 0) 0.0 else goalDistances[it.index] }.thenBy { it.order })
    private val buckets = candidates.indices.groupBy { bucket(candidates[it]) }
    private val costs = IntArray(candidates.size) { Int.MAX_VALUE }
    private val parents = IntArray(candidates.size) { -1 }
    private val aims = arrayOfNulls<EtherGeometry.Aim>(candidates.size)
    private val goalIndex = candidates.indexOf(goal)
    private var order = 0L
    private var active: Entry? = null
    private var neighbour = -1
    private var nearby = emptyList<Int>()
    private var nearbyReady = false
    private var activeEye = start
    var expanded = 0
        private set
    var status = Status.SEARCHING
        private set
    var route: List<Hop> = emptyList()
        private set

    init {
        require(range in 1.0..128.0 && eyeHeight in 0.0..2.0)
        if (goalIndex < 0) status = Status.NO_ROUTE
        else frontier.add(Entry(-1, 0, 0, order++))
    }

    /** Each call does at most [work] candidate checks; the caller owns scheduling/cancellation. */
    fun advance(work: Int = 16) {
        repeat(work) {
            if (status != Status.SEARCHING) return
            if (active == null) {
                val next = frontier.poll() ?: run { status = Status.NO_ROUTE; return }
                if (next.index >= 0 && next.cost != costs[next.index]) return@repeat
                if (next.index == goalIndex) { finish(); return }
                if (++expanded > expansionLimit) { status = Status.LIMIT; return }
                active = next
                activeEye = if (next.index < 0) start else candidates[next.index].eye(eyeHeight)
                nearbyReady = false
                neighbour = -1 // Test the goal first, then the remaining candidate cells.
            }
            val current = active!!
            if (neighbour >= 0) {
                prepareNeighbours()
                if (neighbour >= nearby.size) { active = null; return@repeat }
            }
            val targetIndex = if (neighbour == -1) goalIndex else nearby[neighbour]
            neighbour++
            if (targetIndex == current.index || (targetIndex == goalIndex && neighbour != 0)) return@repeat
            val newCost = current.cost + 1
            if (costs[targetIndex] <= newCost) return@repeat
            val target = candidates[targetIndex]
            val aim = EtherGeometry.connection(terrain, activeEye, target, range, detailed) ?: return@repeat
            accept(current, targetIndex, aim)
        }
    }

    private fun accept(current: Entry, targetIndex: Int, aim: EtherGeometry.Aim) {
        val newCost = current.cost + 1
        costs[targetIndex] = newCost
        parents[targetIndex] = current.index
        aims[targetIndex] = aim
        // Remaining edges from this node cost at least newCost, so a goal edge is
        // optimal if no queued node has a lower bound below it.
        if (targetIndex == goalIndex && (frontier.peek()?.estimate ?: Int.MAX_VALUE) >= newCost) { finish(); return }
        // A landing's center can move at most range + 4 from the previous center:
        // eye offset <= 2.55 plus the target's half diagonal < 0.87.
        val lowerBound = ceil(goalDistances[targetIndex] / (range + 4.0)).toInt()
        frontier.add(Entry(targetIndex, newCost, newCost + lowerBound, order++))
    }

    /** Coordinator owns A* state; workers only trace against immutable terrain. Results merge in order. */
    fun runParallel(executor: ExecutorService?, threads: Int, cancelled: () -> Boolean = { false }) {
        require(threads >= 1)
        while (status == Status.SEARCHING) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
            if (active == null || neighbour == -1) { advance(1); continue }
            val current = active!!
            prepareNeighbours()
            if (neighbour >= nearby.size) { active = null; continue }
            val eye = activeEye
            val end = min(nearby.size, neighbour + 256 * threads)
            val indices = nearby.subList(neighbour, end).filter { it != goalIndex && it != current.index && costs[it] > current.cost + 1 }
            neighbour = end
            fun trace(part: List<Int>): List<Pair<Int, EtherGeometry.Aim>> = part.mapNotNull { index ->
                if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
                EtherGeometry.connection(terrain, eye, candidates[index], range, detailed)?.let { index to it }
            }
            val results = if (executor == null || threads == 1 || indices.size < 128) trace(indices)
            else {
                val tasks = indices.chunked(ceil(indices.size.toDouble() / threads).toInt()).map { part -> Callable { trace(part) } }
                try {
                    executor.invokeAll(tasks).flatMap { it.get() }
                } catch (exception: ExecutionException) {
                    if (exception.cause is CancellationException) throw CancellationException()
                    throw exception
                }
            }
            for ((index, aim) in results) accept(current, index, aim)
        }
    }

    private fun bucket(cell: EtherGeometry.Cell) = EtherGeometry.Cell(cell.x shr 4, cell.y shr 4, cell.z shr 4)

    private fun prepareNeighbours() {
        if (!nearbyReady) {
            nearby = neighbours(activeEye, active!!.cost + 1)
            nearbyReady = true
        }
    }

    private fun neighbours(eye: EtherGeometry.Point, newCost: Int): List<Int> {
        val radius = range + sqrt(0.75)
        val squared = radius * radius
        val result = ArrayList<Int>()
        for (x in (floor(eye.x - radius).toInt() shr 4)..(floor(eye.x + radius).toInt() shr 4))
            for (y in (floor(eye.y - radius).toInt() shr 4)..(floor(eye.y + radius).toInt() shr 4))
                for (z in (floor(eye.z - radius).toInt() shr 4)..(floor(eye.z + radius).toInt() shr 4)) {
                    for (index in buckets[EtherGeometry.Cell(x, y, z)] ?: continue) {
                        if (costs[index] <= newCost) continue
                        val cell = candidates[index]
                        val dx = cell.x + 0.5 - eye.x; val dy = cell.y + 0.5 - eye.y; val dz = cell.z + 0.5 - eye.z
                        if (dx * dx + dy * dy + dz * dz <= squared) result.add(index)
                    }
                }
        return result
    }

    private fun finish() {
        val reversed = ArrayList<Hop>()
        var index = goalIndex
        while (index >= 0) {
            reversed.add(Hop(candidates[index], aims[index]!!))
            index = parents[index]
        }
        route = reversed.asReversed()
        status = Status.FOUND
    }
}
