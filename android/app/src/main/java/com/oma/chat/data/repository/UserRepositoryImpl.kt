package com.oma.chat.data.repository

import com.oma.chat.data.local.dao.UserDao
import com.oma.chat.data.local.entity.UserEntity
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.AuthApi
import com.oma.chat.data.remote.api.BlockUserRequest
import com.oma.chat.data.remote.api.PushTokenRequest
import com.oma.chat.data.remote.api.ReportUserRequest
import com.oma.chat.data.remote.api.UpdateProfileRequest
import com.oma.chat.data.remote.api.UserApi
import com.oma.chat.data.remote.dto.ChangePasswordRequest
import com.oma.chat.data.remote.dto.DeleteAccountRequest
import com.oma.chat.di.IoDispatcher
import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val authApi: AuthApi,
    private val userDao: UserDao,
    private val authPreferences: AuthPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UserRepository {

    override fun getCurrentUser(): Flow<User?> {
        val ownerUserId = authPreferences.getUserId() ?: ""
        return userDao.getUserByIdFlow(ownerUserId).map { entity ->
            entity?.let {
                User(
                    id = it.id,
                    username = it.username,
                    name = it.name,
                    avatar = it.avatar,
                    bio = it.bio,
                    lastSeen = it.lastSeen,
                    isBlocked = it.isBlocked
                )
            } ?: authPreferences.getUser()
        }
    }

    override suspend fun fetchMe(): Result<User> = withContext(ioDispatcher) {
        try {
            val response = userApi.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!.toDomain()
                authPreferences.updateUserData(user)
                userDao.insertUser(
                    UserEntity(
                        id = user.id,
                        username = user.username,
                        name = user.name,
                        avatar = user.avatar,
                        bio = user.bio,
                        lastSeen = user.lastSeen
                    )
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to fetch profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(
        name: String?,
        bio: String?,
        avatar: String?
    ): Result<User> = withContext(ioDispatcher) {
        try {
            val response = userApi.updateProfile(
                UpdateProfileRequest(
                    name = name,
                    bio = bio,
                    avatar = avatar
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!.toDomain()
                authPreferences.updateUserData(user)
                userDao.insertUser(
                    UserEntity(
                        id = user.id,
                        username = user.username,
                        name = user.name,
                        avatar = user.avatar,
                        bio = user.bio,
                        lastSeen = user.lastSeen
                    )
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to update profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePrivacySettings(
        settings: com.oma.chat.domain.model.UserPrivacySettings
    ): Result<User> = withContext(ioDispatcher) {
        try {
            val settingsMap = mapOf<String, Any?>(
                "lastSeenPrivacy" to settings.lastSeenPrivacy,
                "profilePhotoPrivacy" to settings.profilePhotoPrivacy,
                "aboutPrivacy" to settings.aboutPrivacy,
                "readReceipts" to settings.readReceipts,
                "shareBattery" to settings.shareBattery
            )
            val response = userApi.updateProfile(
                UpdateProfileRequest(
                    settings = settingsMap
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!.toDomain()
                authPreferences.updateUserData(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to update privacy settings: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateBattery(batteryLevel: Int): Result<Unit> = withContext(ioDispatcher) {
        try {
            userApi.updateProfile(UpdateProfileRequest(battery = batteryLevel))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBlockedUsers(): Result<List<User>> = withContext(ioDispatcher) {
        try {
            // First refresh me to ensure we have latest blocked IDs
            val meResult = fetchMe()
            val blockedIds = if (meResult.isSuccess) {
                meResult.getOrNull()?.blockedUsers ?: emptyList()
            } else {
                authPreferences.getUser()?.blockedUsers ?: emptyList()
            }

            if (blockedIds.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val response = userApi.batchGetUsers(com.oma.chat.data.remote.api.BatchUsersRequest(ids = blockedIds))
            if (response.isSuccessful && response.body() != null) {
                val users = response.body()!!.map { it.toDomain() }
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to load blocked users: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val response = authApi.changePassword(
                ChangePasswordRequest(
                    oldPassword = oldPassword,
                    newPassword = newPassword
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Password changed successfully")
            } else {
                Result.failure(Exception("Failed to change password: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(password: String): Result<String> = withContext(ioDispatcher) {
        try {
            val response = authApi.deleteAccount(DeleteAccountRequest(password = password))
            if (response.isSuccessful && response.body() != null) {
                authPreferences.clear()
                Result.success(response.body()!!.message ?: "Account deleted")
            } else {
                Result.failure(Exception("Failed to delete account: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockUser(
        userId: String,
        action: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val response = userApi.blockUser(BlockUserRequest(userId = userId, action = action))
            if (response.isSuccessful && response.body() != null) {
                userDao.updateBlockedStatus(userId, action == "block")
                Result.success(response.body()!!.message ?: "User $action-ed successfully")
            } else {
                Result.failure(Exception("Failed to $action user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportUser(
        targetedUserId: String,
        reason: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val response = userApi.reportUser(
                ReportUserRequest(
                    targetedUserId = targetedUserId,
                    reason = reason
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Report submitted")
            } else {
                Result.failure(Exception("Failed to submit report: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePushToken(token: String): Result<String> = withContext(ioDispatcher) {
        try {
            val response = userApi.updatePushToken(PushTokenRequest(token = token))
            if (response.isSuccessful && response.body() != null) {
                Result.success("Push token updated")
            } else {
                Result.failure(Exception("Failed to update push token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
