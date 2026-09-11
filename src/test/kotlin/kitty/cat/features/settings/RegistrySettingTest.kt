package kitty.cat.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegistrySettingTest {
    @Test fun canonicalizesIdsAndRejectsInvalidSavedValues() {
        val setting = RegistrySetting("Block", " MINECRAFT:STONE ", listOf("minecraft:stone", "minecraft:dirt"))
        assertEquals("minecraft:stone", setting.value)
        assertEquals("minecraft:dirt", setting.exactMatch(" Minecraft:Dirt "))
        assertFalse(setting.setValue("missing:block"))
        assertEquals("minecraft:stone", setting.value)
        assertTrue(setting.setValue("MINECRAFT:STONE"))
    }

    @Test fun ranksExactThenPrefixThenSubstringAndKeepsAllResultsReachable() {
        val setting = RegistrySetting("Entry", "stone", listOf("minecraft:stone", "stone_bricks", "stone", "dirt"))
        assertEquals(listOf("stone", "stone_bricks", "minecraft:stone"), setting.filteredSuggestions(" STONE "))
        assertEquals(setting.options, setting.filteredSuggestions(""))
        assertTrue(setting.filteredSuggestions("unknown").isEmpty())
        val many = (1..100).map { "test:entry_$it" }
        assertEquals(many, RegistrySetting("Many", many.first(), many).filteredSuggestions("entry"))
    }

    @Test fun rejectsInvalidDefaultsAndNormalizesOptions() {
        assertFailsWith<IllegalArgumentException> { RegistrySetting("Empty", "stone", emptyList()) }
        assertFailsWith<IllegalArgumentException> { RegistrySetting("Invalid", "missing", listOf("stone")) }
        val setting = RegistrySetting("Entry", "stone", listOf(" stone ", "STONE", "", "dirt"))
        assertEquals(listOf("stone", "dirt"), setting.options)
    }
}
