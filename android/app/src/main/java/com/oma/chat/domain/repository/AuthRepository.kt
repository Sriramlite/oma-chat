package com.oma.chat.domain.repository

import com.oma.chat.domain.model.AuthSession
import com.oma.chat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthSession>
    suspend fun signup(username: String, password: String, name: String, phone: String): Result<AuthSession>
    suspend fun googleAuth(idToken: String): Result<AuthSession>
    suspend fun phoneAuth(idToken: String): Result<AuthSession>
    suspend fun forgotPassword(username: String): Result<String>
    suspend fun resetPassword(username: String, otp: String, newPassword: String): Result<String>
    suspend fun completeProfile(username: String, phone: String): Result<User>
    suspend fun logout()
    fun getAuthState(): Flow<Boolean>
    fun getCurrentUser(): Flow<User?>
    fun getStoredToken(): String?
}
