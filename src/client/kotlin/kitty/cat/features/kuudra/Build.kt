package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.drawLine
import kitty.cat.render.world.drawFilledPolygon
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.kuudra
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
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

    fun register() {
        LevelRenderEvents.END_MAIN.register { context ->
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
                    context.drawLine(bottom[index], bottom[next], pileOutlineColor, 4f, true)
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            updateProgress()
            updateStunSound()
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
