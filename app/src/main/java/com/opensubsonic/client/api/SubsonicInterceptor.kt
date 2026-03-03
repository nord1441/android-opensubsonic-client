package com.opensubsonic.client.api

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.util.UUID

class SubsonicInterceptor(
    private val usernameProvider: () -> String,
    private val passwordProvider: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalUrl = original.url

        val salt = generateSalt()
        val token = md5("${passwordProvider()}$salt")

        val url = originalUrl.newBuilder()
            .addQueryParameter("u", usernameProvider())
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "SubTune")
            .addQueryParameter("f", "json")
            .build()

        val request = original.newBuilder()
            .url(url)
            .build()

        return chain.proceed(request)
    }

    private fun generateSalt(): String {
        return UUID.randomUUID().toString().replace("-", "").take(12)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
