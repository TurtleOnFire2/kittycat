package kitty.cat.features.huds

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.Crate
import kitty.cat.features.kuudra.CratePriority
import kitty.cat.features.kuudra.EtherwarpWaypoints
import kitty.cat.features.kuudra.SafeSpots
import kitty.cat.gui.Hud
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.sprite.SpriteId
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.roundToInt

object CratePriorityPreviewHud : Hud.Component("CratePriorityPreviewHud", 0.5, 0.15, 1f) {
    private const val MAP_SIZE = 150
    private const val WIDTH = 160
    private const val HEIGHT = 160
    private const val MIN_Y = 75
    private const val MAX_Y = 79
    private val secondCrates = listOf(Crate.Shop, Crate.xCannon, Crate.Square)
    private data class CaptureBounds(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxZ - minZ + 1
    }
    private val captureBounds = mapOf(
        Crate.Shop to CaptureBounds(-82, -158, -71, -135),
        Crate.Square to CaptureBounds(-154, -91, -140, -78),
        Crate.xCannon to CaptureBounds(-149, -132, -131, -115)
    )
    private val captureFile = FabricLoader.getInstance().configDir.resolve("kittycat/kuudra-preview-capture.json")

    private data class Terrain(
        val crateType: Crate,
        val warp: Vec3?,
        val originX: Int,
        val originZ: Int,
        val width: Int,
        val height: Int,
        val colors: IntArray,
        val sprites: Array<TextureAtlasSprite?>,
        val blocks: Array<String>,
        val heights: IntArray,
        val known: BooleanArray
    ) {
        val complete: Boolean get() = known.all { it }
    }

    private val terrainCache = mutableMapOf<Crate, Terrain>()
    private val captureCache = mutableMapOf<Crate, Terrain>()
    private var captureActive = false
    private var ticks = 0

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(literal("cratepreviewcapture").executes {
                if (!kuudra() || mc.level == null) {
                    Chat.send("Enter Kuudra before capturing the crate preview.")
                } else {
                    captureActive = true
                    Chat.send("Capturing Shop, X Cannon, and Square terrain at Y $MIN_Y-$MAX_Y. Move around until all three areas load.")
                    captureTick()
                    if (captureActive) Chat.send("Captured cells: " + secondCrates.joinToString { crate ->
                        val map = captureTerrain(crate)
                        "${crate.name} ${map.known.count { it }}/${map.known.size}"
                    })
                }
                1
            })
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (++ticks % 10 == 0 && captureActive && kuudra()) captureTick()
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            captureActive = false
            ticks = 0
            terrainCache.clear()
            captureCache.clear()
        }
    }

    override fun render(context: GuiGraphicsExtractor) {
        if (!CratePriority.enabled || !CratePriority.destinationPreview.value || !kuudra() || !supplies()) return
        CratePriority.missing.takeIf { it in KuudraPreviewTerrain.maps }?.let { draw(context, terrain(it)) }
    }

    override fun example(context: GuiGraphicsExtractor) {
        val (width, height) = frameSize()
        context.fill(0, 0, width, height, 0xCC101821.toInt())
        context.fill(5, 5, width - 5, height - 5, 0xFF424D49.toInt())
        context.fill(width / 2 - 12, height / 2 - 20, width / 2 + 13, height / 2 + 22, 0xFF655D4E.toInt())
        context.fill(width / 2 - 4, height / 2 - 4, width / 2 + 4, height / 2 + 4, 0xFF42D9E8.toInt())
        context.fill(width * 2 / 3 - 6, height * 2 / 3 - 6, width * 2 / 3 + 6, height * 2 / 3 + 6, 0xFFFFBA59.toInt())
        context.fill(width / 3 - 4, height * 2 / 3 - 4, width / 3 + 4, height * 2 / 3 + 4, 0xFF69EF78.toInt())
        context.outline(0, 0, width, height, 0xFF9CA9B0.toInt())
    }

    override fun bounds(): Pair<Double, Double> = frameSize().let { it.first.toDouble() to it.second.toDouble() }

    private fun frameSize(): Pair<Int, Int> {
        val snapshot = KuudraPreviewTerrain.maps[CratePriority.missing] ?: return WIDTH to HEIGHT
        val rotate = CratePriority.missing == Crate.Square || CratePriority.missing == Crate.xCannon
        val cell = MAP_SIZE.toDouble() / max(snapshot.width, snapshot.height)
        val displayWidth = if (rotate) snapshot.height else snapshot.width
        val displayHeight = if (rotate) snapshot.width else snapshot.height
        return 10 + (displayWidth * cell).roundToInt() to 10 + (displayHeight * cell).roundToInt()
    }

    private fun draw(context: GuiGraphicsExtractor, map: Terrain) {
        val left = 5
        val top = 5
        val rotateClockwise = map.crateType == Crate.Square || map.crateType == Crate.xCannon
        val displayWidth = if (rotateClockwise) map.height else map.width
        val displayHeight = if (rotateClockwise) map.width else map.height
        val cell = MAP_SIZE.toDouble() / max(map.width, map.height)
        val frameWidth = 10 + (displayWidth * cell).roundToInt()
        val frameHeight = 10 + (displayHeight * cell).roundToInt()
        context.fill(0, 0, frameWidth, frameHeight, 0xCC101821.toInt())
        for (z in 0 until map.height) for (x in 0 until map.width) {
            val displayX = if (rotateClockwise) map.height - z - 1 else x
            val displayZ = if (rotateClockwise) x else z
            val x1 = left + (displayX * cell).roundToInt()
            val z1 = top + (displayZ * cell).roundToInt()
            val x2 = left + ((displayX + 1) * cell).roundToInt()
            val z2 = top + ((displayZ + 1) * cell).roundToInt()
            val index = z * map.width + x
            val sprite = map.sprites[index]
            if (sprite == null) context.fill(x1, z1, x2, z2, map.colors[index])
            else context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x1, z1, max(1, x2 - x1), max(1, z2 - z1))
        }
        fun marker(pos: Vec3, color: Int, radius: Int = 3) {
            val localX = pos.x - map.originX
            val localZ = pos.z - map.originZ
            val x = left + ((if (rotateClockwise) map.height - localZ else localX) * cell).roundToInt()
            val z = top + ((if (rotateClockwise) localX else localZ) * cell).roundToInt()
            if (x < left || x >= left + displayWidth * cell || z < top || z >= top + displayHeight * cell) return
            context.fill(x - radius - 1, z - radius - 1, x + radius + 2, z + radius + 2, 0xFF101821.toInt())
            context.fill(x - radius, z - radius, x + radius + 1, z + radius + 1, color)
        }
        SafeSpots.safeSpots.forEach { spot ->
            marker(spot.loc, if (spot.safe) 0xFF37DD68.toInt() else 0xFFE35858.toInt(), 1)
        }
        CratePriority.previewSupply(map.crateType)?.let { marker(it.position, 0xFFFFBA59.toInt(), if (it.fromChat) 6 else 3) }
        map.warp?.let { marker(it, 0xFF42D9E8.toInt()) }
        mc.player?.position()?.let { marker(it, 0xFF69EF78.toInt()) }
        context.outline(0, 0, frameWidth, frameHeight, 0xFF9CA9B0.toInt())
    }

    private fun terrain(crateType: Crate): Terrain = terrainCache.getOrPut(crateType) {
        val snapshot = KuudraPreviewTerrain.maps.getValue(crateType)
        val warp = EtherwarpWaypoints.waypoints.firstOrNull { it.third == crateType }?.second
        val size = snapshot.width * snapshot.height
        val spritePalette = KuudraPreviewTerrain.palette.map { texture ->
            texture?.let { mc.atlasManager.get(SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.parse(it))) }
        }
        val lavaSprite = mc.atlasManager.get(SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.parse("minecraft:block/lava_still")))
        Terrain(crateType, warp, snapshot.originX, snapshot.originZ, snapshot.width, snapshot.height,
            IntArray(size) { 0xFF29313B.toInt() },
            Array(size) { index -> spritePalette[snapshot.rows[index / snapshot.width][index % snapshot.width].digitToInt()] ?: lavaSprite },
            Array(size) { "" }, IntArray(size) { 78 }, BooleanArray(size) { true })
    }

    private fun captureTerrain(crateType: Crate): Terrain = captureCache.getOrPut(crateType) {
        val bounds = captureBounds.getValue(crateType)
        val warp = EtherwarpWaypoints.waypoints.firstOrNull { it.third == crateType }?.second
        val size = bounds.width * bounds.height
        Terrain(crateType, warp, bounds.minX, bounds.minZ, bounds.width, bounds.height,
            IntArray(size) { 0xFF29313B.toInt() }, arrayOfNulls(size), Array(size) { "unloaded" }, IntArray(size) { -1 }, BooleanArray(size))
    }

    private fun scan(map: Terrain, level: ClientLevel) {
        if (map.complete) return
        for (z in 0 until map.height) for (x in 0 until map.width) {
            val index = z * map.width + x
            if (map.known[index]) continue
            val worldX = map.originX + x
            val worldZ = map.originZ + z
            if (!level.hasChunk(worldX shr 4, worldZ shr 4)) continue
            map.known[index] = true
            map.blocks[index] = "minecraft:air"
            for (y in MAX_Y downTo MIN_Y) {
                val block = BlockPos(worldX, y, worldZ)
                val state = level.getBlockState(block)
                if (state.isAir) continue
                map.blocks[index] = state.toString()
                map.heights[index] = y
                val rgb = state.getMapColor(level, block).col
                map.colors[index] = 0xFF000000.toInt() or (rgb and 0xFFFFFF)
                val parts = mutableListOf<BlockStateModelPart>()
                mc.modelManager.blockStateModelSet.get(state).collectParts(RandomSource.create(block.asLong()), parts)
                map.sprites[index] = parts.asSequence()
                    .flatMap { it.getQuads(Direction.UP).asSequence() }
                    .firstOrNull()?.materialInfo()?.sprite()
                    ?: mc.modelManager.blockStateModelSet.getParticleMaterial(state).sprite()
                break
            }
        }
    }

    private fun captureTick() {
        val level = mc.level ?: return
        secondCrates.forEach { scan(captureTerrain(it), level) }
        if (secondCrates.any { !captureTerrain(it).complete }) return
        captureActive = false
        try {
            val root = JsonObject()
            root.addProperty("version", 4)
            root.addProperty("minY", MIN_Y)
            root.addProperty("maxY", MAX_Y)
            val maps = JsonArray()
            secondCrates.forEach { crate ->
                val map = captureTerrain(crate)
                val entry = JsonObject()
                entry.addProperty("crate", crate.name)
                entry.addProperty("originX", map.originX)
                entry.addProperty("originZ", map.originZ)
                entry.addProperty("width", map.width)
                entry.addProperty("height", map.height)
                val rows = JsonArray()
                for (z in 0 until map.height) {
                    val row = JsonArray()
                    for (x in 0 until map.width) {
                        val index = z * map.width + x
                        val block = JsonObject()
                        block.addProperty("state", map.blocks[index])
                        block.addProperty("y", map.heights[index])
                        block.addProperty("texture", map.sprites[index]?.contents()?.name()?.toString())
                        block.addProperty("rgb", map.colors[index] and 0xFFFFFF)
                        row.add(block)
                    }
                    rows.add(row)
                }
                entry.add("rows", rows)
                maps.add(entry)
            }
            root.add("maps", maps)
            Files.createDirectories(captureFile.parent)
            Files.writeString(captureFile, GsonBuilder().setPrettyPrinting().create().toJson(root))
            Chat.send("Saved all three crate preview maps to $captureFile")
        } catch (error: Exception) {
            Chat.send("Could not save crate preview capture: ${error.message}")
        }
    }
}
