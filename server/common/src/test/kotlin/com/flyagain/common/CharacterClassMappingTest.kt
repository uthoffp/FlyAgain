package com.flyagain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CharacterClassMappingTest {

    // ---- ID → Name mapping ----

    @Test
    fun `Warrior has class ID 0`() {
        assertEquals("Warrior", CharacterClassMapping.nameForId(0))
    }

    @Test
    fun `Mage has class ID 1`() {
        assertEquals("Mage", CharacterClassMapping.nameForId(1))
    }

    @Test
    fun `Assassin has class ID 2`() {
        assertEquals("Assassin", CharacterClassMapping.nameForId(2))
    }

    @Test
    fun `Cleric has class ID 3`() {
        assertEquals("Cleric", CharacterClassMapping.nameForId(3))
    }

    @Test
    fun `unknown class ID returns Unknown`() {
        assertEquals("Unknown", CharacterClassMapping.nameForId(-1))
        assertEquals("Unknown", CharacterClassMapping.nameForId(4))
        assertEquals("Unknown", CharacterClassMapping.nameForId(99))
    }

    // ---- Name → ID mapping ----

    @Test
    fun `warrior name resolves to ID 0`() {
        assertEquals(0, CharacterClassMapping.idForName("warrior"))
    }

    @Test
    fun `mage name resolves to ID 1`() {
        assertEquals(1, CharacterClassMapping.idForName("mage"))
    }

    @Test
    fun `assassin name resolves to ID 2`() {
        assertEquals(2, CharacterClassMapping.idForName("assassin"))
    }

    @Test
    fun `cleric name resolves to ID 3`() {
        assertEquals(3, CharacterClassMapping.idForName("cleric"))
    }

    @Test
    fun `name lookup is case-insensitive`() {
        assertEquals(0, CharacterClassMapping.idForName("Warrior"))
        assertEquals(1, CharacterClassMapping.idForName("MAGE"))
        assertEquals(2, CharacterClassMapping.idForName("Assassin"))
        assertEquals(3, CharacterClassMapping.idForName("CLERIC"))
    }

    @Test
    fun `unknown class name returns -1`() {
        assertEquals(-1, CharacterClassMapping.idForName(""))
        assertEquals(-1, CharacterClassMapping.idForName("knight"))
        assertEquals(-1, CharacterClassMapping.idForName("archer"))
    }

    // ---- Roundtrip consistency ----

    @Test
    fun `every ID maps to a name that maps back to the same ID`() {
        for ((id, name) in CharacterClassMapping.ID_TO_NAME) {
            val resolvedId = CharacterClassMapping.idForName(name)
            assertEquals(id, resolvedId, "Roundtrip failed: ID $id → '$name' → $resolvedId")
        }
    }

    @Test
    fun `every name maps to an ID that maps back to the expected name`() {
        for ((name, id) in CharacterClassMapping.NAME_TO_ID) {
            val resolvedName = CharacterClassMapping.nameForId(id)
            assertEquals(name, resolvedName.lowercase(),
                "Roundtrip failed: '$name' → $id → '$resolvedName'")
        }
    }

    // ---- Completeness ----

    @Test
    fun `exactly 4 character classes are defined`() {
        assertEquals(4, CharacterClassMapping.ID_TO_NAME.size)
        assertEquals(4, CharacterClassMapping.NAME_TO_ID.size)
    }

    @Test
    fun `class IDs are contiguous starting from 0`() {
        assertEquals(setOf(0, 1, 2, 3), CharacterClassMapping.VALID_IDS)
    }

    @Test
    fun `valid names contain all expected classes`() {
        assertTrue(CharacterClassMapping.VALID_NAMES.containsAll(
            setOf("warrior", "mage", "assassin", "cleric")))
    }

    @Test
    fun `VALID_IDS matches ID_TO_NAME keys`() {
        assertEquals(CharacterClassMapping.ID_TO_NAME.keys, CharacterClassMapping.VALID_IDS)
    }

    @Test
    fun `VALID_NAMES matches NAME_TO_ID keys`() {
        assertEquals(CharacterClassMapping.NAME_TO_ID.keys, CharacterClassMapping.VALID_NAMES)
    }
}
