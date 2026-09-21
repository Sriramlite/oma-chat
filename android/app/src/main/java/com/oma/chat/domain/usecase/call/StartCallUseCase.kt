package com.oma.chat.domain.usecase.call

import com.oma.chat.domain.model.CallType
import com.oma.chat.domain.repository.CallRepository
import javax.inject.Inject

class StartCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(targetId: String, targetName: String, targetAvatar: String, callType: CallType) {
        callRepository.startCall(targetId, targetName, targetAvatar, callType)
    }
}
