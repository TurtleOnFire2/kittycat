package kitty.cat.features.huds

import kitty.cat.KittycatClient.mc
import kitty.cat.features.misc.BestiaryHud
import kitty.cat.gui.Hud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket

object BestiaryHud: Hud.Component("BestiaryHud", 0.0, 0.0, 1f, staticRenderConditions = mutableListOf(Hud.Condition.Always)) {

    val bestiaryRegex = Regex("""(.+) (\d+): ([\d,]+)/([\d,]+)""")
    val bestiaries = mutableMapOf<String, Bestiary>()

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            if (BestiaryHud.resetOnWorldChange.value) bestiaries.clear()
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            bestiaries.values.forEach { b ->
                if (b.active) b.ticksPassed++
                b.ticksWithoutChange++
                if (b.ticksWithoutChange > BestiaryHud.timeout.value * 1200) b.active = false
            }
        }
    }

    fun handleTabChange(packet: ClientboundPlayerInfoUpdatePacket) {
        val entries = packet.entries().mapNotNull { it.displayName?.string }

        entries.forEach {
            val match = bestiaryRegex.find(it) ?: return@forEach
            val name = match.groupValues[1]
            val level = match.groupValues[2].toInt()
            val progress = match.groupValues[3]
            val next = match.groupValues[4]
            val progressInt = progress.replace(",", "").toInt()
            val percentage = "%.2f".format(progressInt / next.replace(",", "").toDouble() * 100)

            val existing = bestiaries[name]
            val sessionStart = existing?.sessionStart ?: progressInt

            if (existing == null) {
                bestiaries[name] = Bestiary(name, level, progress, next, percentage, sessionStart, progressInt, progressInt)
            } else {
                if (progressInt != existing.lastProgress) {
                    existing.active = true
                    existing.ticksWithoutChange = 0
                    if (existing.ticksPassed == 0L) {
                        existing.progressAtTrackingStart = progressInt
                    }
                }
                existing.level = level
                existing.progress = progress
                existing.next = next
                existing.percentage = percentage
                existing.lastProgress = progressInt
                existing.sessionStart = sessionStart
            }
        }
    }

    fun resetSession() {
        bestiaries.values.forEach { b ->
            b.sessionStart = b.progress.replace(",", "").toInt()
            b.ticksPassed = 0
            b.ticksWithoutChange = 0
            b.active = false
        }
    }

    override fun render(context: GuiGraphicsExtractor) {
        if (!BestiaryHud.enabled) return
        var y = 0
        bestiaries.values.forEach { b ->
            val progressInt = b.progress.replace(",", "").toInt()
            //val gain = progressInt - b.sessionStart

            val perHour = if (b.ticksPassed > 0) {
                val elapsedHours = b.ticksPassed / (20.0 * 3600)
                val gained = progressInt - b.progressAtTrackingStart
                if (gained > 0) {
                    val rate = (gained / elapsedHours).toInt()
                    val rateFormatted = rate.toString().reversed().chunked(3).joinToString(",").reversed()
                    val remaining = b.next.replace(",", "").toInt() - progressInt
                    val etaHours = remaining / rate.toDouble()
                    val eta = when {
                        etaHours < 1.0 / 60 -> "${(etaHours * 3600).toInt()}s"
                        etaHours < 1.0 -> "${(etaHours * 60).toInt()}m"
                        else -> "${"%.1f".format(etaHours)}h"
                    }
                    " §6[$rateFormatted/h] §a... [ETA: $eta]"
                } else ""
            } else ""

            if (perHour.isEmpty() && BestiaryHud.showActiveOnly.value) return@forEach

            context.text(mc.font, "${b.name} ${b.level}: §b${b.progress}/${b.next} §7(${b.percentage}%)$perHour ", 0, y, -1)
            y += 10
        }
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "Golden Goblin 9: §b97/100 §7(97%) §6[15] §a1,200/h ", 0, 0, -1)
    }

    override fun bounds(): Pair<Double, Double> {
        return Pair(mc.font.width("Golden Goblin 9: §b97/100 §7(97%) §6[15] §a1,200/h").toDouble(), mc.font.lineHeight.toDouble())
    }
}

class Bestiary(
    val name: String,
    var level: Int,
    var progress: String,
    var next: String,
    var percentage: String,
    var sessionStart: Int,
    var progressAtTrackingStart: Int,
    var lastProgress: Int,
    var ticksPassed: Long = 0L,
    var ticksWithoutChange: Long = 0L,
    var active: Boolean = false
)