package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.repository.GroupRepository
import javax.inject.Inject

class ManageGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend fun leaveGroup(groupId: String): Result<String> {
        return groupRepository.manageGroup(groupId, null, "leave")
    }

    suspend fun addMember(groupId: String, memberId: String): Result<String> {
        return groupRepository.manageGroup(groupId, memberId, "add")
    }

    suspend fun removeMember(groupId: String, memberId: String): Result<String> {
        return groupRepository.manageGroup(groupId, memberId, "remove")
    }

    suspend fun promoteAdmin(groupId: String, memberId: String): Result<String> {
        return groupRepository.manageGroup(groupId, memberId, "promote")
    }

    suspend fun demoteAdmin(groupId: String, memberId: String): Result<String> {
        return groupRepository.manageGroup(groupId, memberId, "demote")
    }
}
