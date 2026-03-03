package com.opensubsonic.client.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.opensubsonic.client.api.SubsonicApi
import com.opensubsonic.client.api.SubsonicInterceptor
import com.opensubsonic.client.data.db.AppDatabase
import com.opensubsonic.client.data.db.MusicDao
import com.opensubsonic.client.data.db.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "subtune-db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideServerDao(db: AppDatabase): ServerDao = db.serverDao()

    @Provides
    fun provideMusicDao(db: AppDatabase): MusicDao = db.musicDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideServerConfigHolder(): ServerConfigHolder = ServerConfigHolder()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, serverConfigHolder: ServerConfigHolder): Retrofit {
        val interceptor = SubsonicInterceptor(
            usernameProvider = { serverConfigHolder.username },
            passwordProvider = { serverConfigHolder.password }
        )

        val client = okHttpClient.newBuilder()
            .addInterceptor(interceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(retrofit: Retrofit, serverConfigHolder: ServerConfigHolder): SubsonicApi {
        return DynamicSubsonicApi(retrofit, serverConfigHolder)
    }
}

class ServerConfigHolder {
    var baseUrl: String = "http://localhost/"
    var username: String = ""
    var password: String = ""

    fun update(url: String, user: String, pass: String) {
        baseUrl = url.trimEnd('/') + "/"
        username = user
        password = pass
    }
}

class DynamicSubsonicApi(
    private val retrofitBase: Retrofit,
    private val serverConfigHolder: ServerConfigHolder
) : SubsonicApi {

    private fun getApi(): SubsonicApi {
        val retrofit = retrofitBase.newBuilder()
            .baseUrl(serverConfigHolder.baseUrl)
            .build()
        return retrofit.create(SubsonicApi::class.java)
    }

    override suspend fun ping() = getApi().ping()
    override suspend fun getAlbumList2(type: String, size: Int, offset: Int) = getApi().getAlbumList2(type, size, offset)
    override suspend fun getAlbum(id: String) = getApi().getAlbum(id)
    override suspend fun getArtists() = getApi().getArtists()
    override suspend fun getArtist(id: String) = getApi().getArtist(id)
    override suspend fun getGenres() = getApi().getGenres()
    override suspend fun getAlbumsByGenre(type: String, genre: String, size: Int, offset: Int) = getApi().getAlbumsByGenre(type, genre, size, offset)
    override suspend fun getPlaylists() = getApi().getPlaylists()
    override suspend fun getPlaylist(id: String) = getApi().getPlaylist(id)
    override suspend fun search3(query: String, artistCount: Int, albumCount: Int, songCount: Int) = getApi().search3(query, artistCount, albumCount, songCount)
    override suspend fun getRandomSongs(size: Int, genre: String?) = getApi().getRandomSongs(size, genre)
}
