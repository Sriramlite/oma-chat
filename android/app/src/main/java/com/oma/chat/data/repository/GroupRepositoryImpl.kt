package com.oma.chat.data.repository

import com.oma.chat.data.local.dao.ConversationDao
import com.oma.chat.data.local.dao.GroupDao
import com.oma.chat.data.local.dao.UserDao
import com.oma.chat.data.local.entity.ConversationEntity
import com.oma.chat.data.local.entity.GroupEntity
import com.oma.chat.data.local.entity.UserEntity
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.BatchUsersRequest
import com.oma.chat.data.remote.api.GroupApi
import com.oma.chat.data.remote.api.UserApi
import com.oma.chat.data.remote.dto.CreateGroupRequest
import com.oma.chat.data.remote.dto.ManageGroupRequest
import com.oma.chat.di.IoDispatcher
import com.oma.chat.domain.model.Group
import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.GroupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupApi: GroupApi,
    private val groupDao: GroupDao,
    private val userApi: UserApi,
    private val userDao: UserDao,
    private val conversationDao: ConversationDao,
    private val authPreferences: AuthPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : GroupRepository {

    override suspend fun createGroup(name: String, members: List<String>): Result<Group> = withContext(ioDispatcher) {
        try {
            val ownerUserId = authPreferences.getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
            val response = groupApi.createGroup(CreateGroupRequest(name = name.trim(), members = members))
            if (response.isSuccessful && response.body() != null) {
                val groupDto = response.body()!!
                val group = groupDto.toDomain()

                // Save to local Room groups
                groupDao.insertGroup(
                    GroupEntity(
                        id = group.id,
                        name = group.name,
                        avatar = group.avatar,
                        adminIds = group.adminIds,
                        members = group.members,
                        created = group.created
                    )
                )

                // Add to local conversations for recent chats preview
                conversationDao.insertConversation(
                    ConversationEntity(
                        ownerUserId = ownerUserId,
                        id = group.id,
                        name = group.name,
                        avatar = group.avatar,
                        lastMsg = "Group created",
                        timestamp = group.created,
                        type = "group",
                        unreadCount = 0
                    )
                )

                Result.success(group)
            } else {
                Result.failure(Exception("Failed to create group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getGroups(): Flow<List<Group>> {
        return groupDao.getAllGroupsFlow().map { entities ->
            entities.map { entity ->
                Group(
                    id = entity.id,
                    name = entity.name,
                    avatar = entity.avatar,
                    adminIds = entity.adminIds,
                    members = entity.members,
                    created = entity.created
                )
            }
        }
    }

    override fun getGroupById(groupId: String): Flow<Group?> {
        return groupDao.getGroupByIdFlow(groupId).map { entity ->
            entity?.let {
                Group(
                    id = it.id,
                    name = it.name,
                    avatar = it.avatar,
                    adminIds = it.adminIds,
                    members = it.members,
                    created = it.created
                )
            }
        }
    }

    override suspend fun fetchGroups(): Result<List<Group>> = withContext(ioDispatcher) {
        try {
            val ownerUserId = authPreferences.getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
            val response = groupApi.getGroups()
            if (response.isSuccessful && response.body() != null) {
                val groupDtos = response.body()!!
                val groups = groupDtos.map { it.toDomain() }

                val entities = groups.map { g ->
                    GroupEntity(
                        id = g.id,
                        name = g.name,
                        avatar = g.avatar,
                        adminIds = g.adminIds,
                        members = g.members,
                        created = g.created
                    )
                }
                groupDao.insertGroups(entities)

                // Sync with recent chats table
                groups.forEach { g ->
                    val existing = conversationDao.getConversationById(ownerUserId, g.id)
                    conversationDao.insertConversation(
                        ConversationEntity(
                            ownerUserId = ownerUserId,
                            id = g.id,
                            name = g.name,
                            avatar = g.avatar,
                            lastMsg = g.lastMsg ?: existing?.lastMsg ?: "Group",
                            timestamp = g.lastTimestamp ?: existing?.timestamp ?: g.created,
                            type = "group",
                            unreadCount = existing?.unreadCount ?: 0
                        )
                    )
                }

                Result.success(groups)
            } else {
                Result.failure(Exception("Failed to fetch groups: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupMembers(memberIds: List<String>): Result<List<User>> = withContext(ioDispatcher) {
        try {
            if (memberIds.isEmpty()) return@withContext Result.success(emptyList())
            
            // Try fetching from remote batch users API first
            val response = userApi.batchGetUsers(BatchUsersRequest(memberIds))
            if (response.isSuccessful && response.body() != null) {
                val userDtos = response.body()!!
                val users = userDtos.map { it.toDomain() }
                
                // Cache them locally
                userDao.insertUsers(users.map { u ->
                    UserEntity(
                        id = u.id,
                        username = u.username,
                        name = u.name,
                        avatar = u.avatar,
                        bio = u.bio,
                        lastSeen = u.lastSeen,
                        isBlocked = u.isBlocked
                    )
                })
                return@withContext Result.success(users)
            }

            // Fallback to local DB cache
            val localUsers = userDao.getUsersByIds(memberIds).map { u ->
                User(
                    id = u.id,
                    username = u.username,
                    name = u.name,
                    avatar = u.avatar,
                    bio = u.bio,
                    lastSeen = u.lastSeen,
                    isBlocked = u.isBlocked
                )
            }
            Result.success(localUsers)
        } catch (e: Exception) {
            // If network fails, try local DB
            try {
                val localUsers = userDao.getUsersByIds(memberIds).map { u ->
                    User(
                        id = u.id,
                        username = u.username,
                        name = u.name,
                        avatar = u.avatar,
                        bio = u.bio,
                        lastSeen = u.lastSeen,
                        isBlocked = u.isBlocked
                    )
                }
                Result.success(localUsers)
            } catch (fallbackError: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun manageGroup(
        groupId: String,
        memberId: String?,
        action: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val ownerUserId = authPreferences.getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
            val response = groupApi.manageGroup(
                ManageGroupRequest(
                    groupId = groupId,
                    memberId = memberId,
                    action = action
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val message = response.body()!!.message ?: "Success"

                if (action == "leave") {
                    conversationDao.deleteConversation(ownerUserId, groupId)
                    groupDao.deleteGroup(groupId)
                } else {
                    fetchGroups()
                }

                Result.success(message)
            } else {
                Result.failure(Exception("Group management failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
