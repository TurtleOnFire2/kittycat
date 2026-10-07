package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.huds.CratePriorityPreviewHud
import kitty.cat.features.huds.CratePriorityWorldPreview
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import net.minecraft.core.BlockPos
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Locale
import kotlin.math.floor

object CratePriority: Feature("Crate Priority", "", Categories.Category.KUUDRA) {
    val destinationPreview = booleanSetting("Destination preview", true)
    val worldPreview = booleanSetting("3D destination preview", false)
    //Odin
    private val partyRegex =
        Regex("^Party > (?:\\[[^]]*])? ?\\w{1,16}: No (Triangle|X|Equals|Slash|xCannon|X Cannon|Square|Shop)!$")
    private val coordinateRegex = Regex(
        "(X Cannon|xCannon|Triangle|Equals|Slash|Square|Shop|X)\\s+x:\\s*([+-]?\\d+(?:\\.\\d+)?),\\s*y:\\s*([+-]?\\d+(?:\\.\\d+)?),\\s*z:\\s*([+-]?\\d+(?:\\.\\d+)?)",
        RegexOption.IGNORE_CASE
    )
    private val reportedCrates = mutableMapOf<Crate, Vec3>()
    private val sentSecondCrates = mutableSetOf<Crate>()
    private var secondScanTick = 0

    fun reportedPosition(crate: Crate): Vec3? = reportedCrates[crate]

    data class PreviewSupply(val position: Vec3, val fromChat: Boolean)

    fun previewSupply(crate: Crate): PreviewSupply? {
        val radius = when (crate) {
            Crate.xCannon -> 16.0
            Crate.Square -> 20.0
            Crate.Shop -> 18.0
            else -> return null
        }
        val giant = mc.level?.entitiesForRendering()?.filterIsInstance<Giant>()
            ?.minByOrNull { crate.pos.distToCenterSqr(Vec3(it.x, 76.0, it.z)) }
            ?.takeIf { crate.pos.distToCenterSqr(Vec3(it.x, 76.0, it.z)) < radius * radius }
        if (giant != null) return PreviewSupply(Supplies.supplyPosition(giant), false)
        return reportedCrates[crate]?.let { PreviewSupply(it, true) }
    }

    var currentPre = Crate.NONE
    var missing = Crate.NONE
    var ownPreMissing = false
        private set

    private var titleText: String? = null
    private var titleStartedAt = 0L

    fun register() {
        CratePriorityPreviewHud.register()
        CratePriorityWorldPreview.register()
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kittycat", "crate_priority_title")) { context, _ ->
            val text = titleText ?: return@addLast
            if (!enabled || mc.player == null) return@addLast
            val elapsed = (System.nanoTime() - titleStartedAt) / 1_000_000L
            if (elapsed >= 2750L) {
                titleText = null
                return@addLast
            }
            val opacity = when {
                elapsed < 250L -> elapsed / 250.0
                elapsed < 2250L -> 1.0
                else -> (2750L - elapsed) / 500.0
            }
            val alpha = (opacity * 255).toInt().coerceIn(0, 255)
            if (alpha < 4) return@addLast
            val pose = context.pose()
            pose.pushMatrix()
            pose.translate(context.guiWidth() / 2f, context.guiHeight() / 2f - 30f)
            pose.scale(4f)
            context.text(mc.font, text, -mc.font.width(text) / 2, -mc.font.lineHeight / 2, (alpha shl 24) or 0xFFFFFF)
            pose.popMatrix()
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            titleText = null
            ownPreMissing = false
            currentPre = Crate.NONE
            missing = Crate.NONE
            reportedCrates.clear()
            sentSecondCrates.clear()
            secondScanTick = 0
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled || !kuudra() || !supplies() || ++secondScanTick % 5 != 0) return@register
            mc.level?.entitiesForRendering()?.filterIsInstance<Giant>()?.forEach { giant ->
                sendSecondCrateLocation(giant, Vec3(giant.x, 76.0, giant.z))
            }
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !kuudra()) return@register
            reportedCrates.forEach { (crate, pos) ->
                ctx.renderBeaconBeam(pos, Color.CYAN)
                ctx.renderString(crate.name, pos.add(0.0, 2.0, 0.0), Color.CYAN, 2f)
            }
        }
    }

    override fun onDisable() {
        titleText = null
    }

    //Crate priority detection HEAVILY inspired from Odin
    fun handleChat(unformatted: String) {
        if (!enabled) return

        if (kuudra()) coordinateRegex.find(unformatted)?.let { match ->
            val crateName = match.groupValues[1].replace(" ", "")
            val crate = Crate.entries.firstOrNull { it.name.equals(crateName, ignoreCase = true) }
            val xyz = (2..4).map { match.groupValues[it].toDoubleOrNull() }
            if (crate != null && crate != Crate.NONE && xyz.all { it != null && it.isFinite() }) {
                reportedCrates[crate] = Vec3(xyz[0]!!, xyz[1]!!, xyz[2]!!)
            }
        }

        if (unformatted.contains("[NPC] Elle: Head over to the main platform, I will join you when I get a bite!")) {
            val pos = mc.player?.position() ?: return
            ownPreMissing = false

            currentPre = when {
                Crate.Triangle.pos.distToCenterSqr(pos) < 15.0 * 15.0 -> Crate.Triangle
                Crate.X.pos.distToCenterSqr(pos) < 30.0 * 30.0 -> Crate.X
                Crate.Equals.pos.distToCenterSqr(pos) < 15.0 * 15.0 -> Crate.Equals
                Crate.Slash.pos.distToCenterSqr(pos) < 15.0 * 15.0 -> Crate.Slash
                else -> Crate.NONE
            }

            Chat.send(if (currentPre == Crate.NONE) "Hurry up..." else "Pre: ${currentPre.name}")
        }

        if (unformatted.contains("[NPC] Elle: Not again!")) {
            var pre = false
            var second = false

            mc.level?.entitiesForRendering()?.filterIsInstance<Giant>()?.forEach  { giant ->
                val loc = Vec3(giant.x, 76.0, giant.z)

                sendSecondCrateLocation(giant, loc)

                if (currentPre.pos.distToCenterSqr(loc) < 18 * 18) pre = true
                when (currentPre) {
                    Crate.Triangle -> {
                        if (Crate.Shop.pos.distToCenterSqr(loc) < 18 * 18) {
                            second = true
                        }
                    }
                    Crate.X -> {
                        if (Crate.xCannon.pos.distToCenterSqr(loc) < 16 * 16) {
                            second = true
                        }
                    }
                    Crate.Slash -> {
                        if (Crate.Square.pos.distToCenterSqr(loc) < 20 * 20) {
                            second = true
                        }
                    }
                    else -> {}
                }
            }

            if (currentPre == Crate.NONE) return

            if (!pre) {
                ownPreMissing = true
                AutoWarp.onMissingPre(currentPre)
                mc.connection?.sendCommand("pc No ${currentPre.name}!")
            }
            if (!second) {
                val msg = when (currentPre) {
                    Crate.Triangle -> { "pc No Shop!" }
                    Crate.X -> { "pc No xCannon!" }
                    Crate.Slash -> { "pc No Square!" }
                    else -> return
                }
                mc.connection?.sendCommand(msg)
            }
        }

        partyRegex.matchEntire(unformatted)?.let { match ->
            val crateName = match.groupValues[1].replace(" ", "")

            missing = Crate.entries.firstOrNull {
                it.name.equals(crateName, ignoreCase = true)
            } ?: Crate.NONE

            if (missing != Crate.NONE && missing == currentPre) ownPreMissing = true
            AutoWarp.onMissingPre(missing)
            missing = getSecond(missing)
            titleText = missing.name
            titleStartedAt = System.nanoTime()
        }
    }

    private fun sendSecondCrateLocation(giant: Giant, giantPos: Vec3) {
        val crate = listOf(Crate.Shop, Crate.xCannon, Crate.Square).firstOrNull {
            val radius = if (it == Crate.xCannon) 16.0 else if (it == Crate.Square) 20.0 else 18.0
            it.pos.distToCenterSqr(giantPos) < radius * radius
        } ?: return
        if (crate in sentSecondCrates) return
        val connection = mc.connection ?: return
        val supply = Supplies.supplyPosition(giant)
        val pos = Vec3(supply.x, floor(supply.y), supply.z)
        val name = if (crate == Crate.xCannon) "X Cannon" else crate.name
        val message = String.format(Locale.ROOT, "%s x: %.2f, y: %.0f, z: %.2f", name, pos.x, pos.y, pos.z)
        connection.sendCommand("pc $message")
        reportedCrates[crate] = pos
        sentSecondCrates.add(crate)
    }

    private fun getSecond(missing: Crate): Crate {
        return when (missing) {
            Crate.Triangle -> when (currentPre) {
                Crate.Triangle -> Crate.Shop
                Crate.X -> Crate.xCannon
                Crate.Slash, Crate.Equals -> Crate.Square
                else -> Crate.NONE
            }

            Crate.X -> when (currentPre) {
                Crate.Triangle -> Crate.xCannon
                Crate.X -> Crate.Shop
                Crate.Slash, Crate.Equals -> Crate.Square
                else -> Crate.NONE
            }

            Crate.Slash -> when (currentPre) {
                Crate.Triangle -> Crate.Square
                Crate.X -> Crate.xCannon
                Crate.Slash -> Crate.Shop
                Crate.Equals -> Crate.Square
                else -> Crate.NONE
            }

            Crate.Equals -> when (currentPre) {
                Crate.Triangle -> Crate.Square
                Crate.X -> Crate.xCannon
                Crate.Slash -> Crate.Square
                Crate.Equals -> Crate.Shop
                else -> Crate.NONE
            }

            Crate.xCannon -> when (currentPre) {
                Crate.Triangle, Crate.Equals -> Crate.Shop
                Crate.X, Crate.Slash -> Crate.Square
                else -> Crate.NONE
            }

            Crate.Shop -> when (currentPre) {
                Crate.Triangle, Crate.X -> Crate.xCannon
                Crate.Slash, Crate.Equals -> Crate.Square
                else -> Crate.NONE
            }

            Crate.Square -> when (currentPre) {
                Crate.Triangle, Crate.Equals -> Crate.Shop
                Crate.X, Crate.Slash -> Crate.xCannon
                else -> Crate.NONE
            }

            else -> { Crate.NONE }
        }
    }

}

enum class Crate(val second: Boolean, val pos: BlockPos, val ether: BlockPos?) {
    NONE(false, BlockPos(0, 0, 0), null),
    Triangle(false, BlockPos(-67, 77, -122), null),
    X(false, BlockPos(-142, 77, -151), null),
    Slash(false, BlockPos(-113, 77, -68), null),
    Equals(false, BlockPos(-65, 76, -87), null),
    xCannon(true, BlockPos(-143, 76, -125), BlockPos(-128, 78, -118)),
    Shop(true, BlockPos(-81, 76, -143), BlockPos(-75, 78, -136)),
    Square(true, BlockPos(-143, 76, -80), BlockPos(-140, 78, -90))
}
