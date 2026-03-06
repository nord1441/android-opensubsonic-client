package com.opensubsonic.client.data.db

import androidx.room.*
import com.opensubsonic.client.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerConfig?

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun getActiveServerFlow(): Flow<ServerConfig?>

    @Query("SELECT * FROM servers")
    fun getAllServers(): Flow<List<ServerConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerConfig): Long

    @Update
    suspend fun updateServer(server: ServerConfig)

    @Delete
    suspend fun deleteServer(server: ServerConfig)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE servers SET isActive = 1 WHERE id = :serverId")
    suspend fun activateServer(serverId: Long)
}
