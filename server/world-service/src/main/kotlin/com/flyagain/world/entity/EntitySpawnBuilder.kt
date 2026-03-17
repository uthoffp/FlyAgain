package com.flyagain.world.entity

import com.flyagain.common.proto.EntitySpawnMessage
import com.flyagain.common.proto.Position

/**
 * Shared builder for EntitySpawnMessage protobuf messages.
 * Eliminates duplicate buildPlayerSpawn / buildMonsterSpawn methods
 * across EnterWorldHandler and ZoneChangeHandler.
 */
object EntitySpawnBuilder {

    fun buildPlayerSpawn(player: PlayerEntity): EntitySpawnMessage {
        return EntitySpawnMessage.newBuilder()
            .setEntityId(player.entityId)
            .setEntityType(0) // player
            .setName(player.name)
            .setPosition(Position.newBuilder()
                .setX(player.x)
                .setY(player.y)
                .setZ(player.z)
                .build())
            .setRotation(player.rotation)
            .setLevel(player.level)
            .setHp(player.hp)
            .setMaxHp(player.maxHp)
            .setCharacterClass(player.characterClass)
            .setIsFlying(player.isFlying)
            .build()
    }

    fun buildMonsterSpawn(monster: MonsterEntity): EntitySpawnMessage {
        return EntitySpawnMessage.newBuilder()
            .setEntityId(monster.entityId)
            .setEntityType(1) // monster
            .setName(monster.name)
            .setPosition(Position.newBuilder()
                .setX(monster.x)
                .setY(monster.y)
                .setZ(monster.z)
                .build())
            .setLevel(monster.level)
            .setHp(monster.hp)
            .setMaxHp(monster.maxHp)
            .build()
    }
}
