package kitty.cat.pathfinding

import kitty.cat.pathfinding.EtherGeometry.Cell
import kitty.cat.pathfinding.EtherGeometry.Point
import kotlin.test.*
import java.util.concurrent.CancellationException

class EtherRoutePlannerTest {
    @Test fun `obstructed floor uses fewer terrain checks and every hop is valid`() {
        val cells = (0..70).flatMap { x -> (-16..16).map { z -> Cell(x, 0, z) } }
        val start = Point(0.5, 2.32, 0.5)
        val goal = Cell(70, 0, 0)
        var checks = 0L
        val terrain = object : EtherGeometry.Terrain {
            override fun transparent(cell: Cell) = transparent(cell.x, cell.y, cell.z)
            override fun transparent(x: Int, y: Int, z: Int): Boolean {
                checks++
                return y != 0 && !(x == 35 && y in 1..8 && z <= 9)
            }
            override fun landing(cell: Cell) = cell.y == 0 && cell.x in 0..70 && cell.z in -16..16
        }
        fun run(fast: Boolean) = EtherRoutePlanner.search(terrain, cells, start, goal, 40.0, 1.27,
            4096, true, fast, null, 1)
        val before = System.nanoTime()
        val exact = run(false)
        val exactTime = System.nanoTime() - before
        val exactChecks = checks
        checks = 0
        val after = System.nanoTime()
        val fast = run(true)
        val fastTime = System.nanoTime() - after
        val fastChecks = checks
        assertEquals(EtherRouteSearch.Status.FOUND, exact.status)
        assertEquals(EtherRouteSearch.Status.FOUND, fast.status)
        var eye = start
        for (hop in fast.route) {
            assertEquals(hop.block, EtherGeometry.hit(terrain, eye, hop.aim, 40.0))
            eye = hop.block.eye(1.27)
        }
        assertEquals(goal, fast.route.last().block)
        assertTrue(fastChecks < exactChecks / 2, "$fastChecks fast checks vs $exactChecks exhaustive checks")
        println("Obstructed floor: exhaustive ${exactTime / 1_000_000} ms / $exactChecks checks / ${exact.route.size} hops; fast ${fastTime / 1_000_000} ms / $fastChecks checks / ${fast.route.size} hops")
    }

    @Test fun `sparse failure falls back to omitted stepping stone`() {
        val start = Point(0.5, 2.32, 0.5)
        val bridge = Cell(4, 0, 0)
        val goal = Cell(8, 0, 0)
        val decoy = Cell(5, 0, 0)
        val cells = listOf(bridge, decoy, goal) + (100..400).map { Cell(it, 0, 0) }
        val terrain = object : EtherGeometry.Terrain {
            override fun transparent(cell: Cell) = cell != bridge && cell != goal
            override fun landing(cell: Cell) = cell == bridge || cell == goal
        }
        // The closer decoy replaces the only usable bridge in its sparse bucket.
        val result = EtherRoutePlanner.search(terrain, cells, start, goal, 4.1, 1.27,
            4096, true, true, null, 1)
        assertEquals(EtherRouteSearch.Status.FOUND, result.status)
        assertEquals(listOf(bridge, goal), result.route.map { it.block })
    }

    @Test fun `fast preprocessing honors cancellation`() {
        val cells = (0..300).map { Cell(it, 0, 0) }
        val terrain = object : EtherGeometry.Terrain {
            override fun transparent(cell: Cell) = true
            override fun landing(cell: Cell) = true
        }
        assertFailsWith<CancellationException> {
            EtherRoutePlanner.search(terrain, cells, Point(0.5, 2.32, 0.5), cells.last(), 56.0,
                1.27, 4096, true, true, null, 1) { true }
        }
    }
}
