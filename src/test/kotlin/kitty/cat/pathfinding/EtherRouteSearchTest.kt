package kitty.cat.pathfinding

import kitty.cat.pathfinding.EtherGeometry.Aim
import kitty.cat.pathfinding.EtherGeometry.Cell
import kitty.cat.pathfinding.EtherGeometry.Point
import kotlin.test.*
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class EtherRouteSearchTest {
    @Test fun `parallel workers preserve the serial route`() {
        val cells = (0..90).flatMap { x -> (-8..8).map { z -> Cell(x, 0, z) } }
        val original = Terrain(cells.toSet())
        val serial = EtherRouteSearch(original, cells, Point(0.5, 2.32, 0.5), Cell(90, 0, 0), 32.0, 1.27)
        while (serial.status == EtherRouteSearch.Status.SEARCHING) serial.advance(1024)
        for (count in listOf(1, 2, 4)) {
            val workerNames = ConcurrentHashMap.newKeySet<String>()
            val terrain = object : EtherGeometry.Terrain {
                override fun transparent(cell: Cell): Boolean {
                    workerNames.add(Thread.currentThread().name)
                    return original.transparent(cell)
                }
                override fun landing(cell: Cell) = original.landing(cell)
            }
            val pool = Executors.newFixedThreadPool(count) { task -> Thread(task, "test-ether-worker") }
            try {
                val search = EtherRouteSearch(terrain, cells, Point(0.5, 2.32, 0.5), Cell(90, 0, 0), 32.0, 1.27)
                search.runParallel(pool, count)
                assertEquals(serial.status, search.status)
                assertEquals(serial.route, search.route)
                if (count > 1) assertTrue("test-ether-worker" in workerNames)
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun `cancellation stops parallel search without publishing a route`() {
        val cells = (0..90).flatMap { x -> (-8..8).map { z -> Cell(x, 0, z) } }
        val checks = AtomicInteger()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val search = EtherRouteSearch(Terrain(cells.toSet()), cells, Point(0.5, 2.32, 0.5), Cell(90, 0, 0), 32.0, 1.27)
            assertFailsWith<CancellationException> { search.runParallel(pool, 4) { checks.incrementAndGet() > 30 } }
            assertTrue(search.route.isEmpty())
        } finally { pool.shutdownNow() }
    }

    @Test fun `dense surface benchmark`() {
        val cells = (0..90).flatMap { x -> (-8..8).map { z -> Cell(x, 0, z) } }
        val terrain = Terrain(cells.toSet())
        val started = System.nanoTime()
        val search = EtherRouteSearch(terrain, cells, Point(0.5, 2.32, 0.5), Cell(90, 0, 0), 32.0, 1.27)
        while (search.status == EtherRouteSearch.Status.SEARCHING) search.advance(1024)
        assertEquals(EtherRouteSearch.Status.FOUND, search.status)
        println("Dense surface: ${(System.nanoTime() - started) / 1_000_000} ms; ${search.expanded} expansions; ${search.route.size} hops")
    }
    private class Terrain(val landings: Set<Cell>, val obstacles: Set<Cell> = emptySet()) : EtherGeometry.Terrain {
        override fun transparent(cell: Cell) = cell !in landings && cell !in obstacles
        override fun landing(cell: Cell) = cell in landings
    }

    private fun solve(search: EtherRouteSearch): EtherRouteSearch {
        repeat(100_000) {
            if (search.status != EtherRouteSearch.Status.SEARCHING) return search
            search.advance(1)
        }
        error("Search did not terminate")
    }

    @Test fun `axis parallel ray stops at the first obstruction`() {
        val goal = Cell(0, 1, 8)
        assertEquals(goal, EtherGeometry.hit(Terrain(setOf(goal)), Point(0.5, 1.5, 0.5), Aim(0f, 0f), 8.0))
        assertNull(EtherGeometry.hit(Terrain(setOf(goal), setOf(Cell(0, 1, 4))), Point(0.5, 1.5, 0.5), Aim(0f, 0f), 8.0))
    }

    @Test fun `range is measured to the cell entry and not its center`() {
        val goal = Cell(0, 1, 8)
        val terrain = Terrain(setOf(goal))
        assertNull(EtherGeometry.hit(terrain, Point(0.5, 1.5, 0.5), Aim(0f, 0f), 7.49))
        assertEquals(goal, EtherGeometry.hit(terrain, Point(0.5, 1.5, 0.5), Aim(0f, 0f), 7.51))
    }

    @Test fun `negative coordinates and downward rays use floor`() {
        val goal = Cell(-2, -4, -3)
        val terrain = Terrain(setOf(goal))
        assertEquals(goal, EtherGeometry.hit(terrain, Point(-1.5, 1.5, -2.5), Aim(0f, 90f), 5.0))
    }

    @Test fun `invalid origin obstruction cannot be skipped`() {
        val goal = Cell(0, 1, 8)
        assertNull(EtherGeometry.hit(Terrain(setOf(goal), setOf(Cell(0, 1, 0))), Point(0.5, 1.5, 0.5), Aim(0f, 0f), 20.0))
    }

    @Test fun `direct route needs one warp and records a valid aim`() {
        val goal = Cell(4, 0, 0)
        val terrain = Terrain(setOf(goal))
        val start = Point(0.5, 2.32, 0.5)
        val result = solve(EtherRouteSearch(terrain, listOf(goal), start, goal, 8.0, 1.27))
        assertEquals(EtherRouteSearch.Status.FOUND, result.status)
        assertEquals(1, result.route.size)
        assertEquals(goal, EtherGeometry.hit(terrain, start, result.route.single().aim, 8.0))
    }

    @Test fun `multi hop route uses each previous landing eye`() {
        val cells = listOf(Cell(4, 0, 0), Cell(8, 0, 0), Cell(12, 0, 0))
        val terrain = Terrain(cells.toSet())
        var eye = Point(0.5, 2.32, 0.5)
        val result = solve(EtherRouteSearch(terrain, cells.reversed(), eye, cells.last(), 5.1, 1.27))
        assertEquals(EtherRouteSearch.Status.FOUND, result.status)
        assertEquals(cells, result.route.map { it.block })
        for (hop in result.route) {
            assertEquals(hop.block, EtherGeometry.hit(terrain, eye, hop.aim, 5.1))
            eye = hop.block.eye(1.27)
        }
    }

    @Test fun `disconnected surfaces report no route`() {
        val goal = Cell(30, 0, 0)
        val result = solve(EtherRouteSearch(Terrain(setOf(goal)), listOf(goal), Point(0.5, 2.32, 0.5), goal, 5.0, 1.27))
        assertEquals(EtherRouteSearch.Status.NO_ROUTE, result.status)
        assertTrue(result.route.isEmpty())
    }

    @Test fun `budget exhaustion is not reported as no route`() {
        val cells = listOf(Cell(4, 0, 0), Cell(8, 0, 0))
        val result = solve(EtherRouteSearch(Terrain(cells.toSet()), cells, Point(0.5, 2.32, 0.5), cells.last(), 5.1, 1.27, expansionLimit = 1))
        assertEquals(EtherRouteSearch.Status.LIMIT, result.status)
    }

    @Test fun `unknown terrain is opaque`() {
        val goal = Cell(8, 0, 0)
        val terrain = object : EtherGeometry.Terrain {
            override fun transparent(cell: Cell) = cell.x < 4 && cell != goal
            override fun landing(cell: Cell) = cell == goal
        }
        assertNull(EtherGeometry.connection(terrain, Point(0.5, 2.32, 0.5), goal, 60.0))
    }

    @Test fun `shortest hop count agrees with exhaustive breadth first search`() {
        val random = kotlin.random.Random(9284)
        repeat(12) {
            val cells = (0 until 18).map { Cell(random.nextInt(1, 24), random.nextInt(-2, 3), random.nextInt(-8, 9)) }.distinct()
            val terrain = Terrain(cells.toSet())
            val start = Point(0.5, 2.32, 0.5)
            val goal = cells.last()
            val queue = ArrayDeque<Pair<Point, Int>>()
            queue.add(start to 0)
            val visited = HashSet<Cell>()
            var shortest: Int? = null
            while (queue.isNotEmpty() && shortest == null) {
                val (eye, hops) = queue.removeFirst()
                for (cell in cells) {
                    if (cell in visited || EtherGeometry.connection(terrain, eye, cell, 9.0) == null) continue
                    visited.add(cell)
                    if (cell == goal) { shortest = hops + 1; break }
                    queue.add(cell.eye(1.27) to hops + 1)
                }
            }
            val result = solve(EtherRouteSearch(terrain, cells, start, goal, 9.0, 1.27))
            if (shortest == null) assertEquals(EtherRouteSearch.Status.NO_ROUTE, result.status)
            else {
                assertEquals(EtherRouteSearch.Status.FOUND, result.status)
                assertEquals(shortest, result.route.size)
            }
        }
    }
}
