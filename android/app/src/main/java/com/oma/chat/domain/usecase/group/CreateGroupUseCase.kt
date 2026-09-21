package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.model.Group
import com.oma.chat.domain.repository.GroupRepository
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(name: String, members: List<String>): Result<Group> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name cannot be blank"))
        }
        if (members.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least one member"))
        }
        return groupRepository.createGroup(name.trim(), members)
    }
}
