package kitty.cat.features.misc

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.utils.aabb
import kitty.cat.utils.flatten
import kitty.cat.utils.setAlpha
import kitty.cat.utils.KuudraUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.animal.armadillo.Armadillo
import net.minecraft.world.entity.animal.dolphin.Dolphin
import net.minecraft.world.entity.animal.fish.TropicalFish
import net.minecraft.world.entity.animal.fox.Fox
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.animal.golem.SnowGolem
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.entity.animal.polarbear.PolarBear
import net.minecraft.world.entity.animal.sniffer.Sniffer
import net.minecraft.world.entity.animal.squid.GlowSquid
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.creaking.Creaking
import net.minecraft.world.entity.monster.spider.CaveSpider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import java.awt.Color

object Safari : Feature("Safari", "", Categories.Category.MISC) {
    private val boxes = mutableListOf<BoxRender>()
    val floorDropEsp = booleanSetting("Floor drop esp", false)
    val highlightColor = colorSetting("Color")
    val flitter = booleanSetting("Flitter", false)
    val flitterColor = colorSetting("Flitter color")
    val chuckwalla = booleanSetting("Chuckwalla", false)
    val chuckwallaColor = colorSetting("Chuckwalla color")
    val rockmite = booleanSetting("Rockmite", false)
    val rockmiteColor = colorSetting("Rockmite color")
    val mantisShrimp = booleanSetting("Mantis Shrimp", false)
    val mantisShrimpColor = colorSetting("Mantis Shrimp color")
    val troodon = booleanSetting("Troodon", false)
    val troodonColor = colorSetting("Troodon color")
    val cavernfish = booleanSetting("Cavernfish", false)
    val cavernfishColor = colorSetting("Cavernfish color")
    val tepid = booleanSetting("Tepid", false)
    val tepidColor = colorSetting("Tepid color")
    val shyworm = booleanSetting("Shyworm", false)
    val shywormColor = colorSetting("Shyworm color")
    val driftling = booleanSetting("Driftling", false)
    val driftlingColor = colorSetting("Driftling color")
    val scrappy = booleanSetting("Scrappy", false)
    val scrappyColor = colorSetting("Scrappy color")
    val snoozle = booleanSetting("Snoozle", false)
    val snoozleColor = colorSetting("Snoozle color")
    val foxtrot = booleanSetting("Foxtrot", false)
    val foxtrotColor = colorSetting("Foxtrot color")
    val treefrog = booleanSetting("Treefrog", false)
    val treefrogColor = colorSetting("Treefrog color")
    val woodchucker = booleanSetting("Woodchucker", false)
    val woodchuckerColor = colorSetting("Woodchucker color")
    val fluffling = booleanSetting("Fluffling", false)
    val flufflingColor = colorSetting("Fluffling color")
    val hideOnFloor = booleanSetting("Hide on floor", false)
    val hideOnFloorColor = colorSetting("Hide on floor color")
    val areita = booleanSetting("Areita", false)
    val areitaColor = colorSetting("Areita color")
    val bloodbat = booleanSetting("Bloodbat", false)
    val bloodbatColor = colorSetting("Bloodbat color")
    val duplico = booleanSetting("Duplico", false)
    val duplicoColor = colorSetting("Duplico color")
    val litterbug = booleanSetting("Litterbug", false)
    val litterbugColor = colorSetting("Litterbug color")
    val solsnatcher = booleanSetting("Solsnatcher", false)
    val solsnatcherColor = colorSetting("Solsnatcher color")
    val hideOnWall = booleanSetting("Hide on wall", false)
    val hideOnWallColor = colorSetting("Hide on wall color")
    val hideyho = booleanSetting("Hideyho", false)
    val hideyhoColor = colorSetting("Hideyho color")
    val strongarm = booleanSetting("Strongarm", false)
    val strongarmColor = colorSetting("Strongarm color")
    val polaris = booleanSetting("Polaris", false)
    val polarisColor = colorSetting("Polaris color")
    val shuddersquid = booleanSetting("Shuddersquid", false)
    val shuddersquidColor = colorSetting("Shuddersquid color")
    val nozzlenose = booleanSetting("Nozzlenose", false)
    val nozzlenoseColor = colorSetting("Nozzlenose color")

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            boxes.clear()

            KuudraUtils.entitiesForRendering().forEach { e ->
                if (floorDropEsp.value && e is Display.ItemDisplay && e.itemStack.item == Items.STRING) {
                    boxes += BoxRender(
                        e.blockPosition().aabb().move(0.0, 1.0, 0.0).flatten(0.1),
                        highlightColor.color.setAlpha(0),
                        highlightColor.color
                    )
                }

                when (e) {
                    is Display.ItemDisplay -> {
                        when (e.itemStack.itemName.string) {
                            "f4c16ce02815426f's Head" -> { if (flitter.value) render(ctx, e, flitterColor.color) } //Flitter
                            "32fe7a535ee8480e' Head" -> { if (chuckwalla.value) render(ctx, e, chuckwallaColor.color) } //Chuckwalla
                            "Player Head" -> { if (rockmite.value) render(ctx, e, rockmiteColor.color, 0.2) } //Rockmite
                            "48e0d28bb1b54d4a's Head" -> { if (mantisShrimp.value) render(ctx, e, mantisShrimpColor.color, 0.2) } //Mantis Shrimp
                            "ef2580172a474a1b's Head" -> { if (mantisShrimp.value) render(ctx, e, mantisShrimpColor.color, 0.2) } //Mantis Shrimp
                            "9be41fba60274de3's Head" -> { if (troodon.value) render(ctx, e, troodonColor.color) } //Troodon
                            in listOf("Bookshelf", "Cherry Wood", "Deepslate") -> {
                                if (duplico.value) render(ctx, e, duplicoColor.color, 0.5)
                            }
                            else -> {}
                        }
                    }
                    is TropicalFish -> {
                        when (e.baseColor) {
                            DyeColor.GRAY -> { if (cavernfish.value) render(ctx, e, cavernfishColor.color) } //Cavernfish
                            DyeColor.WHITE -> { if (tepid.value) render(ctx, e, tepidColor.color) } //Tepid
                            else -> {}
                        }
                    }
                    is Shulker -> {
                        when (e.color) {
                            DyeColor.GREEN -> { if (hideOnFloor.value) render(ctx, e, hideOnFloorColor.color) }
                            DyeColor.PURPLE -> { if (hideOnWall.value) render(ctx, e, hideOnWallColor.color) }
                            else -> {}
                        }

                    }

                    is Zombie -> { if (shyworm.value) render(ctx, e, shywormColor.color) } //Shyworm
                    is Silverfish -> { if (driftling.value && e.inBlockState.block != Blocks.BARRIER) render(ctx, e, driftlingColor.color) } //Flitter
                    is Armadillo -> { if (scrappy.value) render(ctx, e, scrappyColor.color) } //Driftling
                    is Sniffer -> { if (snoozle.value) render(ctx, e, snoozleColor.color) } //Snoozle

                    is Fox -> { if (foxtrot.value) render(ctx, e, foxtrotColor.color) } //Foxtrot
                    is Frog -> { if (treefrog.value) render(ctx, e, treefrogColor.color) } //Treefrog
                    is Creaking -> { if (woodchucker.value) render(ctx, e, woodchuckerColor.color) } //Woodchucker
                    is Panda -> { if (fluffling.value) render(ctx, e, flufflingColor.color) } //Fluffling

                    is CaveSpider -> { if (areita.value) render(ctx, e, areitaColor.color) } //Areita
                    is Bat -> { if (bloodbat.value) render(ctx, e, bloodbatColor.color) } //Bloodbat
                    is Endermite -> { if (litterbug.value) render(ctx, e, litterbugColor.color) } //Litterbug
                    is Phantom -> { if (solsnatcher.value) render(ctx, e, solsnatcherColor.color) } //Solsnatcher
                    is AbstractClientPlayer -> {
                        if (!e.name.string.contains("Hidey")) return@forEach
                        if (hideyho.value) render(ctx, e, hideyhoColor.color)
                    } //Hideyho

                    is SnowGolem -> { if (strongarm.value) render(ctx, e, strongarmColor.color) } //Strongarm
                    is PolarBear -> { if (polaris.value) render(ctx, e, polarisColor.color) } //Polaris
                    is GlowSquid -> { if (shuddersquid.value) render(ctx, e, shuddersquidColor.color) } //Shuddersquid
                    is Dolphin -> { if (nozzlenose.value) render(ctx, e, nozzlenoseColor.color) } //Nozzlenose
                }
            }
            ctx.renderBoxesBounds(boxes)
        }
    }

    fun render(@Suppress("UNUSED_PARAMETER") ctx: LevelRenderContext, entity: Entity, color: Color, customSize: Double = 0.0) {
        boxes += BoxRender(
            entity.boundingBox.inflate(customSize).move(0.0, customSize, 0.0),
            color,
            color.setAlpha(128)
        )
    }
}
