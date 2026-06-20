package kitty.cat.utils

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType

data class Mob(val beName: String, val name: String, val texture: String?, val maxHealth: List<Float?>, val mobType: EntityType<*>?)

fun entityType(path: String): EntityType<*>? =
    BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(path))

val allMobs = listOf<Mob>(
    Mob("Crypt Ghoul","Zombie", null, listOf(2000f), entityType("zombie")),
    Mob("Golden Ghoul", "Zombie", null, listOf(45000f), entityType("zombie")),
    Mob("Graveyard Zombie", "Zombie", null, listOf(100f), entityType("zombie")),
    Mob("Old Wolf", "Wolf", null, listOf(15000f), entityType("wolf")),
    Mob("Shiny Pig", "Pig", null, listOf(null), entityType("pig")),
    Mob("Wolf", "Wolf", null, listOf(250f), entityType("wolf")),
    Mob("Zombie Villager", "Zombie Villager", null, listOf(120f), entityType("zombie_villager")),

    Mob("Chicken", "Chicken", null, listOf(20.0f), entityType("chicken")),
    Mob("Cow", "Cow", null, listOf(50.0f), entityType("cow")),
    Mob("Mushroom Cow", "Mooshroom", null, listOf(50.0f), entityType("mooshroom")),
    Mob("Pig", "Pig", null, listOf(50.0f), entityType("pig")),
    Mob("Rabbit", "Rabbit", null, listOf(130.0f), entityType("rabbit")),
    Mob("Sheep", "Sheep", null, listOf(50.0f), entityType("sheep")),

    Mob("Arachne's Keeper", "Spider", null, listOf(3000.0f), entityType("spider")), //Arachne Keeper
    Mob("Broodmother", "Spider", null, listOf(6000.0f), entityType("spider")), //Broodmother
    Mob("Dasher Spider", "Spider", null, listOf(170f, 210f, 900f, 1100f, 1400f), entityType("spider")), //Dasher Spider
    Mob("Flint Skeleton", "Skeleton", null, listOf(100.0f), entityType("skeleton")), //Flint Skeleton
    Mob("Rain Slime", "Slime", null, listOf(400.0f, 1500f, 1000f), entityType("slime")), //Rain Slime
    Mob("Silverfish", "Silverfish", null, listOf(50.0f), entityType("silverfish")),
    Mob("Spider Jockey", "Skeleton", null, listOf(220f, 280f, 1000.0f), entityType("skeleton")), //Spider Jockey
    Mob("Splitter Spider", "Spider", null, listOf(180f, 220f, 260f, 800f, 1100f, 1450f), entityType("spider")), //Splitter Spider
    Mob("Voracious Spider", "Spider", null, listOf(300f, 750f, 1150f, 1450f), entityType("spider")), //Voracious Spider
    Mob("Weaver Spider", "Spider", null, listOf(160f, 180f, 200f, 220f, 800f, 1200f, 1500f), entityType("spider")), //Weaver Spider

    Mob("Enderman", "Enderman", null, listOf(4500f, 6000f, 9000f), entityType("enderman")), //Enderman
    Mob("Obsidian Defender", "Wither Skeleton", null, listOf(10000.0f), entityType("wither_skeleton")), //Obsidian Defender
    Mob("Seer", "Skeleton", null, listOf(9500.0f), entityType("skeleton")), //Seer
    Mob("Voidling Extremist", "Enderman", null, listOf(8000000f), entityType("enderman")), //Voidling Extremist
    Mob("Voidling Fanatic", "Enderman", null, listOf(750000f), entityType("enderman")), //Voidling Fanatic
    Mob("Zealot", "Enderman", null, listOf(13000f), entityType("enderman")), //Zealot
    Mob("Zealot Bruiser", "Enderman", null, listOf(65000f), entityType("enderman")), //Zealot Bruiser

    Mob("Blaze", "Blaze", null, listOf(1200f, 250000f, 300000f, 500000f), entityType("blaze")), //Blaze
    Mob("Flaming Spider", "Spider", null, listOf(1000000.0f), entityType("spider")), //Flaming Spider
    Mob("Flare", "Blaze", null, listOf(5000000f), entityType("blaze")), //Flare
    Mob("Ghast", "Ghast", null, listOf(100000f), entityType("ghast")), //Ghast
    Mob("Kada Knight", "Zombified Piglin", null, listOf(800000f), entityType("zombified_piglin")), //Kada Knight
    Mob("Magma Cube", "Magma Cube", null, listOf(400000f, 600000f), entityType("magma_cube")), //Magma Cube
    Mob("Magma Cube Rider", "Zombified Piglin", null, listOf(1200000f), entityType("zombified_piglin")), //Magma Cube Rider
    Mob("Matcho", "matcho ", "ef2daabb78a1f7aa12d145d88c0ca46b9e856f5534e9286e555faf0c291f4fd5", listOf(750000f), entityType("player")), //Matcho
    Mob("Millennia-Aged Blaze", "Blaze", null, listOf(12000000f), entityType("blaze")), //Millennia-Aged Blaze
    Mob("Mushroom Bull", "Mooshroom", null, listOf(1000000.0f), entityType("mooshroom")), //Mushroom Bull
    Mob("Smoldering Blaze", "Blaze", null, listOf(2000000f), entityType("blaze")), //Smoldering Blaze
    Mob("Tentacle", "Zombie", null, listOf(2500000.0f), entityType("zombie")), //Tentacle
    Mob("Wither Skeleton", "Wither Skeleton", null, listOf(300000.0f), entityType("wither_skeleton")), //Wither Skeleton
    Mob("Wither Spectre", "Wither Skeleton", null, listOf(300000.0f), entityType("wither_skeleton")), //Wither Spectre


    Mob("Emerald Slime", "Slime", null, listOf(80.0f, 150f), entityType("slime")), //Emerald Slime
    Mob("Lapis Zombie", "Zombie", null, listOf(200.0f), entityType("zombie")), //Lapis Zombie
    Mob("Miner Skeleton", "Skeleton", null, listOf(250.0f, 300f), entityType("skeleton")), //Miner Skeleton
    Mob("Miner Zombie", "Zombie", null, listOf(250.0f, 300f), entityType("zombie")), //Miner Zombie
    Mob("Redstone Pigman", "Zombified Piglin", null, listOf(250.0f), entityType("zombified_piglin")), //Redstone Pigman
    Mob("Sneaky Creeper", "Creeper", null, listOf(120.0f), entityType("creeper")), //Sneaky Creeper

    Mob("Ghost", "Creeper", null, listOf(1000000.0f), entityType("creeper")), //Ghost
    Mob("Glacite Bowman", "Glacite Bowman", "3e1cef33161ec42226aa8220f1b1cc02e8ede6dea7cdd487402f559f3c8fdab6", listOf(750000.0f), entityType("player")), //Glacite Bowman
    Mob("Glacite Caver", "Glacite Caver", "ef3178fb4bd2c629c218ec03fd4a96bfdc846b1f5625743c49eb205b873ae0d5", listOf(800000.0f), entityType("player")), //Glacite Caver
    Mob("Glacite Mage", "Glacite Mage", "f941d0e9413b50507919e2679a02a034a37cd0661b7c2de646a076d636033f42", listOf(825000.0f), entityType("player")), //Glacite Mage
    Mob("Glacite Mutt", "Wolf", null, listOf(750000.0f), entityType("wolf")), //Glacite Mutt
    Mob("Glacite Walker", "Ice Walker", "b2b12a814ced8af02cddf29a37e7f3011e430e8a18b38b706f27c6bd31650b65", listOf(888.0f), entityType("player")), //Glacite Walker
    Mob("Goblin", "Goblin ", "3af28fbf046e5ddc735756836f62e798cc69a3816b4b4ccc86200da88e4963de", listOf(800.0f), entityType("player")), //Goblin
    Mob("Littlefoot", "Littlefoot ", "f2b33640bfb71557e0e1d852287263ceafc9bec205301acf046b7c29fe8cb37b", listOf(2.5E7f), entityType("player")), //Littlefoot
    Mob("Ghast", "Ghast", null, listOf(50f), entityType("ghast")), //Ghast
    Mob("Star Sentry", "Crystal Sentry", "d87b8f3cf1c1014883276f230501d5af1397699071bfab1ee294c861722cc2f", listOf(10.0f), entityType("player")), //Star Sentry
    Mob("Treasure Hoarder", "Treasuer Hunter", "b2b12a814ced8af02cddf29a37e7f3011e430e8a18b38b706f27c6bd31650b65", listOf(15000.0f), entityType("player")), //Treasure Hoarder

    Mob("Automaton", "Iron Golem", null, listOf(15000.0f, 20000f), entityType("iron_golem")), //Automaton
    Mob("Grunt", "Team Treasurite", "4c85bdda8ffc233e738fb3ca7715e947a8e62a351031682da1ef6efcecf48f79", listOf(18000.0f), entityType("player")), //Grunt
    Mob("Grunt", "Team Treasurite", "83a4985545a3318293e7362b05293116063a6de86fbd45df5cce6662f4728133", listOf(15000.0f), entityType("player")), //Grunt
    Mob("Grunt", "Team Treasurite", "eaf2a8dfe1f8b21c446008a5c2a76b9dcc7ec202c2e08bbe4b5c29a898895451", listOf(15000.0f), entityType("player")), //Grunt
    Mob("Grunt", "Team Treasurite", "8030adf55b9f8a5acc8aca9488df08c3f3bf630926b347a755fd7e1b44e3ad60", listOf(18000.0f), entityType("player")), //Grunt
    Mob("Sludge", "Slime", null, listOf(5000f, 10000.0f, 25000f), entityType("slime")), //Sludge
    Mob("Thyst", "Endermite", null, listOf(5000.0f), entityType("endermite")), //Thyst
    Mob("Yog", "Magma Cube", null, listOf(35000.0f), entityType("magma_cube")), //Yog

    Mob("Howling Spirit", "Wolf", null, listOf(7000.0f), entityType("wolf")), //Howling Spirit
    Mob("Pack Spirit", "Wolf", null, listOf(6000.0f), entityType("wolf")), //Pack Spirit
    Mob("Soul of the Alpha", "Wolf", null, listOf(31150.0f), entityType("wolf")), //Soul of the Alpha

    Mob("Chill", "Stray", null, listOf(400.0f), entityType("stray")), //Chill
    Mob("Tidedot", "Drowned", null, listOf(4000f, 5000f, 6000.0f), entityType("drowned")), //Tidedot
)
