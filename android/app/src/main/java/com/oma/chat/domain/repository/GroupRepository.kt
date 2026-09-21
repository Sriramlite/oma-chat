package com.oma.chat.domain.repository

import com.oma.chat.domain.model.Group
import com.oma.chat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    suspend fun createGroup(name: String, members: List<String>): Result<Group>
    fun getGroups(): Flow<List<Group>>
    fun getGroupById(groupId: String): Flow<Group?>
    suspend fun fetchGroups(): Result<List<Group>>
    suspend fun getGroupMembers(memberIds: List<String>): Result<List<User>>
    suspend fun manageGroup(groupId: String, memberId: String?, action: String): Result<String>
}
