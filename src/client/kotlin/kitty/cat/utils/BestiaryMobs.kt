package kitty.cat.utils

import net.minecraft.world.entity.EntityType

data class Mob(val beName: String, val name: String, val texture: String?, val maxHealth: List<Float?>, val mobType: EntityType<*>?)

val allMobs = listOf<Mob>(
    Mob("Crypt Ghoul","Zombie", null, listOf(2000f), EntityType.ZOMBIE),
    Mob("Golden Ghoul", "Zombie", null, listOf(45000f), EntityType.ZOMBIE),
    Mob("Graveyard Zombie", "Zombie", null, listOf(100f), EntityType.ZOMBIE),
    Mob("Old Wolf", "Wolf", null, listOf(15000f), EntityType.WOLF),
    Mob("Shiny Pig", "Pig", null, listOf(null), EntityType.PIG),
    Mob("Wolf", "Wolf", null, listOf(250f), EntityType.WOLF),
    Mob("Zombie Villager", "Zombie Villager", null, listOf(120f), EntityType.ZOMBIE_VILLAGER),

    Mob("Chicken", "Chicken", null, listOf(20.0f), EntityType.CHICKEN),
    Mob("Cow", "Cow", null, listOf(50.0f), EntityType.COW),
    Mob("Mushroom Cow", "Mooshroom", null, listOf(50.0f), EntityType.MOOSHROOM),
    Mob("Pig", "Pig", null, listOf(50.0f), EntityType.PIG),
    Mob("Rabbit", "Rabbit", null, listOf(130.0f), EntityType.RABBIT),
    Mob("Sheep", "Sheep", null, listOf(50.0f), EntityType.SHEEP),

    Mob("Arachne's Keeper", "Spider", null, listOf(3000.0f), EntityType.SPIDER), //Arachne Keeper
    Mob("Broodmother", "Spider", null, listOf(6000.0f), EntityType.SPIDER), //Broodmother
    Mob("Dasher Spider", "Spider", null, listOf(170f, 210f, 900f, 1100f, 1400f), EntityType.SPIDER), //Dasher Spider
    Mob("Flint Skeleton", "Skeleton", null, listOf(100.0f), EntityType.SKELETON), //Flint Skeleton
    Mob("Rain Slime", "Slime", null, listOf(400.0f, 1500f, 1000f), EntityType.SLIME), //Rain Slime
    Mob("Silverfish", "Silverfish", null, listOf(50.0f), EntityType.SILVERFISH),
    Mob("Spider Jockey", "Skeleton", null, listOf(220f, 280f, 1000.0f), EntityType.SKELETON), //Spider Jockey
    Mob("Splitter Spider", "Spider", null, listOf(180f, 220f, 260f, 800f, 1100f, 1450f), EntityType.SPIDER), //Splitter Spider
    Mob("Voracious Spider", "Spider", null, listOf(300f, 750f, 1150f, 1450f), EntityType.SPIDER), //Voracious Spider
    Mob("Weaver Spider", "Spider", null, listOf(160f, 180f, 200f, 220f, 800f, 1200f, 1500f), EntityType.SPIDER), //Weaver Spider

    Mob("Enderman", "Enderman", null, listOf(4500f, 6000f, 9000f), EntityType.ENDERMAN), //Enderman
    Mob("Obsidian Defender", "Wither Skeleton", null, listOf(10000.0f), EntityType.WITHER_SKELETON), //Obsidian Defender
    Mob("Seer", "Skeleton", null, listOf(9500.0f), EntityType.SKELETON), //Seer
    Mob("Voidling Extremist", "Enderman", null, listOf(8000000f), EntityType.ENDERMAN), //Voidling Extremist
    Mob("Voidling Fanatic", "Enderman", null, listOf(750000f), EntityType.ENDERMAN), //Voidling Fanatic
    Mob("Zealot", "Enderman", null, listOf(13000f), EntityType.ENDERMAN), //Zealot
    Mob("Zealot Bruiser", "Enderman", null, listOf(65000f), EntityType.ENDERMAN), //Zealot Bruiser

    Mob("Blaze", "Blaze", null, listOf(1200f, 250000f, 300000f, 500000f), EntityType.BLAZE), //Blaze
    Mob("Flaming Spider", "Spider", null, listOf(1000000.0f), EntityType.SPIDER), //Flaming Spider
    Mob("Flare", "Blaze", null, listOf(5000000f), EntityType.BLAZE), //Flare
    Mob("Ghast", "Ghast", null, listOf(100000f), EntityType.GHAST), //Ghast
    Mob("Kada Knight", "Zombified Piglin", null, listOf(800000f), EntityType.ZOMBIFIED_PIGLIN), //Kada Knight
    Mob("Magma Cube", "Magma Cube", null, listOf(400000f, 600000f), EntityType.MAGMA_CUBE), //Magma Cube
    Mob("Magma Cube Rider", "Zombified Piglin", null, listOf(1200000f), EntityType.ZOMBIFIED_PIGLIN), //Magma Cube Rider
    Mob("Matcho", "matcho ", "ef2daabb78a1f7aa12d145d88c0ca46b9e856f5534e9286e555faf0c291f4fd5", listOf(750000f), EntityType.PLAYER), //Matcho
    Mob("Millennia-Aged Blaze", "Blaze", null, listOf(12000000f), EntityType.BLAZE), //Millennia-Aged Blaze
    Mob("Mushroom Bull", "Mooshroom", null, listOf(1000000.0f), EntityType.MOOSHROOM), //Mushroom Bull
    Mob("Smoldering Blaze", "Blaze", null, listOf(2000000f), EntityType.BLAZE), //Smoldering Blaze
    Mob("Tentacle", "Zombie", null, listOf(2500000.0f), EntityType.ZOMBIE), //Tentacle
    Mob("Wither Skeleton", "Wither Skeleton", null, listOf(300000.0f), EntityType.WITHER_SKELETON), //Wither Skeleton
    Mob("Wither Spectre", "Wither Skeleton", null, listOf(300000.0f), EntityType.WITHER_SKELETON), //Wither Spectre


    Mob("Emerald Slime", "Slime", null, listOf(80.0f, 150f), EntityType.SLIME), //Emerald Slime
    Mob("Lapis Zombie", "Zombie", null, listOf(200.0f), EntityType.ZOMBIE), //Lapis Zombie
    Mob("Miner Skeleton", "Skeleton", null, listOf(250.0f, 300f), EntityType.SKELETON), //Miner Skeleton
    Mob("Miner Zombie", "Zombie", null, listOf(250.0f, 300f), EntityType.ZOMBIE), //Miner Zombie
    Mob("Redstone Pigman", "Zombified Piglin", null, listOf(250.0f), EntityType.ZOMBIFIED_PIGLIN), //Redstone Pigman
    Mob("Sneaky Creeper", "Creeper", null, listOf(120.0f), EntityType.CREEPER), //Sneaky Creeper

    Mob("Ghost", "Creeper", null, listOf(1000000.0f), EntityType.CREEPER), //Ghost
    Mob("Glacite Bowman", "Glacite Bowman", "3e1cef33161ec42226aa8220f1b1cc02e8ede6dea7cdd487402f559f3c8fdab6", listOf(750000.0f), EntityType.PLAYER), //Glacite Bowman
    Mob("Glacite Caver", "Glacite Caver", "ef3178fb4bd2c629c218ec03fd4a96bfdc846b1f5625743c49eb205b873ae0d5", listOf(800000.0f), EntityType.PLAYER), //Glacite Caver
    Mob("Glacite Mage", "Glacite Mage", "f941d0e9413b50507919e2679a02a034a37cd0661b7c2de646a076d636033f42", listOf(825000.0f), EntityType.PLAYER), //Glacite Mage
    Mob("Glacite Mutt", "Wolf", null, listOf(750000.0f), EntityType.WOLF), //Glacite Mutt
    Mob("Glacite Walker", "Ice Walker", "b2b12a814ced8af02cddf29a37e7f3011e430e8a18b38b706f27c6bd31650b65", listOf(888.0f), EntityType.PLAYER), //Glacite Walker
    Mob("Goblin", "Goblin ", "3af28fbf046e5ddc735756836f62e798cc69a3816b4b4ccc86200da88e4963de", listOf(800.0f), EntityType.PLAYER), //Goblin
    Mob("Littlefoot", "Littlefoot ", "f2b33640bfb71557e0e1d852287263ceafc9bec205301acf046b7c29fe8cb37b", listOf(2.5E7f), EntityType.PLAYER), //Littlefoot
    Mob("Ghast", "Ghast", null, listOf(50f), EntityType.GHAST), //Ghast
    Mob("Star Sentry", "Crystal Sentry", "d87b8f3cf1c1014883276f230501d5af1397699071bfab1ee294c861722cc2f", listOf(10.0f), EntityType.PLAYER), //Star Sentry
    Mob("Treasure Hoarder", "Treasuer Hunter", "b2b12a814ced8af02cddf29a37e7f3011e430e8a18b38b706f27c6bd31650b65", listOf(15000.0f), EntityType.PLAYER), //Treasure Hoarder

    Mob("Automaton", "Iron Golem", null, listOf(15000.0f, 20000f), EntityType.IRON_GOLEM), //Automaton
    Mob("Grunt", "Team Treasurite", "4c85bdda8ffc233e738fb3ca7715e947a8e62a351031682da1ef6efcecf48f79", listOf(18000.0f), EntityType.PLAYER), //Grunt
    Mob("Grunt", "Team Treasurite", "83a4985545a3318293e7362b05293116063a6de86fbd45df5cce6662f4728133", listOf(15000.0f), EntityType.PLAYER), //Grunt
    Mob("Grunt", "Team Treasurite", "eaf2a8dfe1f8b21c446008a5c2a76b9dcc7ec202c2e08bbe4b5c29a898895451", listOf(15000.0f), EntityType.PLAYER), //Grunt
    Mob("Grunt", "Team Treasurite", "8030adf55b9f8a5acc8aca9488df08c3f3bf630926b347a755fd7e1b44e3ad60", listOf(18000.0f), EntityType.PLAYER), //Grunt
    Mob("Sludge", "Slime", null, listOf(5000f, 10000.0f, 25000f), EntityType.SLIME), //Sludge
    Mob("Thyst", "Endermite", null, listOf(5000.0f), EntityType.ENDERMITE), //Thyst
    Mob("Yog", "Magma Cube", null, listOf(35000.0f), EntityType.MAGMA_CUBE), //Yog

    Mob("Howling Spirit", "Wolf", null, listOf(7000.0f), EntityType.WOLF), //Howling Spirit
    Mob("Pack Spirit", "Wolf", null, listOf(6000.0f), EntityType.WOLF), //Pack Spirit
    Mob("Soul of the Alpha", "Wolf", null, listOf(31150.0f), EntityType.WOLF), //Soul of the Alpha

    Mob("Chill", "Stray", null, listOf(400.0f), EntityType.STRAY), //Chill
    Mob("Tidedot", "Drowned", null, listOf(4000f, 5000f, 6000.0f), EntityType.DROWNED), //Tidedot
)