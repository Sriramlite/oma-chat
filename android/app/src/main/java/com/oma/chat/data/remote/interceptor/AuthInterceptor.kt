package com.oma.chat.data.remote.interceptor

import com.oma.chat.data.local.preferences.AuthPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authPreferences: AuthPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = authPreferences.getToken()

        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        if (!token.isNullOrBlank() && !originalRequest.headers.names().contains("Authorization")) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Automatically handle expired tokens
        if (response.code == 401) {
            val path = originalRequest.url.encodedPath
            // Don't auto-clear if it was a login attempt failure
            if (!path.contains("/auth/login") && !path.contains("/auth/signup")) {
                authPreferences.clear()
            }
        }

        return response
    }
}
