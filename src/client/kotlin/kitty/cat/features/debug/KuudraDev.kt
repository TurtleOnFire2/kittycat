package kitty.cat.features.debug

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories

object KuudraDev : Feature("Kuudra Dev", "",Categories.Category.MISC) {
    val forceKuudra = booleanSetting("Force kuudra", false)
    val forceSupplies = booleanSetting("Force supplies", false)
    val forceBuild = booleanSetting("Force build", false)
    val forceStun = booleanSetting("Force stun", false)
    val forceDps = booleanSetting("Force dps", false)
}