package com.flyagain.world

import com.flyagain.world.ai.AIState
import kotlin.test.Test
import kotlin.test.assertEquals

class AIStateTest {

    @Test
    fun `AIState has exactly 5 states`() {
        assertEquals(5, AIState.entries.size)
    }

    @Test
    fun `AIState contains all expected values`() {
        val expected = setOf("IDLE", "AGGRO", "ATTACK", "RETURN", "DEAD")
        val actual = AIState.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `AIState valueOf works for all states`() {
        assertEquals(AIState.IDLE, AIState.valueOf("IDLE"))
        assertEquals(AIState.AGGRO, AIState.valueOf("AGGRO"))
        assertEquals(AIState.ATTACK, AIState.valueOf("ATTACK"))
        assertEquals(AIState.RETURN, AIState.valueOf("RETURN"))
        assertEquals(AIState.DEAD, AIState.valueOf("DEAD"))
    }
}
