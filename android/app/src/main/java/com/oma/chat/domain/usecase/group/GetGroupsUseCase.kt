package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.model.Group
import com.oma.chat.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(): Flow<List<Group>> {
        return groupRepository.getGroups()
    }
}
