package com.opensubsonic.client.util

import com.opensubsonic.client.data.model.ServerConfig
import java.security.MessageDigest
import java.util.UUID

object SubsonicUrlHelper {

    private var cachedSalt: String? = null
    private var cachedToken: String? = null
    private var cachedPassword: String? = null

    private fun getAuth(server: ServerConfig): Pair<String, String> {
        if (cachedPassword == server.password && cachedSalt != null && cachedToken != null) {
            return cachedToken!! to cachedSalt!!
        }
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5("${server.password}$salt")
        cachedSalt = salt
        cachedToken = token
        cachedPassword = server.password
        return token to salt
    }

    fun getStreamUrl(
        server: ServerConfig,
        songId: String,
        format: String? = null,
        maxBitRate: Int? = null
    ): String {
        val params = mutableMapOf("id" to songId)
        if (format != null && format != "raw") params["format"] = format
        if (maxBitRate != null && maxBitRate > 0) params["maxBitRate"] = maxBitRate.toString()
        return buildUrl(server, "rest/stream", params)
    }

    fun getCoverArtUrl(server: ServerConfig, coverArtId: String, size: Int = 300): String {
        return buildUrl(server, "rest/getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()))
    }

    fun getDownloadUrl(
        server: ServerConfig,
        songId: String,
        format: String? = null,
        maxBitRate: Int? = null
    ): String {
        // Subsonic download endpoint doesn't support transcoding natively,
        // but stream endpoint with transcoding params works for this purpose
        if (format != null && format != "raw" || maxBitRate != null && maxBitRate > 0) {
            val params = mutableMapOf("id" to songId)
            if (format != null && format != "raw") params["format"] = format
            if (maxBitRate != null && maxBitRate > 0) params["maxBitRate"] = maxBitRate.toString()
            return buildUrl(server, "rest/stream", params)
        }
        return buildUrl(server, "rest/download", mapOf("id" to songId))
    }

    private fun buildUrl(server: ServerConfig, path: String, params: Map<String, String>): String {
        val baseUrl = server.url.trimEnd('/')
        val (token, salt) = getAuth(server)

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
