package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.phys.Vec3

object CratePriority: Feature("Crate Priority", "", Categories.Category.KUUDRA) {

    //Odin
    private val partyRegex =
        Regex("^Party > (?:\\[[^]]*])? ?\\w{1,16}: No (Triangle|X|Equals|Slash|xCannon|X Cannon|Square|Shop)!$")

    var currentPre = Crate.NONE
    var missing = Crate.NONE

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
                    Crate.Triangle -> { if (Crate.Shop.pos.distToCenterSqr(loc) < 18 * 18) second = true }
                    Crate.X -> { if (Crate.xCannon.pos.distToCenterSqr(loc) < 16 * 16) second = true }
                    Crate.Slash -> { if (Crate.Square.pos.distToCenterSqr(loc) < 20 * 20) second = true }
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
            val crateName = match.groupValues[1]

            missing = Crate.entries.firstOrNull {
                it.name.equals(crateName, ignoreCase = true)
            } ?: Crate.NONE

            val next = getSecond(missing)
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

enum class Crate(val second: Boolean, val pos: BlockPos) {
    NONE(false, BlockPos(0, 0, 0)),
    Triangle(false, BlockPos(-67, 77, -122)),
    X(false, BlockPos(-142, 77, -151)),
    Slash(false, BlockPos(-113, 77, -68)),
    Equals(false, BlockPos(-65, 76, -87)),
    xCannon(true, BlockPos(-143, 76, -125)),
    Shop(true, BlockPos(-81, 76, -143)),
    Square(true, BlockPos(-143, 76, -80))
}