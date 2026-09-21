package com.oma.chat.domain.usecase.group

import com.oma.chat.domain.model.Group
import com.oma.chat.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGroupByIdUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(groupId: String): Flow<Group?> {
        return groupRepository.getGroupById(groupId)
    }
}
