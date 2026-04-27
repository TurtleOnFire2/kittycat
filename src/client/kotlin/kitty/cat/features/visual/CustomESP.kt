package kitty.cat.features.visual

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Chat
import kitty.cat.utils.name
import kitty.cat.utils.round
import me.cheater.legitcatmod.utils.drawFilled
import me.cheater.legitcatmod.utils.drawLineBox
import me.cheater.legitcatmod.utils.drawLineFromCursor
import me.cheater.legitcatmod.utils.drawString
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.nio.file.Files
import java.util.Locale.getDefault
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists

object CustomESP: Feature("Custom ESP", "/cesp", Categories.Category.VISUAL) {
    val color = colorSetting("Color")
    val debug = booleanSetting("Debug", false)
    val skipArmorStands = booleanSetting("Skip ArmorStands", false)

    private val configPath = FabricLoader.getInstance().configDir.resolve("kittycat/custom_esp.json")
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    var tracers = mutableListOf<Entity>()
    var entities = mutableListOf<Entity>()
    var tracerList = mutableListOf<String>()
    val entityList = mutableListOf<String>()

    fun register() {
        loadConfig()
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tracers.clear()
            entities.clear()

            if (!enabled) return@register

            client.level?.entitiesForRendering()?.forEach { e ->
                var entity = e
                val name = entity.name() ?: return@forEach

                if (!entityList.any { name.contains(it, true) } || !entity.isAlive) return@forEach

                if (entity is ArmorStand) {
                    client.level!!.getEntities(entity, entity.boundingBox.move(0.0, -1.0, 0.0)) {
                        val pos = it.position().round(1)
                        val entityPos = entity.position().round(1)
                        if (pos.x != entityPos.x || pos.z != entityPos.z) return@getEntities false
                        isValidEntity(it)
                    }.firstOrNull()?.let {
                        entity = it
                    }
                }

                if (tracerList.any { name.contains(it, ignoreCase = true) }) tracers.add(entity)

                entities.add(entity)
                tracers.removeIf { entity -> !entity.isAlive }
                entities.removeIf { entity -> !entity.isAlive }
            }
        }
        WorldRenderEvents.END_MAIN.register { ctx ->
            if (debug.value) {
                mc.level?.entitiesForRendering()?.forEach { e ->
                    if (e is ArmorStand && skipArmorStands.value || e == mc.player) return@forEach

                    val h = e.bbHeight

                    ctx.drawString(e.name.string, e.position().add(0.0, 1.4 + h, 0.0), -1)
                    ctx.drawString(e.position().toString(), e.position().add(0.0, 1.2 + h, 0.0), -1)
                    ctx.drawString(e.type.toString(), e.position().add(0.0, 1.0 + h, 0.0), -1)
                    ctx.drawLineBox(e.boundingBox, Color.WHITE, 3f, true)
                    if (e !is LivingEntity) return@forEach
                    ctx.drawString(e.getAttributeBaseValue(Attributes.MAX_HEALTH).toString(), e.position().add(0.0, 0.8 + h, 0.0), -1)
                    ctx.drawString( getEntityTextureString(e) ?: "", e.position().add(0.0, 0.6 + h, 0.0), -1)
                }
            }

            entities.forEach{
                ctx.drawFilled(it.boundingBox, color.color, false)
            }
            tracers.forEach{
                val height = it.boundingBox.ysize
                ctx.drawLineFromCursor(it.position().add(0.0, height / 2.0, 0.0), color.color, 3.0f)
            }
        }
    }

    private fun isValidEntity(entity: Entity): Boolean =
        when (entity) {
            is ArmorStand -> false
            is WitherBoss -> false
            is Player -> entity.uuid.version() == 2 && entity != Minecraft.getInstance().player
            else -> true
        }

    fun getEntityTextureString(entity: Entity): String? {
        val player = entity as? Player ?: return null

        val encoded = player.gameProfile.properties["textures"].firstOrNull()?.value
        if (encoded != null) {
            val json = String(java.util.Base64.getDecoder().decode(encoded))
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            return obj["textures"]?.asJsonObject
                ?.get("SKIN")?.asJsonObject
                ?.get("url")?.asString
        }

        return null
    }

    fun getAllTextureStrings(str: String) {
        mc.level?.entitiesForRendering()?.forEach { e ->
            val texture = getEntityTextureString(e) ?: return@forEach
            val name = e.name.string ?: return@forEach

            if (!name.contains(str)) return@forEach

            Chat.sendWithClickable(
                "${e.name.string}: ",
                Chat.Clickable(texture.replace("http://textures.minecraft.net/texture/", ""), ClickEvent.CopyToClipboard(texture))
                )
        }
    }

    fun getMobString(str: String) {
        mc.level?.entitiesForRendering()?.forEach { e ->
            if (e !is LivingEntity) return@forEach
            val name = e.name.string ?: return@forEach

            if (!name.contains(str)) return@forEach

            val texture = (getEntityTextureString(e) ?: "null").replace("http://textures.minecraft.net/texture/", "")
            val maxHealth = e.getAttributeBaseValue(Attributes.MAX_HEALTH).toString() ?: "null"
            val mobType = e.type.toString().replace("entity.minecraft.", "").uppercase(getDefault()) ?: "null"

            val string = "Mob(\"\", \"$name\", $texture, listOf(${maxHealth}f), EntityType.$mobType),"

            Chat.sendWithClickable(
                "${e.name.string}: ",
                Chat.Clickable(string, ClickEvent.CopyToClipboard(string))
            )
        }
    }

    private data class ConfigData(
        val entityList: List<String> = emptyList(),
        val tracerList: List<String> = emptyList()
    )

    fun saveConfig() {
        if (!configPath.exists()) {
            configPath.createParentDirectories()
            configPath.createFile()
        }

        val data = ConfigData(entityList.toList(), tracerList.toList())
        val writer = Files.newBufferedWriter(configPath)
        gson.toJson(data, writer)
        writer.close()
    }

    private fun loadConfig() {
        if (!configPath.exists()) return

        val reader = Files.newBufferedReader(CustomESP.configPath)
        val data: ConfigData = gson.fromJson(reader, ConfigData::class.java) ?: return
        reader.close()

        entityList.clear()
        entityList.addAll(data.entityList)
        tracerList.clear()
        tracerList.addAll(data.tracerList)
    }
}