package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.minecraft.core.BlockPos
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.phys.Vec3

object CratePriority: Feature("Crate Priority", "", Categories.Category.KUUDRA) {
    //Odin
    private val partyRegex =
        Regex("^Party > (?:\\[[^]]*])? ?\\w{1,16}: No (Triangle|X|Equals|Slash|xCannon|X Cannon|Square|Shop)!$")

    var currentPre = Crate.NONE
    var missing = Crate.NONE

    private var titleText: String? = null
    private var titleStartedAt = 0L

    fun register() {
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
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> titleText = null }
    }

    override fun onDisable() {
        titleText = null
    }

    //Crate priority detection HEAVILY inspired from Odin
    fun handleChat(unformatted: String) {
        if (!enabled) return

        if (unformatted.contains("[NPC] Elle: Head over to the main platform, I will join you when I get a bite!")) {
            val pos = mc.player?.position() ?: return

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

            if (!pre) mc.connection?.sendCommand("pc No ${currentPre.name}!")
            if (!second) {
                val msg = when (currentPre) {
                    Crate.Triangle -> { "pc No Shop!" }
                    Crate.X -> { "pc No xCannon!" }
                    Crate.Slash -> { "pc No Square!" }
                    else -> return
                }
                mc.connection?.sendCommand("pc $msg")
            }
        }

        partyRegex.matchEntire(unformatted)?.let { match ->
            val crateName = match.groupValues[1].replace(" ", "")

            missing = Crate.entries.firstOrNull {
                it.name.equals(crateName, ignoreCase = true)
            } ?: Crate.NONE

            val next = getSecond(missing)
            titleText = next.name
            titleStartedAt = System.nanoTime()
        }
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
