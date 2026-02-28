package com.flyagain.common

/**
 * Single source of truth for character class ID ↔ name mapping.
 *
 * Database stores classes as integers (0–3). This object provides
 * conversions used by login-service, account-service, and world-service.
 *
 * Class IDs: 0=Warrior, 1=Mage, 2=Assassin, 3=Cleric
 */
object CharacterClassMapping {

    /** Class integer ID → display name. */
    val ID_TO_NAME: Map<Int, String> = mapOf(
        0 to "Warrior",
        1 to "Mage",
        2 to "Assassin",
        3 to "Cleric"
    )

    /** Lowercase class name → integer ID (used for character creation input). */
    val NAME_TO_ID: Map<String, Int> = mapOf(
        "warrior"  to 0,
        "mage"     to 1,
        "assassin" to 2,
        "cleric"   to 3
    )

    /** All valid class IDs. */
    val VALID_IDS: Set<Int> = ID_TO_NAME.keys

    /** All valid class names (lowercase). */
    val VALID_NAMES: Set<String> = NAME_TO_ID.keys

    /** Resolves a class ID to its display name, or "Unknown" if invalid. */
    fun nameForId(classId: Int): String = ID_TO_NAME.getOrDefault(classId, "Unknown")

    /** Resolves a lowercase class name to its ID, or -1 if invalid. */
    fun idForName(className: String): Int = NAME_TO_ID.getOrDefault(className.lowercase(), -1)
}
