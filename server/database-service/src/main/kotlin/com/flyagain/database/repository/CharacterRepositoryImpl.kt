package com.flyagain.database.repository

import com.flyagain.common.grpc.CharacterRecord
import com.flyagain.common.grpc.SaveCharacterRequest
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [CharacterRepository].
 *
 * Handles character CRUD with class-specific base stats on creation,
 * periodic write-back saves, and soft-deletion. All mutating operations
 * run inside transactions to maintain data consistency.
 *
 * @param dataSource the HikariCP connection pool
 */
class CharacterRepositoryImpl(dataSource: DataSource) : BaseRepository(dataSource), CharacterRepository {

    override suspend fun getByAccount(accountId: String): List<CharacterRecord> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT * FROM characters WHERE account_id = ? AND is_deleted = FALSE ORDER BY created_at"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(accountId))
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<CharacterRecord>()
                while (rs.next()) {
                    results.add(mapToCharacterRecord(rs))
                }
                results
            }
        }
    }

    override suspend fun getById(characterId: String, accountId: String): CharacterRecord? = withConnection { conn ->
        conn.prepareStatement(
            "SELECT * FROM characters WHERE id = ? AND account_id = ? AND is_deleted = FALSE"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.setObject(2, UUID.fromString(accountId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapToCharacterRecord(rs) else null
            }
        }
    }

    override suspend fun create(accountId: String, name: String, characterClass: Int): String = withTransaction { conn ->
        // Enforce maximum of 3 characters per account
        val count = conn.prepareStatement(
            "SELECT COUNT(*) FROM characters WHERE account_id = ? AND is_deleted = FALSE"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(accountId))
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

        if (count >= 3) {
            throw IllegalStateException("Maximum 3 characters per account")
        }

        // Base stats by class: 0=Krieger, 1=Magier, 2=Assassine, 3=Kleriker
        data class BaseStats(val hp: Int, val mp: Int, val maxHp: Int, val maxMp: Int,
                             val str: Int, val sta: Int, val dex: Int, val intStat: Int)
        val stats = when (characterClass) {
            0 -> BaseStats(150, 50, 150, 50, 15, 15, 10, 5)   // Krieger
            1 -> BaseStats(80, 150, 80, 150, 5, 8, 10, 20)    // Magier
            2 -> BaseStats(100, 80, 100, 80, 10, 8, 20, 5)    // Assassine
            3 -> BaseStats(120, 120, 120, 120, 8, 12, 8, 15)  // Kleriker
            else -> throw IllegalArgumentException("Invalid class: $characterClass")
        }

        conn.prepareStatement(
            """INSERT INTO characters (account_id, name, class, level, xp, hp, mp, max_hp, max_mp,
               str, sta, dex, int_stat, stat_points, map_id, pos_x, pos_y, pos_z, gold)
               VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, 0, 0, 0, 0) RETURNING id"""
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(accountId))
            stmt.setString(2, name)
            stmt.setInt(3, characterClass)
            stmt.setInt(4, stats.hp)
            stmt.setInt(5, stats.mp)
            stmt.setInt(6, stats.maxHp)
            stmt.setInt(7, stats.maxMp)
            stmt.setInt(8, stats.str)
            stmt.setInt(9, stats.sta)
            stmt.setInt(10, stats.dex)
            stmt.setInt(11, stats.intStat)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getString("id")
            }
        }
    }

    override suspend fun save(request: SaveCharacterRequest): Unit = withTransaction { conn ->
        conn.prepareStatement(
            """UPDATE characters SET hp = ?, mp = ?, xp = ?, level = ?, map_id = ?,
               pos_x = ?, pos_y = ?, pos_z = ?, gold = ?, play_time = ?,
               str = ?, sta = ?, dex = ?, int_stat = ?, stat_points = ?
               WHERE id = ?"""
        ).use { stmt ->
            stmt.setInt(1, request.hp)
            stmt.setInt(2, request.mp)
            stmt.setLong(3, request.xp)
            stmt.setInt(4, request.level)
            stmt.setInt(5, request.mapId)
            stmt.setFloat(6, request.posX)
            stmt.setFloat(7, request.posY)
            stmt.setFloat(8, request.posZ)
            stmt.setLong(9, request.gold)
            stmt.setLong(10, request.playTime)
            stmt.setInt(11, request.str)
            stmt.setInt(12, request.sta)
            stmt.setInt(13, request.dex)
            stmt.setInt(14, request.intStat)
            stmt.setInt(15, request.statPoints)
            stmt.setObject(16, UUID.fromString(request.characterId))
            stmt.executeUpdate()
        }
    }

    override suspend fun softDelete(characterId: String, accountId: String): Unit = withTransaction { conn ->
        conn.prepareStatement(
            "UPDATE characters SET is_deleted = TRUE WHERE id = ? AND account_id = ?"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.setObject(2, UUID.fromString(accountId))
            stmt.executeUpdate()
        }
    }

    /**
     * Maps a SQL [ResultSet][java.sql.ResultSet] row to a protobuf [CharacterRecord],
     * reading all character columns including stats, position, and metadata.
     */
    private fun mapToCharacterRecord(rs: java.sql.ResultSet): CharacterRecord =
        CharacterRecord.newBuilder()
            .setId(rs.getString("id"))
            .setAccountId(rs.getString("account_id"))
            .setName(rs.getString("name"))
            .setCharacterClass(rs.getInt("class"))
            .setLevel(rs.getInt("level"))
            .setXp(rs.getLong("xp"))
            .setHp(rs.getInt("hp"))
            .setMp(rs.getInt("mp"))
            .setMaxHp(rs.getInt("max_hp"))
            .setMaxMp(rs.getInt("max_mp"))
            .setStr(rs.getInt("str"))
            .setSta(rs.getInt("sta"))
            .setDex(rs.getInt("dex"))
            .setIntStat(rs.getInt("int_stat"))
            .setStatPoints(rs.getInt("stat_points"))
            .setMapId(rs.getInt("map_id"))
            .setPosX(rs.getFloat("pos_x"))
            .setPosY(rs.getFloat("pos_y"))
            .setPosZ(rs.getFloat("pos_z"))
            .setGold(rs.getLong("gold"))
            .setPlayTime(rs.getLong("play_time"))
            .setIsDeleted(rs.getBoolean("is_deleted"))
            .setFound(true)
            .build()
}
