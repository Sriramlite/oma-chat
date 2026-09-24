package com.oma.chat.domain.repository

import com.oma.chat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): Flow<User?>
    suspend fun fetchMe(): Result<User>
    suspend fun updateProfile(name: String?, bio: String?, avatar: String?): Result<User>
    suspend fun updatePrivacySettings(settings: com.oma.chat.domain.model.UserPrivacySettings): Result<User>
    suspend fun updateBattery(batteryLevel: Int, isCharging: Boolean = false): Result<Unit>
    suspend fun getBlockedUsers(): Result<List<User>>
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<String>
    suspend fun deleteAccount(password: String): Result<String>
    suspend fun blockUser(userId: String, action: String): Result<String>
    suspend fun reportUser(targetedUserId: String, reason: String): Result<String>
    suspend fun updatePushToken(token: String): Result<String>
}
