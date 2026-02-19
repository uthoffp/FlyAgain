package com.flyagain.database.repository

import com.flyagain.common.grpc.*
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [GameDataRepository].
 *
 * Reads static game definition tables that are populated by Flyway migrations
 * and game design data. These tables are read-only at runtime — the world
 * service caches them in memory after loading at startup.
 *
 * @param dataSource the HikariCP connection pool
 */
class GameDataRepositoryImpl(dataSource: DataSource) : BaseRepository(dataSource), GameDataRepository {

    override suspend fun getAllItemDefinitions(): List<ItemDefinitionRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM item_definitions ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<ItemDefinitionRecord>()
                while (rs.next()) {
                    results.add(
                        ItemDefinitionRecord.newBuilder()
                            .setId(rs.getInt("id"))
                            .setName(rs.getString("name"))
                            .setType(rs.getInt("type"))
                            .setSubtype(rs.getInt("subtype"))
                            .setLevelReq(rs.getInt("level_req"))
                            .setClassReq(rs.getInt("class_req").let { if (rs.wasNull()) -1 else it })
                            .setRarity(rs.getInt("rarity"))
                            .setBaseAttack(rs.getInt("base_attack"))
                            .setBaseDefense(rs.getInt("base_defense"))
                            .setBaseHp(rs.getInt("base_hp"))
                            .setBaseMp(rs.getInt("base_mp"))
                            .setBuyPrice(rs.getInt("buy_price"))
                            .setSellPrice(rs.getInt("sell_price"))
                            .setStackable(rs.getBoolean("stackable"))
                            .setMaxStack(rs.getInt("max_stack"))
                            .setDescription(rs.getString("description") ?: "")
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getAllMonsterDefinitions(): List<MonsterDefinitionRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM monster_definitions ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<MonsterDefinitionRecord>()
                while (rs.next()) {
                    results.add(
                        MonsterDefinitionRecord.newBuilder()
                            .setId(rs.getInt("id"))
                            .setName(rs.getString("name"))
                            .setLevel(rs.getInt("level"))
                            .setHp(rs.getInt("hp"))
                            .setAttack(rs.getInt("attack"))
                            .setDefense(rs.getInt("defense"))
                            .setXpReward(rs.getInt("xp_reward"))
                            .setAggroRange(rs.getFloat("aggro_range"))
                            .setAttackRange(rs.getFloat("attack_range"))
                            .setAttackSpeedMs(rs.getInt("attack_speed_ms"))
                            .setMoveSpeed(rs.getFloat("move_speed"))
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getAllMonsterSpawns(): List<MonsterSpawnRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM monster_spawns ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<MonsterSpawnRecord>()
                while (rs.next()) {
                    results.add(
                        MonsterSpawnRecord.newBuilder()
                            .setId(rs.getInt("id"))
                            .setMonsterId(rs.getInt("monster_id"))
                            .setMapId(rs.getInt("map_id"))
                            .setPosX(rs.getFloat("pos_x"))
                            .setPosY(rs.getFloat("pos_y"))
                            .setPosZ(rs.getFloat("pos_z"))
                            .setSpawnRadius(rs.getFloat("spawn_radius"))
                            .setSpawnCount(rs.getInt("spawn_count"))
                            .setRespawnMs(rs.getInt("respawn_ms"))
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getAllSkillDefinitions(): List<SkillDefinitionRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM skill_definitions ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<SkillDefinitionRecord>()
                while (rs.next()) {
                    results.add(
                        SkillDefinitionRecord.newBuilder()
                            .setId(rs.getInt("id"))
                            .setName(rs.getString("name"))
                            .setClassReq(rs.getInt("class_req"))
                            .setLevelReq(rs.getInt("level_req"))
                            .setMaxLevel(rs.getInt("max_level"))
                            .setMpCost(rs.getInt("mp_cost"))
                            .setCooldownMs(rs.getInt("cooldown_ms"))
                            .setBaseDamage(rs.getInt("base_damage"))
                            .setDamagePerLevel(rs.getInt("damage_per_level"))
                            .setRangeUnits(rs.getFloat("range_units"))
                            .setDescription(rs.getString("description") ?: "")
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getAllLootTables(): List<LootTableRecord> = withConnection { conn ->
        conn.prepareStatement("SELECT * FROM loot_table ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<LootTableRecord>()
                while (rs.next()) {
                    results.add(
                        LootTableRecord.newBuilder()
                            .setId(rs.getInt("id"))
                            .setMonsterId(rs.getInt("monster_id"))
                            .setItemId(rs.getInt("item_id"))
                            .setDropChance(rs.getFloat("drop_chance"))
                            .setMinAmount(rs.getInt("min_amount"))
                            .setMaxAmount(rs.getInt("max_amount"))
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getCharacterSkills(characterId: Long): List<CharacterSkillRecord> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT skill_id, skill_level FROM character_skills WHERE character_id = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<CharacterSkillRecord>()
                while (rs.next()) {
                    results.add(
                        CharacterSkillRecord.newBuilder()
                            .setSkillId(rs.getInt("skill_id"))
                            .setSkillLevel(rs.getInt("skill_level"))
                            .build()
                    )
                }
                results
            }
        }
    }
}
