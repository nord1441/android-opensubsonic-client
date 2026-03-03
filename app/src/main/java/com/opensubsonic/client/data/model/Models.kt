package com.opensubsonic.client.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val isActive: Boolean = true
)

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val serverId: Long = 0
)

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val serverId: Long = 0
)

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val path: String? = null,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val serverId: Long = 0
)

@Entity(tableName = "genres")
data class Genre(
    @PrimaryKey val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val owner: String? = null,
    val isPublic: Boolean = false,
    val serverId: Long = 0
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId", "sortOrder"])
data class PlaylistSong(
    val playlistId: String,
    val songId: String,
    val sortOrder: Int
)

data class Genre2(
    val value: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)

enum class RepeatMode {
    OFF, ONE, ALL
}

enum class PlaybackMode {
    SEQUENTIAL, SHUFFLE, REPEAT_ONE, REPEAT_ALL
}
