package kitty.cat.features.visual

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kitty.cat.KittycatClient.mc
import kitty.cat.gui.bestiaryesp.BestiaryESPScreen
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Mob
import kitty.cat.utils.allMobs
import me.cheater.legitcatmod.utils.drawFilled
import me.cheater.legitcatmod.utils.drawLineFromCursor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import java.awt.Color
import java.nio.file.Files
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

    fun saveConfig() {
        if (!configPath.exists()) { configPath.createParentDirectories(); configPath.createFile() }
        val data = ConfigData(
            enabledBeNames = enabledMobs.map { it.beName }.distinct(),
            tracerBeNames  = tracerMobs.map { it.beName }.distinct(),
            espColors      = espColors.map { (k, v) -> ColorEntry(k, v.toHexColor()) },
            tracerColors   = tracerColors.map { (k, v) -> ColorEntry(k, v.toHexColor()) }
        )
        Files.newBufferedWriter(configPath).use { gson.toJson(data, it) }
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
        saveConfig()
    }

    fun toggleTracer(beName: String) {
        if (tracerMobs.any { it.beName == beName }) tracerMobs.removeAll { it.beName == beName }
        else tracerMobs.addAll(allMobs.filter { it.beName == beName })
        saveConfig()
    }

    fun register() {
        loadConfig()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openGui) { mc.setScreen(BestiaryESPScreen(mc.screen)); openGui = false }
            espEntities.clear(); tracerEntities.clear()
            if (!enabled) return@register

            client.level?.entitiesForRendering()?.forEach { entity ->
                if (!entity.isAlive || entity !is LivingEntity) return@forEach
                val matched = enabledMobs.firstOrNull { entityMatchesMob(entity, it) } ?: return@forEach
                espEntities.add(entity to matched.beName)
                if (tracerMobs.any { it.beName == matched.beName }) tracerEntities.add(entity to matched.beName)
            }
        }

        WorldRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            espEntities.forEach { (entity, beName) ->
                ctx.drawFilled(entity.boundingBox, Color(espColors.getOrDefault(beName, 0xFFFFFFFF.toInt()), true), false)
            }
            tracerEntities.forEach { (entity, beName) ->
                ctx.drawLineFromCursor(
                    entity.position().add(0.0, entity.bbHeight / 2.0, 0.0),
                    Color(tracerColors.getOrDefault(beName, 0xFFFFFFFF.toInt()), true),
                    3.0f
                )
            }
        }
    }

    private fun entityMatchesMob(entity: LivingEntity, mob: Mob): Boolean {
        if (mob.texture != null && CustomESP.getEntityTextureString(entity)?.contains(mob.texture) == true) return true
        if (entity.name.string == mob.name && mob.maxHealth.contains(entity.getAttributeBaseValue(Attributes.MAX_HEALTH).toFloat())) return true
        return false
    }
}
