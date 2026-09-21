package com.oma.chat.domain.usecase.call

import com.oma.chat.domain.model.CallType
import com.oma.chat.domain.repository.CallRepository
import javax.inject.Inject

class AcceptCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(callerId: String, sdp: String, callType: CallType) {
        callRepository.acceptCall(callerId, sdp, callType)
    }
}
