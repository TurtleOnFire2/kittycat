package kitty.cat.features.visual

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kitty.cat.KittycatClient.mc
import kitty.cat.gui.bestiaryesp.BestiaryESPScreen
import kitty.cat.gui.categories.Categories
import kitty.cat.features.Feature
import kitty.cat.utils.Mob
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderTracer
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.render.world.Render3D.TracerRender
import kitty.cat.render.world.Render3D.renderTracers
import kitty.cat.utils.allMobs
import kitty.cat.utils.KuudraUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import java.awt.Color
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.Future
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists

object BestiaryESP : Feature("Bestiary ESP", "", Categories.Category.VISUAL) {
    val openGuiAction = actionSetting("Open Bestiary ESP") { openGui = true }

    var openGui = false

    private val espEntities    = mutableListOf<Pair<Entity, String>>()
    private val tracerEntities = mutableListOf<Pair<Entity, String>>()

    val enabledMobs  = mutableListOf<Mob>()
    val tracerMobs   = mutableListOf<Mob>()
    val espColors    = mutableMapOf<String, Int>()
    val tracerColors = mutableMapOf<String, Int>()

    private val configPath = FabricLoader.getInstance().configDir.resolve("kittycat/bestiary_esp.json")
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    private data class ColorEntry(val beName: String, val color: String)
    private data class ConfigData(
        val enabledBeNames: List<String> = emptyList(),
        val tracerBeNames:  List<String> = emptyList(),
        val espColors:      List<ColorEntry> = emptyList(),
        val tracerColors:   List<ColorEntry> = emptyList()
    )

    private fun Int.toHexColor(): String = (toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
    private fun String.fromHexColor(): Int? = try { toLong(16).toInt() } catch (_: Exception) { null }

    fun setEspColor(beName: String, argb: Int) { espColors[beName] = argb; saveConfig() }
    fun setTracerColor(beName: String, argb: Int) { tracerColors[beName] = argb; saveConfig() }

    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kittycat-bestiary-config").apply { isDaemon = true }
    }
    @Volatile private var savePending = false
    private var saveAt = 0L
    private var pendingWrite: Future<*>? = null
    private var mobsByName: Map<String, List<Mob>> = emptyMap()
    private var texturedMobs: List<Mob> = emptyList()
    private var tracerNames: Set<String> = emptySet()

    private fun rebuildMatchers() {
        mobsByName = enabledMobs.groupBy { it.name }
        texturedMobs = enabledMobs.filter { it.texture != null }
        tracerNames = tracerMobs.mapTo(mutableSetOf()) { it.beName }
    }

    fun saveConfig() {
        savePending = true
        saveAt = System.nanoTime() + 500_000_000L
    }

    private fun writeConfig() {
        val data = ConfigData(
            enabledBeNames = enabledMobs.map { it.beName }.distinct(),
            tracerBeNames  = tracerMobs.map { it.beName }.distinct(),
            espColors      = espColors.map { (k, v) -> ColorEntry(k, v.toHexColor()) },
            tracerColors   = tracerColors.map { (k, v) -> ColorEntry(k, v.toHexColor()) }
        )
        savePending = false
        pendingWrite = writer.submit {
            try {
                Files.createDirectories(configPath.parent)
                Files.newBufferedWriter(configPath).use { gson.toJson(data, it) }
            } catch (e: Exception) {
                savePending = true
                e.printStackTrace()
            }
        }
    }

    private fun loadConfig() {
        if (!configPath.exists()) return
        val data: ConfigData = Files.newBufferedReader(configPath).use { gson.fromJson(it, ConfigData::class.java) } ?: return
        enabledMobs.clear();  enabledMobs.addAll(allMobs.filter { it.beName in data.enabledBeNames })
        tracerMobs.clear();   tracerMobs.addAll(allMobs.filter { it.beName in data.tracerBeNames })
        espColors.clear();    data.espColors.forEach { e -> e.color.fromHexColor()?.let { espColors[e.beName] = it } }
        tracerColors.clear(); data.tracerColors.forEach { e -> e.color.fromHexColor()?.let { tracerColors[e.beName] = it } }
    }

    fun toggleEsp(beName: String) {
        if (enabledMobs.any { it.beName == beName }) enabledMobs.removeAll { it.beName == beName }
        else enabledMobs.addAll(allMobs.filter { it.beName == beName })
        rebuildMatchers()
        saveConfig()
    }

    fun toggleTracer(beName: String) {
        if (tracerMobs.any { it.beName == beName }) tracerMobs.removeAll { it.beName == beName }
        else tracerMobs.addAll(allMobs.filter { it.beName == beName })
        rebuildMatchers()
        saveConfig()
    }

    fun register() {
        loadConfig()
        rebuildMatchers()
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            pendingWrite?.get()
            if (savePending) {
                writeConfig()
                pendingWrite?.get()
            }
            writer.shutdown()
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (savePending && System.nanoTime() >= saveAt && pendingWrite?.isDone != false) writeConfig()
            if (openGui) { mc.gui.setScreen(BestiaryESPScreen(mc.gui.screen())); openGui = false }
            espEntities.clear(); tracerEntities.clear()
            if (!enabled) return@register

            mc.level?.entitiesForRendering()?.forEach { entity ->
                if (!entity.isAlive || entity !is LivingEntity) return@forEach
                val texture = if (texturedMobs.isEmpty()) null else CustomESP.getEntityTextureString(entity)
                val matched = texturedMobs.firstOrNull { texture?.contains(it.texture!!) == true }
                    ?: mobsByName[entity.name.string]?.firstOrNull {
                        it.maxHealth.contains(entity.getAttributeBaseValue(Attributes.MAX_HEALTH).toFloat())
                    }
                    ?: return@forEach
                espEntities.add(entity to matched.beName)
                if (matched.beName in tracerNames) tracerEntities.add(entity to matched.beName)
            }
        }

        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            ctx.renderBoxesBounds(espEntities.map { (entity, beName) ->
                BoxRender(entity.boundingBox, Color(espColors.getOrDefault(beName, 0xFFFFFFFF.toInt()), true))
            })
            ctx.renderTracers(tracerEntities.map { (entity, beName) ->
                TracerRender(
                    entity.position().add(0.0, entity.bbHeight / 2.0, 0.0),
                    Color(tracerColors.getOrDefault(beName, 0xFFFFFFFF.toInt()), true),
                    3.0f
                )
            })
        }
    }

}
