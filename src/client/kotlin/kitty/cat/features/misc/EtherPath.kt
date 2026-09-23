package kitty.cat.features.misc

import com.mojang.brigadier.arguments.IntegerArgumentType
import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.pathfinding.EtherGeometry
import kitty.cat.pathfinding.EtherGeometry.Cell
import kitty.cat.pathfinding.EtherGeometry.Point
import kitty.cat.pathfinding.EtherRouteSearch
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderLine
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.Chat
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.*
import kotlin.math.*

/** World capture/validation run on the client; search workers only see a frozen byte snapshot. */
object EtherPath : Feature("Etherwarp Pathfinder", "Finds and previews Etherwarp routes without executing them.", Categories.Category.MISC) {
    val threads = numberSetting("Worker threads", 1.0, 16.0, (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4).toDouble(), "", 1.0, "Parallel raycast workers. Applies to the next search.")
    val scanBudget = numberSetting("Scan budget", 1.0, 15.0, 5.0, "ms/tick", 1.0, "Client-thread terrain capture budget. Higher values finish scanning sooner but may affect FPS.")
    val padding = numberSetting("Horizontal padding", 4.0, 64.0, 24.0, "blocks", 1.0, "Room for detours around the start and goal.")
    val verticalPadding = numberSetting("Vertical padding", 4.0, 48.0, 16.0, "blocks", 1.0)
    val timeout = numberSetting("Search timeout", 2.0, 60.0, 20.0, "s", 1.0, "Includes terrain capture and route search.")
    val nodeLimit = numberSetting("Expansion limit", 128.0, 32768.0, 4096.0, "nodes", 128.0)
    val landingLimit = numberSetting("Landing limit", 10000.0, 200000.0, 60000.0, "blocks", 10000.0)
    val detailedAiming = booleanSetting("Detailed aiming", true, "Try additional points on each visible face. Disable for faster but less complete searches.")
    val showLines = booleanSetting("Route lines", true)
    val showTimings = booleanSetting("Show timings", true, "Report scan and worker times after each search.")
    val findLook = actionSetting("Find looked-at block", "Find a route to the block under the crosshair.") { findLookedAt() }
    val clearRoute = actionSetting("Clear route", "Cancel work and remove route markers.") { clear() }
    private const val PASS = 1
    private const val CLEAR = 2
    private const val SUPPORT = 4
    private const val MAX_CELLS = 4_000_000
    private var job: Job? = null
    private val requests = linkedSetOf<Job>()
    private var displayed: List<EtherRouteSearch.Hop> = emptyList()
    private var routeLevel: ClientLevel? = null
    private var routeStart: Vec3? = null
    // Only used during client-thread capture/validation, never by search workers.
    private val passability = java.util.IdentityHashMap<Block, Int>()
    override fun onDisable() = clearAll()

    data class Route(
        val hops: List<EtherRouteSearch.Hop>,
        val startEye: Point,
        val eyeHeight: Double,
        val range: Double
    ) {
        /** One yaw/pitch in degrees per warp, in execution order; no starting-position entry. */
        val rotations: List<EtherGeometry.Aim> = hops.map { it.aim }
    }

    /**
     * Call on the client thread. Goal is the support block, not the player's feet position.
     * Uses current module settings and held-item range/pose. Independent of preview requests.
     * Completes on the client thread after live validation; failures complete exceptionally.
     * Already at the goal returns an empty route. Cancel the future to stop this request.
     * Never block the client thread with get()/join(): terrain capture needs client ticks.
     */
    fun findRoute(goal: BlockPos): CompletableFuture<Route> {
        check(mc.isSameThread) { "EtherPath.findRoute must be called on the client thread" }
        return startRoute(Cell(goal.x, goal.y, goal.z), preview = false)
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(literal("etherpath")
                .executes { Chat.send("/etherpath look | to <x> <y> <z> (support block) | status | steps | clear. Settings: /kc > Misc > Etherwarp Pathfinder."); 1 }
                .then(literal("status").executes { Chat.send(job?.progress ?: if (displayed.isEmpty()) "No active route search." else "${displayed.size}-warp route displayed."); 1 })
                .then(literal("clear").executes { clear(); Chat.send("Etherwarp route cleared."); 1 })
                .then(literal("steps").executes {
                    if (displayed.isEmpty()) Chat.send("No route displayed.")
                    displayed.forEachIndexed { index, hop ->
                        Chat.send("${index + 1}: ${hop.block.x} ${hop.block.y} ${hop.block.z}; yaw ${"%.2f".format(java.util.Locale.ROOT, hop.aim.yaw)}, pitch ${"%.2f".format(java.util.Locale.ROOT, hop.aim.pitch)}")
                    }
                    1
                })
                .then(literal("look").executes {
                    findLookedAt()
                    1
                })
                .then(literal("to")
                    .then(argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .then(argument("y", IntegerArgumentType.integer(-2048, 2048))
                            .then(argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000)).executes { context ->
                                begin(Cell(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z")))
                                1
                            })))))
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (routeLevel != null && routeLevel !== mc.level) clear()
            requests.toList().forEach { current ->
                if (current.completion.isCancelled || mc.level !== current.level || mc.player == null) current.cancel()
                else current.tick()
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> clearAll() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { clearAll() }
        LevelRenderEvents.END_MAIN.register { context ->
            if (routeLevel !== mc.level) return@register
            var previous = routeStart
            displayed.forEachIndexed { index, hop ->
                val block = hop.block
                val color = if (index == displayed.lastIndex) Color.GREEN else Color.CYAN
                val marker = Vec3(block.x + 0.5, block.y + 1.08, block.z + 0.5)
                if (showLines.value) previous?.let { context.renderLine(it, marker, Color(80, 220, 240, 160), 2f, false) }
                previous = marker
                context.renderBoxBounds(AABB(block.x.toDouble(), block.y + 1.005, block.z.toDouble(), block.x + 1.0, block.y + 1.065, block.z + 1.0), color)
                context.renderString("${index + 1}${if (index == displayed.lastIndex) " - Goal" else ""}", Vec3(block.x + 0.5, block.y + 1.5, block.z + 0.5), color, 1.5f)
            }
        }
    }

    private fun clear() {
        val previous = job
        job = null
        displayed = emptyList()
        routeLevel = null
        routeStart = null
        previous?.cancel()
    }

    private fun clearAll() {
        val previous = requests.toList()
        requests.clear()
        clear()
        previous.forEach { it.cancel() }
    }

    private fun findLookedAt() {
        val hit = mc.player?.pick(160.0, 1.0f, false) as? BlockHitResult
        if (hit == null || hit.type != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Chat.send("Look at a loaded landing block, or use /etherpath to x y z.")
        } else begin(Cell(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z))
    }

    private fun begin(goal: Cell) {
        startRoute(goal, preview = true).whenComplete { _, error ->
            if (error != null && error !is CancellationException) Chat.send(error.message ?: "Route search failed.")
        }
    }

    private fun startRoute(goal: Cell, preview: Boolean): CompletableFuture<Route> {
        if (preview) { clear(); setEnabled(true) }
        val completion = CompletableFuture<Route>()
        fun failure(message: String): CompletableFuture<Route> {
            completion.completeExceptionally(IllegalStateException(message))
            return completion
        }
        val player = mc.player ?: return failure("No player is available.")
        val level = mc.level ?: return failure("No world is loaded.")
        val item = player.mainHandItem
        val id = item.uuid()
        val data = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        val eligible = id == "ETHERWARP_CONDUIT" ||
            ((id == "ASPECT_OF_THE_END" || id == "ASPECT_OF_THE_VOID") && data.getInt("ethermerge").orElse(0) == 1)
        val bonus = if (eligible) data.getInt("tuned_transmission").orElse(0).coerceIn(0, 4) else 0
        val height = player.getEyeHeight(if (id == "ETHERWARP_CONDUIT" && !player.isCrouching) Pose.STANDING else Pose.CROUCHING).toDouble()
        val start = Point(player.x, player.y + height, player.z)
        val feet = Cell(floor(player.x).toInt(), floor(player.y).toInt() - 1, floor(player.z).toInt())
        val range = 56.0 + bonus
        if (feet == goal) {
            if (preview) Chat.send("You are already on that block.")
            completion.complete(Route(emptyList(), start, height, range))
            return completion
        }
        val live = LiveTerrain(level)
        if (!live.landing(goal)) return failure("Goal must be a loaded full block, stair, or slab with two clear blocks above it.")
        // Most nearby requests do not need a volume scan or graph at all.
        val direct = EtherGeometry.connection(live, start, goal, range, detailedAiming.value)
        if (direct != null) {
            val route = Route(listOf(EtherRouteSearch.Hop(goal, direct)), start, height, range)
            if (preview) {
                displayed = route.hops
                routeLevel = level
                routeStart = Vec3(player.x, player.y + 0.08, player.z)
                Chat.send("Direct route found: 1 warp (no terrain scan needed).")
            }
            completion.complete(route)
            return completion
        }
        val pad = padding.value.toInt()
        val vertical = verticalPadding.value.toInt()
        val low = Cell(min(feet.x, goal.x) - pad, max(level.minY, min(feet.y, goal.y) - vertical), min(feet.z, goal.z) - pad)
        val high = Cell(max(feet.x, goal.x) + pad, min(level.maxY - 1, max(feet.y, goal.y) + vertical + 4), max(feet.z, goal.z) + pad)
        val volume = (high.x - low.x + 1).toLong() * (high.y - low.y + 1) * (high.z - low.z + 1)
        if (goal.y !in level.minY until level.maxY || volume !in 1..MAX_CELLS.toLong() ||
            high.x - low.x > 320 || high.z - low.z > 320 || high.y - low.y > 160) {
            return failure("Destination exceeds the local search limits. Choose a closer waypoint.")
        }
        // Keep one block of range margin until boundary behavior has been verified in-game.
        val request = Job(level, Snapshot(low, high), start, goal, range, height,
            Options(threads.value.toInt(), (scanBudget.value * 1_000_000).toLong(), (timeout.value * 1_000_000_000).toLong(), nodeLimit.value.toInt(), landingLimit.value.toInt(), detailedAiming.value), preview, completion)
        requests.add(request)
        if (preview) {
            job = request
            routeLevel = level
            Chat.send("Finding Etherwarp route (range ${56 + bonus}). ${if (!eligible) "No Etherwarp item held; assuming base range and sneaking." else if (id == "ETHERWARP_CONDUIT" && !player.isCrouching) "Using standing conduit eye height." else "Using sneaking eye height."}")
        }
        return completion
    }

    private data class Options(val threads: Int, val scanNanos: Long, val timeoutNanos: Long, val nodeLimit: Int, val landingLimit: Int, val detailed: Boolean)

    private class Snapshot(val low: Cell, high: Cell) : EtherGeometry.Terrain {
        private val sx = high.x - low.x + 1
        private val sy = high.y - low.y + 1
        private val sz = high.z - low.z + 1
        val cells = ByteArray(sx * sy * sz)
        fun position(index: Int) = Cell(low.x + index % sx, low.y + index / sx % sy, low.z + index / (sx * sy))
        private fun flags(worldX: Int, worldY: Int, worldZ: Int): Int {
            val x = worldX - low.x; val y = worldY - low.y; val z = worldZ - low.z
            if (x !in 0 until sx || y !in 0 until sy || z !in 0 until sz) return 0
            return cells[x + sx * (y + sy * z)].toInt()
        }
        override fun transparent(cell: Cell) = transparent(cell.x, cell.y, cell.z)
        override fun transparent(x: Int, y: Int, z: Int) = flags(x, y, z) and PASS != 0
        override fun landing(cell: Cell) = flags(cell.x, cell.y, cell.z) and SUPPORT != 0 &&
            flags(cell.x, cell.y + 1, cell.z) and CLEAR != 0 && flags(cell.x, cell.y + 2, cell.z) and CLEAR != 0
    }

    private data class SearchResult(val status: EtherRouteSearch.Status, val route: List<EtherRouteSearch.Hop>, val expanded: Int, val workerNanos: Long, val error: String? = null)

    private class Job(val level: ClientLevel, val snapshot: Snapshot, val start: Point, val goal: Cell, val range: Double, val height: Double, val options: Options, val preview: Boolean, val completion: CompletableFuture<Route>) {
        private var cursor = 0
        private val started = System.nanoTime()
        private var scanNanos = 0L
        private val position = BlockPos.MutableBlockPos()
        private var unknown = false
        @Volatile private var cancelled = false
        @Volatile var progress = "Capturing terrain..."
            private set
        private var coordinator: ExecutorService? = null
        private var workers: ExecutorService? = null
        private var pending: Future<SearchResult>? = null

        fun cancel() {
            release()
            completion.cancel(false)
        }

        private fun release() {
            cancelled = true
            pending?.cancel(true)
            workers?.shutdownNow()
            coordinator?.shutdownNow()
            requests.remove(this)
            if (job === this) job = null
        }

        private fun stopped() = cancelled || completion.isCancelled || System.nanoTime() - started > options.timeoutNanos

        fun tick() {
            if (stopped()) { fail("Search time limit reached; increase the module timeout or choose a closer goal."); return }
            val future = pending
            if (future != null) {
                if (!future.isDone) return
                val result = try { future.get() } catch (_: CancellationException) {
                    fail("Search cancelled."); return
                } catch (exception: ExecutionException) {
                    fail("Route search failed: ${exception.cause?.message ?: "worker error"}"); return
                }
                complete(result)
                return
            }
            val deadline = System.nanoTime() + options.scanNanos
            while (cursor < snapshot.cells.size && System.nanoTime() < deadline) {
                repeat(min(128, snapshot.cells.size - cursor)) {
                    val cell = snapshot.position(cursor)
                    position.set(cell.x, cell.y, cell.z)
                    if (level.hasChunk(cell.x shr 4, cell.z shr 4)) snapshot.cells[cursor] = classify(level, position).toByte()
                    else unknown = true
                    cursor++
                }
            }
            progress = "Capturing terrain: ${cursor * 100L / snapshot.cells.size}%"
            if (cursor < snapshot.cells.size) return
            if (!snapshot.landing(goal)) { fail("Goal changed or is unloaded; choose a full block, stair, or slab with two clear blocks above it."); return }
            scanNanos = System.nanoTime() - started
            launch()
        }

        private fun launch() {
            // Publication via executor submission freezes the snapshot for the duration of this job.
            // No worker reads ClientLevel, the player, module settings, or any render state.
            val factory = ThreadFactory { task -> Thread(task, "Kittycat Etherwarp Worker").apply { isDaemon = true } }
            coordinator = Executors.newSingleThreadExecutor(factory)
            workers = if (options.threads > 1) Executors.newFixedThreadPool(options.threads, factory) else null
            pending = coordinator!!.submit(Callable {
                val workerStarted = System.nanoTime()
                progress = "Indexing landing blocks..."
                val candidates = ArrayList<Cell>()
                for (index in snapshot.cells.indices) {
                    if (index and 4095 == 0 && stopped()) throw CancellationException()
                    if (snapshot.cells[index].toInt() and SUPPORT == 0) continue
                    val cell = snapshot.position(index)
                    if (snapshot.landing(cell)) candidates.add(cell)
                    if (candidates.size > options.landingLimit) return@Callable SearchResult(EtherRouteSearch.Status.LIMIT, emptyList(), 0, System.nanoTime() - workerStarted, "Landing limit reached; raise it in the module or reduce padding.")
                }
                progress = "Searching ${candidates.size} landings with ${options.threads} worker thread(s)..."
                val search = EtherRouteSearch(snapshot, candidates, start, goal, range, height, options.nodeLimit, options.detailed)
                search.runParallel(workers, options.threads, ::stopped)
                SearchResult(search.status, search.route, search.expanded, System.nanoTime() - workerStarted)
            })
        }

        private fun complete(result: SearchResult) {
            if (this !in requests || cancelled || completion.isCancelled) { cancel(); return }
            if (result.error != null) { fail(result.error); return }
            when (result.status) {
                EtherRouteSearch.Status.FOUND -> {
                    val live = LiveTerrain(level)
                    val player = mc.player ?: run { cancel(); return }
                    val currentEye = Point(player.x, player.y + height, player.z)
                    val horizontalMovement = hypot(currentEye.x - start.x, currentEye.z - start.z)
                    if (horizontalMovement > 0.25) {
                        fail("You moved horizontally while searching; stand still and run the command again."); return
                    }

                    // Falling only changes the origin of the first warp. Re-aim that hop
                    // from the player's current eye; later hops still begin at landings.
                    val adjusted = result.route.toMutableList()
                    if (adjusted.isNotEmpty()) {
                        val first = adjusted.first()
                        val aim = EtherGeometry.connection(live, currentEye, first.block, range, options.detailed)
                            ?: run { fail("You fell out of reach of the first warp; run the path again after landing."); return }
                        adjusted[0] = first.copy(aim = aim)
                    }

                    var eye = currentEye
                    for (hop in adjusted) {
                        if (EtherGeometry.hit(live, eye, hop.aim, range) != hop.block) {
                            fail("Terrain changed while searching; run the command again."); return
                        }
                        eye = hop.block.eye(height)
                    }
                    val route = Route(adjusted, currentEye, height, range)
                    if (preview) {
                        displayed = route.hops
                        routeStart = Vec3(currentEye.x, currentEye.y - height + 0.08, currentEye.z)
                        Chat.send("Route found: ${displayed.size} warps, ${result.expanded} explored landings.${if (showTimings.value) " Scan ${scanNanos / 1_000_000} ms, worker ${result.workerNanos / 1_000_000} ms." else ""}")
                    }
                    release()
                    completion.complete(route)
                }
                EtherRouteSearch.Status.NO_ROUTE -> fail("No route in the scanned area.${if (unknown) " Some terrain was unloaded." else ""} Try more padding or detailed aiming.")
                else -> fail("Expansion limit reached; raise it in the module or choose a closer goal.")
            }
        }

        private fun fail(message: String) {
            release()
            completion.completeExceptionally(IllegalStateException(message))
        }
    }

    private class LiveTerrain(private val level: ClientLevel) : EtherGeometry.Terrain {
        private fun flags(cell: Cell): Int = if (level.hasChunk(cell.x shr 4, cell.z shr 4)) classify(level, BlockPos(cell.x, cell.y, cell.z)) else 0
        override fun transparent(cell: Cell) = flags(cell) and PASS != 0
        override fun landing(cell: Cell) = flags(cell) and SUPPORT != 0 &&
            flags(cell.copy(y = cell.y + 1)) and CLEAR != 0 && flags(cell.copy(y = cell.y + 2)) and CLEAR != 0
    }

    /** Conservative classification: unsupported thin blocks stop the ray but are not destinations. */
    private fun classify(level: ClientLevel, pos: BlockPos): Int {
        val state = level.getBlockState(pos)
        val block = state.block
        if (state.isAir) return PASS or CLEAR
        val flags = passability.getOrPut(block) { passFlags(block) }
        if (flags != 0) return flags
        // These are valid targets even though their collision shape is not a full cube.
        // Keep the Etherwarp block-based arrival offset; collision height is not a
        // measurement of the server's initial teleport position.
        if (block is StairBlock || block is SlabBlock) return SUPPORT
        return if (state.isCollisionShapeFullBlock(level, pos)) SUPPORT else 0
    }

    private fun passFlags(block: Block): Int {
        if (block is LadderBlock || block is VineBlock || block is SkullBlock || block is WallSkullBlock || block is FlowerPotBlock) return PASS
        val passable = block is LiquidBlock || block is FlowerBlock || block is TallGrassBlock ||
            block is SaplingBlock || block is CropBlock || block is StemBlock || block is MushroomBlock ||
            block is TorchBlock || block is ButtonBlock || block is LeverBlock || block is RailBlock ||
            block is RedStoneWireBlock || block is RepeaterBlock || block is ComparatorBlock ||
            block is TripWireBlock || block is TripWireHookBlock || block is FireBlock ||
            block is SnowLayerBlock || block is SugarCaneBlock || block is NetherWartBlock ||
            block is SeagrassBlock || block is TallSeagrassBlock || block is DoublePlantBlock ||
            block is WebBlock || block is NetherPortalBlock
        return if (passable) PASS or CLEAR else 0
    }
}
