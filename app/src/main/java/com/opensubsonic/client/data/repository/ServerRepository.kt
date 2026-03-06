package com.opensubsonic.client.data.repository

import com.opensubsonic.client.data.db.ServerDao
import com.opensubsonic.client.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    fun getActiveServerFlow(): Flow<ServerConfig?> = serverDao.getActiveServerFlow()

    suspend fun getActiveServer(): ServerConfig? = serverDao.getActiveServer()

    fun getAllServers(): Flow<List<ServerConfig>> = serverDao.getAllServers()

    suspend fun addServer(server: ServerConfig): Long {
        serverDao.deactivateAll()
        return serverDao.insertServer(server.copy(isActive = true))
    }

    suspend fun updateServer(server: ServerConfig) {
        serverDao.updateServer(server)
    }

    suspend fun deleteServer(server: ServerConfig) {
        serverDao.deleteServer(server)
    }

    suspend fun setActiveServer(serverId: Long) {
        serverDao.deactivateAll()
        serverDao.activateServer(serverId)
    }
}
