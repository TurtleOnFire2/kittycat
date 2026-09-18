package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.cheat
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.isEtherwarpItem
import kitty.cat.utils.uuid
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult

object Fixes : Feature("Fixes", "", Categories.Category.KUUDRA){
    val hollowFix = booleanSetting("Hollow wand fix", false)
    val cancelPlacingConduit = booleanSetting("Cancel placing conduit", false)
    val fixSkillIssue = booleanSetting("Cancel teleporting into lava. (Disable for stun").cheat()
    val noSkyblockMenu = booleanSetting("Cancel open skyblock menu", false)
    val clickThroughGiants = booleanSetting("Click through giants", false).cheat()
    val clickThroughEther = booleanSetting("Click through ether", false).cheat()

    fun cancelClick(): Boolean {
        if (!enabled) return false

        val player = mc.player ?: return false

        if (noSkyblockMenu.value) {
            if (player.inventory.selectedSlot == 8) return true
        }

        if (!fixSkillIssue.value) return false

        val id = player.mainHandItem.uuid()
        val isEtherwarp = id == "ETHERWARP_CONDUIT" ||
                (id == "ASPECT_OF_THE_VOID" && player.isCrouching)

        if (!isEtherwarp) return false

        val level = player.level()
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(60.0))
        val hit = level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                player
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return false

        return level.getFluidState(hit.blockPos).`is`(FluidTags.LAVA)
    }

    fun cancelPlacement(player: Player): Boolean {
        val uuid = player.mainHandItem.uuid()

        if (uuid != "ETHERWARP_CONDUIT") return false

        return (enabled && cancelPlacingConduit.value)
    }

    fun clickThrough(): Boolean {
        val uuid = mc.player?.mainHandItem?.uuid()

        if (uuid != "HOLLOW_WAND") return false

        return (enabled && hollowFix.value)
    }

    fun clickThroughEther(): Boolean {
        return (enabled && clickThroughEther.value && mc.player?.mainHandItem?.isEtherwarpItem() == true && kuudra())
    }

    fun ignoreGiant(): Boolean {
        return enabled && clickThroughGiants.value
    }
}
