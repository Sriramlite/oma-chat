package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.GroupRepository
import javax.inject.Inject

class GetGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(memberIds: List<String>): Result<List<User>> {
        return groupRepository.getGroupMembers(memberIds)
    }
}
