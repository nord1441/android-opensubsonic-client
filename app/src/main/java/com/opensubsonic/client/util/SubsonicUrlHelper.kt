package com.opensubsonic.client.util

import com.opensubsonic.client.data.model.ServerConfig
import java.security.MessageDigest
import java.util.UUID

object SubsonicUrlHelper {

    fun getStreamUrl(server: ServerConfig, songId: String): String {
        return buildUrl(server, "rest/stream", mapOf("id" to songId))
    }

    fun getCoverArtUrl(server: ServerConfig, coverArtId: String, size: Int = 300): String {
        return buildUrl(server, "rest/getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))
    }

    fun getDownloadUrl(server: ServerConfig, songId: String): String {
        return buildUrl(server, "rest/download", mapOf("id" to songId))
    }

    private fun buildUrl(server: ServerConfig, path: String, params: Map<String, String>): String {
        val baseUrl = server.url.trimEnd('/')
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5("${server.password}$salt")

        val queryParams = buildString {
            append("u=${server.username}")
            append("&t=$token")
            append("&s=$salt")
            append("&v=1.16.1")
            append("&c=SubTune")
            append("&f=json")
            params.forEach { (key, value) ->
                append("&$key=$value")
            }
        }
        return "$baseUrl/$path?$queryParams"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
