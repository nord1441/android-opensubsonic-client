package com.opensubsonic.client.api

import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    @GET("rest/ping")
    suspend fun ping(): SubsonicResponse<Unit>

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String = "alphabeticalByName",
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0
    ): SubsonicResponse<Unit>

    @GET("rest/getAlbum")
    suspend fun getAlbum(
        @Query("id") id: String
    ): SubsonicResponse<Unit>

    @GET("rest/getArtists")
    suspend fun getArtists(): SubsonicResponse<Unit>

    @GET("rest/getArtist")
    suspend fun getArtist(
        @Query("id") id: String
    ): SubsonicResponse<Unit>

    @GET("rest/getGenres")
    suspend fun getGenres(): SubsonicResponse<Unit>

    @GET("rest/getAlbumList2")
    suspend fun getAlbumsByGenre(
        @Query("type") type: String = "byGenre",
        @Query("genre") genre: String,
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0
    ): SubsonicResponse<Unit>

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicResponse<Unit>

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(
        @Query("id") id: String
    ): SubsonicResponse<Unit>

    @GET("rest/search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 50
    ): SubsonicResponse<Unit>

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 50,
        @Query("genre") genre: String? = null
    ): SubsonicResponse<Unit>
}
