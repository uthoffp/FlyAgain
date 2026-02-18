package com.flyagain.world.ai

/**
 * States for the monster AI state machine.
 *
 * IDLE    - Monster is at its spawn point, not engaged with any player.
 * AGGRO   - Monster has detected a player within aggro range and is pursuing.
 * ATTACK  - Monster is within attack range and actively attacking its target.
 * RETURN  - Monster has lost its target or exceeded leash range; returning to spawn.
 * DEAD    - Monster is dead and waiting for respawn timer.
 */
enum class AIState {
    IDLE,
    AGGRO,
    ATTACK,
    RETURN,
    DEAD
}
