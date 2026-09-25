package com.oma.chat.data.repository

import com.google.gson.Gson
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.AuthApi
import com.oma.chat.data.remote.dto.AuthResponseDto
import com.oma.chat.data.remote.dto.CompleteProfileRequest
import com.oma.chat.data.remote.dto.ForgotPasswordRequest
import com.oma.chat.data.remote.dto.GoogleAuthRequest
import com.oma.chat.data.remote.dto.LoginRequest
import com.oma.chat.data.remote.dto.PhoneAuthRequest
import com.oma.chat.data.remote.dto.ResetPasswordRequest
import com.oma.chat.data.remote.dto.SignupRequest
import com.oma.chat.data.remote.dto.SimpleMessageResponseDto
import com.oma.chat.di.IoDispatcher
import com.oma.chat.domain.model.AuthSession
import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<AuthSession> = withContext(ioDispatcher) {
        try {
            val response = authApi.login(LoginRequest(username = username.trim(), password = password))
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signup(
        username: String,
        password: String,
        name: String,
        phone: String
    ): Result<AuthSession> = withContext(ioDispatcher) {
        try {
            val response = authApi.signup(
                SignupRequest(
                    username = username.trim(),
                    password = password,
                    name = name.trim(),
                    phone = phone.trim()
                )
            )
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun googleAuth(idToken: String): Result<AuthSession> = withContext(ioDispatcher) {
        try {
            val response = authApi.googleAuth(GoogleAuthRequest(idToken = idToken))
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun phoneAuth(idToken: String): Result<AuthSession> = withContext(ioDispatcher) {
        try {
            val response = authApi.phoneAuth(PhoneAuthRequest(idToken = idToken))
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(username: String): Result<String> = withContext(ioDispatcher) {
        try {
            val response = authApi.forgotPassword(ForgotPasswordRequest(username = username.trim()))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Password reset instructions sent")
            } else {
                val errorMsg = parseErrorMessage(response)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(
        username: String,
        otp: String,
        newPassword: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val response = authApi.resetPassword(
                ResetPasswordRequest(
                    username = username.trim(),
                    otp = otp.trim(),
                    newPassword = newPassword
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Password updated successfully")
            } else {
                val errorMsg = parseErrorMessage(response)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeProfile(username: String, phone: String): Result<User> = withContext(ioDispatcher) {
        try {
            val response = authApi.completeProfile(
                CompleteProfileRequest(
                    username = username.trim(),
                    phone = phone.trim()
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val domainUser = body.user.toDomain()
                authPreferences.saveAuthSession(body.token, domainUser)
                Result.success(domainUser)
            } else {
                val errorMsg = parseErrorMessage(response)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() = withContext(ioDispatcher) {
        authPreferences.clear()
    }

    override fun getAuthState(): Flow<Boolean> {
        return authPreferences.isLoggedInState
    }

    override fun getCurrentUser(): Flow<User?> {
        return authPreferences.currentUserState
    }

    override fun getStoredToken(): String? {
        return authPreferences.getToken()
    }

    private fun handleAuthResponse(response: Response<AuthResponseDto>): Result<AuthSession> {
        return if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val domainUser = body.user.toDomain()
            authPreferences.saveAuthSession(token = body.token, user = domainUser)
            Result.success(
                AuthSession(
                    token = body.token,
                    user = domainUser,
                    isNew = body.isNew == true
                )
            )
        } else {
            val errorMsg = parseErrorMessage(response)
            Result.failure(Exception(errorMsg))
        }
    }

    private fun <T> parseErrorMessage(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val parsed = gson.fromJson(errorBody, SimpleMessageResponseDto::class.java)
                parsed.error ?: parsed.message ?: "Server error (${response.code()})"
            } else {
                "Server error (${response.code()})"
            }
        } catch (e: Exception) {
            "Server error (${response.code()})"
        }
    }
}
