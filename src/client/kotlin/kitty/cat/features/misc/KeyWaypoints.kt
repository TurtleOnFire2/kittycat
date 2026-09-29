package kitty.cat.features.misc

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.keywaypoints.KeyWaypointScreen
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.LocationManager
import kitty.cat.utils.skyblock.Island
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

object KeyWaypoints : Feature("Key Waypoints", "Press or release movement keys inside placed waypoints", Categories.Category.MISC) {
    enum class KeyAction { NOTHING, PRESS, UNPRESS;
        fun next() = entries[(ordinal + 1) % entries.size]
        val label get() = when (this) { NOTHING -> "Do nothing"; PRESS -> "Press"; UNPRESS -> "Unpress" }
    }

    data class Waypoint(val dimension: String, val pos: BlockPos, val actions: MutableList<KeyAction>) {
        fun bounds() = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path = FabricLoader.getInstance().configDir.resolve("kittycat-key-waypoints.json")
    private val waypoints = mutableListOf<Waypoint>()
    private val controlled = BooleanArray(4)
    private var activeWaypoint: Waypoint? = null

    val editMode = booleanSetting("Edit mode", false, "Right click to place; shift right click a waypoint to edit it")

    init {
        cheat()
        load()
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { updateKeys() }
        LevelRenderEvents.END_MAIN.register { context ->
            if (!enabled || LocationManager.currentArea != Island.Garden || mc.level == null) return@register
            val dimension = currentDimension() ?: return@register
            val visible = waypoints.filter { it.dimension == dimension }
            context.renderBoxesBounds(visible.map { waypoint ->
                val color = if (waypoint.actions.any { it != KeyAction.NOTHING }) Color(72, 210, 155) else Color(215, 170, 85)
                BoxRender(waypoint.bounds(), color, Color(color.red, color.green, color.blue, 45))
            })
            visible.forEach { waypoint ->
                "WASD".forEachIndexed { index, letter ->
                    val action = waypoint.actions[index]
                    val color = when (action) {
                        KeyAction.NOTHING -> Color.WHITE
                        KeyAction.PRESS -> Color(115, 255, 160)
                        KeyAction.UNPRESS -> Color(255, 125, 135)
                    }
                    context.renderString("$letter ${action.label}",
                        Vec3(waypoint.pos.x + 0.5, waypoint.pos.y + 0.82 - index * 0.20, waypoint.pos.z + 0.5),
                        color, 0.55f)
                }
            }
        }
    }

    override fun onDisable() {
        activeWaypoint = null
        releaseKeys()
    }

    fun handleEditRightClick(): Boolean {
        val player = mc.player ?: return false
        if (!enabled || !editMode.value || LocationManager.currentArea != Island.Garden || mc.screen != null) return false
        val dimension = currentDimension() ?: return false
        if (player.isShiftKeyDown) {
            val start = player.eyePosition
            val end = start.add(player.getViewVector(1.0f).scale(6.0))
            val selected = waypoints.asSequence().filter { it.dimension == dimension }
                .mapNotNull { waypoint -> waypoint.bounds().clip(start, end).orElse(null)?.let { waypoint to start.distanceToSqr(it) } }
                .minByOrNull { it.second }?.first
            if (selected != null) mc.setScreen(KeyWaypointScreen(selected))
            return true
        }
        val hit = mc.hitResult as? BlockHitResult ?: return true
        if (hit.type != HitResult.Type.BLOCK) return true
        val pos = hit.blockPos.relative(hit.direction)
        if (waypoints.none { it.dimension == dimension && it.pos == pos }) {
            waypoints += Waypoint(dimension, pos, MutableList(4) { KeyAction.NOTHING })
            save()
        }
        return true
    }

    fun cycle(waypoint: Waypoint, index: Int) {
        if (waypoint !in waypoints || index !in 0..3) return
        waypoint.actions[index] = waypoint.actions[index].next()
        save()
    }

    fun delete(waypoint: Waypoint) {
        if (waypoints.remove(waypoint)) save()
    }

    private fun currentDimension() = mc.level?.dimension()?.identifier()?.toString()

    private fun mappings(): List<KeyMapping> = listOf(mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight)

    private fun updateKeys() {
        val player = mc.player
        if (!enabled || LocationManager.currentArea != Island.Garden || player == null) {
            activeWaypoint = null
            releaseKeys()
            return
        }
        if (editMode.value || mc.screen != null) {
            activeWaypoint = null
            return
        }
        val dimension = currentDimension() ?: run { activeWaypoint = null; releaseKeys(); return }
        val waypoint = waypoints.asReversed().firstOrNull { it.dimension == dimension && it.bounds().contains(player.position()) }
        if (waypoint === activeWaypoint) return
        activeWaypoint = waypoint
        if (waypoint == null) return
        mappings().forEachIndexed { index, mapping ->
            val action = waypoint.actions[index]
            if (action != KeyAction.NOTHING) {
                mapping.isDown = action == KeyAction.PRESS
                controlled[index] = true
            }
        }
    }

    private fun releaseKeys() {
        mappings().forEachIndexed { index, mapping ->
            if (controlled[index]) mapping.isDown = physicallyDown(mapping)
            controlled[index] = false
        }
    }

    private fun physicallyDown(mapping: KeyMapping): Boolean {
        val key = InputConstants.getKey(mapping.saveString())
        if (key.value < 0) return false
        return when (key.type) {
            InputConstants.Type.KEYSYM -> InputConstants.isKeyDown(mc.window, key.value)
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(mc.window.handle(), key.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }

    private fun load() {
        if (!Files.isRegularFile(path)) return
        runCatching {
            val root = Files.newBufferedReader(path, StandardCharsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
            root?.getAsJsonArray("waypoints")?.forEach { element ->
                runCatching {
                    val entry = element.asJsonObject
                    val actions = entry.getAsJsonArray("actions")?.map { KeyAction.valueOf(it.asString) } ?: emptyList()
                    if (actions.size == 4) waypoints += Waypoint(
                        entry.get("dimension").asString,
                        BlockPos(entry.get("x").asInt, entry.get("y").asInt, entry.get("z").asInt),
                        actions.toMutableList()
                    )
                }
            }
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(path.parent)
            val root = JsonObject()
            val array = JsonArray()
            waypoints.forEach { waypoint ->
                val entry = JsonObject()
                entry.addProperty("dimension", waypoint.dimension)
                entry.addProperty("x", waypoint.pos.x)
                entry.addProperty("y", waypoint.pos.y)
                entry.addProperty("z", waypoint.pos.z)
                val actions = JsonArray()
                waypoint.actions.forEach { actions.add(it.name) }
                entry.add("actions", actions)
                array.add(entry)
            }
            root.add("waypoints", array)
            Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { gson.toJson(root, it) }
        }
    }
}
