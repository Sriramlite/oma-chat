package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.model.Group
import com.oma.chat.domain.repository.GroupRepository
import javax.inject.Inject

class FetchGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(): Result<List<Group>> {
        return groupRepository.fetchGroups()
    }
}
