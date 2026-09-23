package kitty.cat.pathfinding

import kitty.cat.pathfinding.EtherGeometry.Cell
import kitty.cat.pathfinding.EtherGeometry.Point
import kotlin.test.*

class EtherSearchAreaTest {
    @Test fun `automatic areas grow to include a required detour`() {
        val start = Cell(0, 0, 0)
        val goal = Cell(20, 0, 0)
        val bridge = Cell(10, 0, 12)
        val areas = EtherSearchArea.areas(start, goal, -64, 320, true, 4, 4)
        val statuses = areas.take(2).map { area ->
            fun inside(cell: Cell) = cell.x in area.low.x..area.high.x &&
                cell.y in area.low.y..area.high.y && cell.z in area.low.z..area.high.z
            val nodes = listOf(bridge, goal).filter(::inside)
            val terrain = object : EtherGeometry.Terrain {
                override fun transparent(cell: Cell) = inside(cell) && cell !in nodes
                override fun landing(cell: Cell) = cell in nodes
            }
            EtherRoutePlanner.search(terrain, nodes, Point(0.5, 2.32, 0.5), goal,
                17.0, 1.27, 128, true, true, null, 1).status
        }
        assertEquals(listOf(EtherRouteSearch.Status.NO_ROUTE, EtherRouteSearch.Status.FOUND), statuses)
        areas.zipWithNext().forEach { (a, b) ->
            assertTrue(b.low.x <= a.low.x && b.low.y <= a.low.y && b.low.z <= a.low.z)
            assertTrue(b.high.x >= a.high.x && b.high.y >= a.high.y && b.high.z >= a.high.z)
        }
    }

    @Test fun `automatic expansion respects volume and world bounds`() {
        val areas = EtherSearchArea.areas(Cell(-90, -64, -90), Cell(90, 0, 90), -64, 320, true, 4, 4)
        assertTrue(areas.isNotEmpty())
        assertTrue(areas.size < 5)
        for ((low, high) in areas) {
            assertTrue(low.y >= -64 && high.y < 320)
            assertTrue((high.x - low.x + 1).toLong() * (high.y - low.y + 1) * (high.z - low.z + 1) <= 4_000_000)
        }
        assertTrue(EtherSearchArea.areas(Cell(0, 0, 0), Cell(400, 0, 0), -64, 320, true, 4, 4).isEmpty())
    }

    @Test fun `manual mode preserves configured margins and uses one attempt`() {
        val areas = EtherSearchArea.areas(Cell(0, 20, 0), Cell(30, 30, 40), -64, 320, false, 24, 16)
        assertEquals(listOf(EtherSearchArea.Bounds(Cell(-24, 4, -24), Cell(54, 50, 64))), areas)
    }
}
