package kitty.cat.features.misc

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.pathfinding.EtherGeometry
import kitty.cat.pathfinding.EtherGeometry.Cell
import kitty.cat.pathfinding.EtherGeometry.Point
import kitty.cat.pathfinding.EtherRouteSearch
import kitty.cat.pathfinding.EtherRoutePlanner
import kitty.cat.pathfinding.EtherTerrainCache
import kitty.cat.pathfinding.EtherSearchArea
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderLine
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.Chat
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.Schedule.schedule
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
object EtherPath : Feature("Etherwarp Pathfinder", "Finds Etherwarp routes with optional command execution.", Categories.Category.MISC) {
    val threads = numberSetting("Worker threads", 1.0, 16.0, (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4).toDouble(), "", 1.0, "Parallel raycast workers. Applies to the next search.")
    val scanBudget = numberSetting("Scan budget", 1.0, 15.0, 5.0, "ms/tick", 1.0, "Client-thread terrain capture budget. Higher values finish scanning sooner but may affect FPS.")
    val backgroundScanning = booleanSetting("Background terrain scanning", false, "Continuously cache nearby loaded terrain while this feature is enabled, even without a route request.")
    val backgroundScanBudget = numberSetting("Background scan budget", 0.25, 3.0, 1.0, "ms/tick", 0.25, "Time budget for background terrain capture. Route searches take priority.")
    val automaticPadding = booleanSetting("Automatic padding", true, "Start small and expand the search area when needed, within the search timeout.")
    val padding = numberSetting("Horizontal padding", 4.0, 64.0, 24.0, "blocks", 1.0, "Only used when Automatic padding is disabled.")
    val verticalPadding = numberSetting("Vertical padding", 4.0, 48.0, 16.0, "blocks", 1.0, "Only used when Automatic padding is disabled.")
    val timeout = numberSetting("Search timeout", 2.0, 60.0, 20.0, "s", 1.0, "Includes terrain capture and route search.")
    val nodeLimit = numberSetting("Expansion limit", 128.0, 32768.0, 4096.0, "nodes", 128.0)
    val landingLimit = numberSetting("Landing limit", 10000.0, 200000.0, 60000.0, "blocks", 10000.0)
    val detailedAiming = booleanSetting("Detailed aiming", true, "Try additional points on each visible face. Disable for faster but less complete searches.")
    val fastRouting = booleanSetting("Fast routing", true, "Try widely spaced landings first, with full-search fallback. Faster routes may use extra warps; disable to minimize hops.")
    val showLines = booleanSetting("Route lines", true)
    val showTimings = booleanSetting("Show timings", true, "Report scan and worker times after each search.")
    val findLook = actionSetting("Find looked-at block", "Find a route to the block under the crosshair.") { findLookedAt() }
    val clearRoute = actionSetting("Clear route", "Cancel work and remove route markers.") { clear() }
    private const val PASS = 1
    private const val CLEAR = 2
    private const val SUPPORT = 4
    private var job: Job? = null
    private var executionGeneration = 0L
    private val requests = linkedSetOf<Job>()
    private var displayed: List<EtherRouteSearch.Hop> = emptyList()
    private var routeLevel: ClientLevel? = null
    private var routeStart: Vec3? = null
    // Only used during client-thread capture/validation, never by search workers.
    private val passability = java.util.IdentityHashMap<Block, Int>()
    private val terrainCache = EtherTerrainCache()
    private var cacheLevel: ClientLevel? = null
    private var terrainRevision = 0L
    private var backgroundCenter: Cell? = null
    private var backgroundSections: List<Cell> = emptyList()
    private var backgroundSection = 0
    private var backgroundRow = 0
    private var backgroundCooldown = 0
    private val backgroundBuffer = ByteArray(16)
    private val backgroundPosition = BlockPos.MutableBlockPos()
    private data class RouteKey(val start: Point, val goal: Cell, val range: Double, val height: Double,
                                val detailed: Boolean, val padding: Int, val verticalPadding: Int, val fast: Boolean, val automatic: Boolean)
    private val routeCache = object : LinkedHashMap<RouteKey, Route>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RouteKey, Route>) = size > 32
    }

    fun invalidateTerrain(level: ClientLevel, pos: BlockPos) {
        if (level === cacheLevel) {
            terrainCache.invalidateBlock(pos.x, pos.y, pos.z)
            routeCache.clear()
            terrainRevision++
        }
    }

    fun invalidateChunk(level: ClientLevel, x: Int, z: Int) {
        if (level === cacheLevel) {
            terrainCache.invalidateChunk(x, z)
            routeCache.clear()
            terrainRevision++
        }
    }
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
     * Completes on the client thread after validation (cached terrain for unloaded cells); failures complete exceptionally.
     * Already at the goal returns an empty route. Cancel the future to stop this request.
     * Never block the client thread with get()/join(): terrain capture needs client ticks.
     */
    fun findRoute(goal: BlockPos): CompletableFuture<Route> {
        check(mc.isSameThread) { "EtherPath.findRoute must be called on the client thread" }
        return startRoute(Cell(goal.x, goal.y, goal.z), preview = false)
    }

    /** Re-aim immediately before the first click, after any falling or sneak preparation. */
    fun aimFromPlayer(target: Cell, range: Double): EtherGeometry.Aim? {
        check(mc.isSameThread)
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val eye = player.eyePosition
        return EtherGeometry.connection(LiveTerrain(level), Point(eye.x, eye.y, eye.z), target, range, detailedAiming.value)
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(literal("etherpath")
                .executes { Chat.send("/etherpath look [true|false] | to <x> <y> <z> [true|false] (support block) | status | steps | clear. true queues warps; default false previews only."); 1 }
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
                }.then(argument("execute", BoolArgumentType.bool()).executes { context ->
                    findLookedAt(BoolArgumentType.getBool(context, "execute"))
                    1
                }))
                .then(literal("to")
                    .then(argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .then(argument("y", IntegerArgumentType.integer(-2048, 2048))
                            .then(argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000)).executes { context ->
                                begin(Cell(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z")))
                                1
                            }.then(argument("execute", BoolArgumentType.bool()).executes { context ->
                                begin(Cell(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z")), BoolArgumentType.getBool(context, "execute"))
                                1
                            }))))))
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (routeLevel != null && routeLevel !== mc.level) clear()
            requests.toList().forEach { current ->
                if (current.completion.isCancelled || mc.level !== current.level || mc.player == null) current.cancel()
                else current.tick()
            }
            if (enabled && backgroundScanning.value && requests.isEmpty()) scanBackgroundTerrain()
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
        executionGeneration++
        val previous = job
        job = null
        displayed = emptyList()
        routeLevel = null
        routeStart = null
        previous?.cancel()
    }

    private fun clearAll() {
        backgroundCenter = null
        backgroundSections = emptyList()
        backgroundSection = 0
        backgroundRow = 0
        backgroundCooldown = 0
        terrainCache.clear()
        routeCache.clear()
        terrainRevision++
        cacheLevel = null
        val previous = requests.toList()
        requests.clear()
        clear()
        previous.forEach { it.cancel() }
    }

    private fun scanBackgroundTerrain() {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (cacheLevel !== level) {
            terrainCache.clear()
            routeCache.clear()
            terrainRevision++
            cacheLevel = level
            backgroundCenter = null
        }
        val center = Cell(floor(player.x).toInt() shr 4, floor(player.y).toInt() shr 4, floor(player.z).toInt() shr 4)
        if (center != backgroundCenter) {
            backgroundCenter = center
            // 9x9x9 sections at most: fits comfortably inside the bounded terrain cache,
            // leaving room to remember previously visited terrain after it unloads.
            backgroundSections = buildList {
                for (x in center.x - 4..center.x + 4)
                    for (z in center.z - 4..center.z + 4)
                        for (y in max(level.minY shr 4, center.y - 4)..min((level.maxY - 1) shr 4, center.y + 4))
                            add(Cell(x, y, z))
            }.sortedBy { abs(it.x - center.x) + abs(it.y - center.y) + abs(it.z - center.z) }
            backgroundSection = 0
            backgroundRow = 0
            backgroundCooldown = 0
        }
        if (backgroundCooldown > 0) { backgroundCooldown--; return }
        val deadline = System.nanoTime() + (backgroundScanBudget.value * 1_000_000).toLong()
        while (backgroundSection < backgroundSections.size && System.nanoTime() < deadline) {
            val section = backgroundSections[backgroundSection]
            if (!level.hasChunk(section.x, section.z) || terrainCache.isSectionComplete(section)) {
                backgroundSection++
                backgroundRow = 0
                continue
            }
            val x = section.x shl 4
            val y = (section.y shl 4) + backgroundRow % 16
            val z = (section.z shl 4) + backgroundRow / 16
            terrainCache.copyRow(x, y, z, 16, backgroundBuffer, 0) { bx, by, bz ->
                backgroundPosition.set(bx, by, bz)
                classify(level, backgroundPosition)
            }
            if (++backgroundRow == 256) {
                backgroundSection++
                backgroundRow = 0
            }
        }
        if (backgroundSection == backgroundSections.size) {
            backgroundSection = 0
            backgroundRow = 0
            // Completed sections are skipped next pass; revisit for loads and invalidations.
            backgroundCooldown = 20
        }
    }

    private fun findLookedAt(execute: Boolean = false) {
        val hit = mc.player?.pick(160.0, 1.0f, false) as? BlockHitResult
        if (hit == null || hit.type != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            Chat.send("Look at a loaded landing block, or use /etherpath to x y z.")
        } else begin(Cell(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z), execute)
    }

    private fun holdingEtherwarp(): Boolean {
        val item = mc.player?.mainHandItem ?: return false
        val id = item.uuid()
        return id == "ETHERWARP_CONDUIT" ||
            ((id == "ASPECT_OF_THE_END" || id == "ASPECT_OF_THE_VOID") &&
                item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("ethermerge").orElse(0) == 1)
    }

    private fun begin(goal: Cell, execute: Boolean = false) {
        if (execute && !holdingEtherwarp()) {
            Chat.send("Hold an Etherwarp Conduit or Etherwarp-upgraded AOTE/AOTV to execute a route.")
            return
        }
        val future = startRoute(goal, preview = true)
        val plannedItem = mc.player?.mainHandItem?.uuid()
        val generation = executionGeneration
        val level = mc.level
        future.whenComplete { route, error ->
            if (error != null) {
                if (error !is CancellationException) Chat.send(error.message ?: "Route search failed.")
                return@whenComplete
            }
            if (!execute || route.hops.isEmpty()) return@whenComplete
            val queue = queue@{
                if (generation != executionGeneration || mc.level !== level) return@queue
                if (!holdingEtherwarp() || mc.player?.mainHandItem?.uuid() != plannedItem) {
                    Chat.send("Route not queued: you are no longer holding an Etherwarp item.")
                    return@queue
                }
                var cancelled = false
                route.rotations.forEach { aim ->
                    val sneak = route.eyeHeight == mc.player?.getEyeHeight(Pose.CROUCHING)?.toDouble()
                    ClickUtils.queueLook(aim.yaw to aim.pitch, sneak = sneak) {
                        if (!cancelled && (generation != executionGeneration || mc.level !== level || !holdingEtherwarp())) cancelled = true
                        if (mc.player?.mainHandItem?.uuid() != plannedItem) cancelled = true
                        !cancelled
                    }
                }
                Chat.send("Queued ${route.rotations.size} Etherwarp rotations.")
            }
            val player = mc.player
            if (plannedItem == "ASPECT_OF_THE_VOID" && player?.isShiftKeyDown == true && player.isCrouching) queue()
            else schedule(2) { queue() }
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
        if (cacheLevel !== level) {
            terrainCache.clear()
            routeCache.clear()
            terrainRevision++
            cacheLevel = level
        }
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
        if (!live.landing(goal)) return failure("Goal must be a loaded or cached full block, stair, or slab with two clear blocks above it.")
        val key = RouteKey(start, goal, range, height, detailedAiming.value, padding.value.toInt(), verticalPadding.value.toInt(), fastRouting.value, automaticPadding.value)
        routeCache[key]?.let { cached ->
            var eye = start
            val valid = cached.hops.all { hop ->
                val hit = EtherGeometry.hit(live, eye, hop.aim, range) == hop.block
                eye = hop.block.eye(height)
                hit
            }
            if (valid) {
                if (preview) {
                    displayed = cached.hops
                    routeLevel = level
                    routeStart = Vec3(player.x, player.y + 0.08, player.z)
                    Chat.send("Cached route found: ${cached.hops.size} warps (validated against loaded/cached terrain).")
                }
                completion.complete(cached)
                return completion
            }
            routeCache.remove(key)
        }
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
        val areas = EtherSearchArea.areas(feet, goal, level.minY, level.maxY,
            key.automatic, key.padding, key.verticalPadding)
        if (areas.isEmpty()) {
            return failure("Destination exceeds the local search limits. Choose a closer waypoint.")
        }
        // Keep one block of range margin until boundary behavior has been verified in-game.
        val request = Job(level, areas, start, goal, range, height,
            Options(threads.value.toInt(), (scanBudget.value * 1_000_000).toLong(), (timeout.value * 1_000_000_000).toLong(), nodeLimit.value.toInt(), landingLimit.value.toInt(), detailedAiming.value), preview, completion, key)
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
        fun rowLength(index: Int, x: Int) = min(sx - index % sx, 16 - (x and 15))
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

    private class Job(val level: ClientLevel, val areas: List<EtherSearchArea.Bounds>, val start: Point, val goal: Cell, val range: Double, val height: Double, val options: Options, val preview: Boolean, val completion: CompletableFuture<Route>, val cacheKey: RouteKey) {
        private var areaIndex = 0
        private var snapshot = Snapshot(areas.first().low, areas.first().high)
        private val initialRevision = terrainRevision
        private var cursor = 0
        private val started = System.nanoTime()
        private var scanNanos = 0L
        private var scanStarted = started
        private var workerNanos = 0L
        private var reusedCells = 0
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
                val cell = snapshot.position(cursor)
                val count = snapshot.rowLength(cursor, cell.x)
                if (level.hasChunk(cell.x shr 4, cell.z shr 4)) {
                    reusedCells += terrainCache.copyRow(cell.x, cell.y, cell.z, count, snapshot.cells, cursor) { x, y, z ->
                        position.set(x, y, z)
                        classify(level, position)
                    }
                } else {
                    for (i in 0 until count) {
                        val cached = terrainCache.flags(cell.x + i, cell.y, cell.z)
                        if (cached < 0) unknown = true
                        else {
                            snapshot.cells[cursor + i] = cached.toByte()
                            reusedCells++
                        }
                    }
                }
                cursor += count
            }
            progress = "Capturing terrain: ${cursor * 100L / snapshot.cells.size}%"
            if (cursor < snapshot.cells.size) return
            if (!snapshot.landing(goal)) { fail("Goal changed or is unloaded; choose a full block, stair, or slab with two clear blocks above it."); return }
            scanNanos += System.nanoTime() - scanStarted
            launch()
        }

        private fun launch() {
            // Publication via executor submission freezes the snapshot for the duration of this job.
            // No worker reads ClientLevel, the player, module settings, or any render state.
            val factory = ThreadFactory { task -> Thread(task, "Kittycat Etherwarp Worker").apply { isDaemon = true } }
            if (coordinator == null) {
                coordinator = Executors.newSingleThreadExecutor(factory)
                workers = if (options.threads > 1) Executors.newFixedThreadPool(options.threads, factory) else null
            }
            val snapshot = this.snapshot
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
                val search = EtherRoutePlanner.search(snapshot, candidates, start, goal, range, height,
                    options.nodeLimit, options.detailed, cacheKey.fast, workers, options.threads, ::stopped)
                SearchResult(search.status, search.route, search.expanded, System.nanoTime() - workerStarted)
            })
        }

        private fun complete(result: SearchResult) {
            if (this !in requests || cancelled || completion.isCancelled) { cancel(); return }
            if (result.error != null) { fail(result.error); return }
            workerNanos += result.workerNanos
            if (result.status != EtherRouteSearch.Status.FOUND && areaIndex < areas.lastIndex && !stopped()) {
                val area = areas[++areaIndex]
                snapshot = Snapshot(area.low, area.high)
                cursor = 0
                reusedCells = 0
                unknown = false
                pending = null
                scanStarted = System.nanoTime()
                progress = "Expanding search area (${areaIndex + 1}/${areas.size})..."
                if (preview) Chat.send(progress)
                return
            }
            when (result.status) {
                EtherRouteSearch.Status.FOUND -> {
                    val live = LiveTerrain(level)
                    val player = mc.player ?: run { cancel(); return }
                    val currentEye = Point(player.x, player.y + height, player.z)
                    // Movement changes the origin of the first warp. Re-aim that hop
                    // from the player's current eye; later hops still begin at landings.
                    val adjusted = result.route.toMutableList()
                    if (adjusted.isNotEmpty()) {
                        val first = adjusted.first()
                        val aim = EtherGeometry.connection(live, currentEye, first.block, range, options.detailed)
                            ?: run { fail("The first warp is no longer reachable from your current position; run the path again."); return }
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
                    if (initialRevision == terrainRevision) routeCache[cacheKey.copy(start = currentEye)] = route
                    if (preview) {
                        displayed = route.hops
                        routeStart = Vec3(currentEye.x, currentEye.y - height + 0.08, currentEye.z)
                        Chat.send("Route found: ${displayed.size} warps, ${result.expanded} explored landings.${if (showTimings.value) " Scan ${scanNanos / 1_000_000} ms (${reusedCells * 100L / snapshot.cells.size}% cached), worker ${workerNanos / 1_000_000} ms." else ""}")
                    }
                    release()
                    completion.complete(route)
                }
                EtherRouteSearch.Status.NO_ROUTE -> fail("No route within the search area limits.${if (unknown) " Some unloaded terrain had no cached data." else ""} Try a closer waypoint or detailed aiming.")
                else -> fail("Expansion limit reached; raise it in the module or choose a closer goal.")
            }
        }

        private fun fail(message: String) {
            release()
            completion.completeExceptionally(IllegalStateException(message))
        }
    }

    private class LiveTerrain(private val level: ClientLevel) : EtherGeometry.Terrain {
        // Used only within one synchronous client-thread operation, so changes cannot
        // interleave with this cache. Loaded blocks are read live; unloaded blocks use
        // the last captured terrain, assuming it has not changed while out of view.
        private val flags = Long2ByteOpenHashMap().apply { defaultReturnValue((-1).toByte()) }
        private val position = BlockPos.MutableBlockPos()
        private fun flags(x: Int, y: Int, z: Int): Int {
            val key = BlockPos.asLong(x, y, z)
            val cached = flags.get(key).toInt()
            if (cached >= 0) return cached
            val value = if (level.hasChunk(x shr 4, z shr 4)) {
                position.set(x, y, z)
                classify(level, position)
            } else if (level === cacheLevel) terrainCache.flags(x, y, z).coerceAtLeast(0) else 0
            flags.put(key, value.toByte())
            return value
        }
        override fun transparent(cell: Cell) = transparent(cell.x, cell.y, cell.z)
        override fun transparent(x: Int, y: Int, z: Int) = flags(x, y, z) and PASS != 0
        override fun landing(cell: Cell) = flags(cell.x, cell.y, cell.z) and SUPPORT != 0 &&
            flags(cell.x, cell.y + 1, cell.z) and CLEAR != 0 && flags(cell.x, cell.y + 2, cell.z) and CLEAR != 0
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
