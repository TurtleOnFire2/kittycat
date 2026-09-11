package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderLine
import kitty.cat.render.world.drawFilledPolygon
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.kuudra
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.PI

object Build : Feature("Build", "", Categories.Category.KUUDRA) {
    val progressHud = booleanSetting("Progress HUD", false)
    val progressHudOrder = selectorSetting(
        "Progress HUD order",
        listOf("Pile first", "Build first"),
        listOf("Pile first"),
        description = "The first percentage appears larger on top."
    )
    val hideUselessArmorStands = booleanSetting("Hide useless armor stands", false)
    val highlightPile = booleanSetting("Highlight pile", false)
    val replaceSounds = booleanSetting("Replace sounds", false)
    val replacementSound = registrySetting(
        "Replacement sound",
        "minecraft:block.note_block.pling",
        BuiltInRegistries.SOUND_EVENT,
        placeholder = "Search sounds..."
    )
    val playSound = actionSetting("Play sound", "Play the selected replacement sound once.") {
        BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(replacementSound.value))?.let { sound ->
            mc.soundManager.play(SimpleSoundInstance.forUI(sound, 1f))
        }
    }
    val announceFresh = booleanSetting("Announce fresh", false)
    val flowstate = booleanSetting("Flowstate", false, description = "Cyan/purple glow and outward speed streaks that fade as Fresh Tools expires.")
    val stunAlert = booleanSetting("Stun alert", false)
    val stunThreshold = numberSetting(
        "Stun alert threshold", min = 0.0, max = 100.0, defaultValue = 80.0,
        unit = "%", step = 1.0,
        description = "Show GO STUN! when build progress exceeds this percentage."
    )

    private val pileProgressRegex = Regex("PROGRESS: (?:(\\d+)%|COMPLETE)")
    private val buildRegex = Regex("Building Progress (\\d+)% \\((\\d+) Players Helping\\)") //Thank you, Odin

    private val pileOutline = run {
        val outer = 2.5
        val halfSide = outer * (sqrt(2.0) - 1.0)
        listOf(
            Vec3(-halfSide, 0.0, -outer), Vec3(halfSide, 0.0, -outer),
            Vec3(outer, 0.0, -halfSide), Vec3(outer, 0.0, halfSide),
            Vec3(halfSide, 0.0, outer), Vec3(-halfSide, 0.0, outer),
            Vec3(-outer, 0.0, halfSide), Vec3(-outer, 0.0, -halfSide)
        )
    }

    var buildProgress = 0
    var pileProgress = 0
    private var stunAlertPlayed = false
    private var nextStunSoundTicks = 0
    private var remainingStunSounds = 0
    var playingStunSound = false
    var freshTimeLeft = 0
    private var flowAnimation = 0.0
    private var lastFlowFrame = 0L

    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kittycat", "flowstate")) { context, _ ->
            renderFlowstate(context)
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> freshTimeLeft = 0 }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            if (!enabled || !build() || !highlightPile.value) return@register
            val stands = mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>() ?: return@register
            for (stand in stands) {
                val match = pileProgressRegex.matchEntire(stand.name.string) ?: continue
                val progress = if (match.value == "PROGRESS: COMPLETE") 100 else match.groupValues[1].toIntOrNull() ?: 0
                val pileOutlineColor = progressColor(progress, alpha = 50)
                val bottom = pileOutline.map { it.add(stand.x, 79.1, stand.z) }
                context.drawFilledPolygon(bottom, pileOutlineColor)
                for (index in bottom.indices) {
                    val next = (index + 1) % bottom.size
                    context.renderLine(bottom[index], bottom[next], pileOutlineColor, 4f, phase = false)
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (mc.level == null || mc.player == null) freshTimeLeft = 0
            updateProgress()
            updateStunSound()
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted == "Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!") {
            freshTimeLeft = 200
            if (enabled && announceFresh.value) mc.connection?.sendCommand("pc FRESH $pileProgress%")
        }
    }

    fun serverTick() {
        freshTimeLeft--
    }

    private fun renderFlowstate(context: GuiGraphicsExtractor) {
        if (!enabled || !flowstate.value || freshTimeLeft <= 0
            || mc.player == null || mc.level == null || mc.options.hideGui) return

        val width = context.guiWidth()
        val height = context.guiHeight()
        val depth = (minOf(width, height) * 0.22f).roundToInt().coerceAtLeast(1)
        val time = System.nanoTime() / 1_000_000_000.0
        val pulse = 0.85 + 0.15 * sin(time * 2.5)
        val fade = (freshTimeLeft / 200f).coerceIn(0f, 1f)
        val now = System.nanoTime()
        val delta = if (lastFlowFrame == 0L) 0.0 else ((now - lastFlowFrame) / 1_000_000_000.0).coerceIn(0.0, 0.05)
        lastFlowFrame = now
        flowAnimation += delta * (0.8 + 1.8 * fade)
        val blend = ((sin(time * 0.8) + 1.0) / 2.0).toFloat()
        for (inset in 0 until depth) {
            val strength = 1f - inset.toFloat() / depth
            val alpha = (150 * strength * strength * pulse * fade).roundToInt()
            if (alpha == 0) continue
            val color = Color((70 + 85 * blend).roundToInt(), (210 - 100 * blend).roundToInt(), 255, alpha).rgb
            context.fill(inset, inset, width - inset, inset + 1, color)
            context.fill(inset, height - inset - 1, width - inset, height - inset, color)
            context.fill(inset, inset + 1, inset + 1, height - inset - 1, color)
            context.fill(width - inset - 1, inset + 1, width - inset, height - inset - 1, color)
        }

        val centerX = width / 2.0
        val centerY = height / 2.0
        for (index in 0 until 32) {
            val angle = index * (2.0 * PI / 32) + 0.035 * sin(index * 7.0)
            val dx = cos(angle)
            val dy = sin(angle)
            val edge = minOf(centerX / abs(dx).coerceAtLeast(0.001), centerY / abs(dy).coerceAtLeast(0.001))
            val phase = (flowAnimation + index * 0.61803398875) % 1.0
            val head = 0.67 + phase * 0.44
            val length = 0.08 + 0.12 * fade
            val visibility = sin(phase * PI)
            val steps = (edge * length).roundToInt().coerceAtLeast(1)
            for (step in 0 until steps) {
                val taper = step.toDouble() / steps
                val radius = head - length + length * taper
                if (radius < 0.62 || radius > 1.0) continue
                val alpha = (210 * fade * visibility * taper).roundToInt()
                if (alpha <= 0) continue
                val x = (centerX + dx * edge * radius).roundToInt()
                val y = (centerY + dy * edge * radius).roundToInt()
                val color = if (index % 2 == 0) Color(115, 235, 255, alpha).rgb else Color(190, 145, 255, alpha).rgb
                context.fill(x, y, minOf(x + 2, width), minOf(y + 2, height), color)
            }
        }
    }

    fun progressColor(progress: Int, alpha: Int = 255): Color {
        val fraction = progress.coerceIn(0, 100) / 100f
        return Color(
            (255 + (144 - 255) * fraction).roundToInt(),
            (238 * fraction).roundToInt(),
            (144 * fraction).roundToInt(),
            alpha
        )
    }

    private fun updateProgress() {
        buildProgress = 0
        pileProgress = 0
        if (!enabled) return

        if ((progressHud.value || stunAlert.value) && build()) {
            val armorStands = mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>() ?: return

            for (armorStand in armorStands) {
                buildRegex.find(armorStand.name.string)?.let {
                    buildProgress = it.groupValues[1].toIntOrNull() ?: 0
                    continue
                }
            }

            val player = mc.player ?: return
            val closest = armorStands
                .filter { it.distanceToSqr(player) <= 36.0 && pileProgressRegex.matches(it.name.string) }
                .minByOrNull { it.distanceToSqr(player) } ?: return

            pileProgressRegex.find(closest.name.string)?.let {
                pileProgress = if (it.value == "PROGRESS: COMPLETE") 100 else it.groupValues[1].toIntOrNull() ?: 0
            }
        }
    }

    private fun updateStunSound() {
        if (!enabled || !stunAlert.value || !build() || mc.level == null || mc.player == null
            || buildProgress <= stunThreshold.value) {
            stunAlertPlayed = false
            nextStunSoundTicks = 0
            remainingStunSounds = 0
            return
        }
        if (remainingStunSounds > 0 && --nextStunSoundTicks == 0) {
            playStunSound()
            remainingStunSounds--
            if (remainingStunSounds > 0) nextStunSoundTicks = 4
        }
        if (!stunAlertPlayed && buildProgress > stunThreshold.value) {
            stunAlertPlayed = true
            playStunSound()
            remainingStunSounds = 2
            nextStunSoundTicks = 4
        }
    }

    private fun playStunSound() {
        playingStunSound = true
        try {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_PLACE, 1f))
        } finally {
            playingStunSound = false
        }
    }

    fun hideEntity(entity: Entity): Boolean {
        if (entity !is ArmorStand) return false

        if (entity.x in -75.0..-67.0 && entity.z in -104.0..-98.0) return false

        if (pileProgressRegex.matches(entity.name.string)) return false

        return (kuudra() && build() && hideUselessArmorStands.value)
    }
}
