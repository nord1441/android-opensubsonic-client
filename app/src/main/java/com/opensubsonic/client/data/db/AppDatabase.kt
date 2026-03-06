package com.opensubsonic.client.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.opensubsonic.client.data.model.*

@Database(
    entities = [
        ServerConfig::class,
        Album::class,
        Artist::class,
        Song::class,
        Genre::class,
        Playlist::class,
        PlaylistSong::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun musicDao(): MusicDao
}
