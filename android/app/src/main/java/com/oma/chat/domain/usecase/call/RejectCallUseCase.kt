package com.oma.chat.domain.usecase.call

import com.oma.chat.domain.repository.CallRepository
import javax.inject.Inject

class RejectCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(callerId: String) {
        callRepository.rejectCall(callerId)
    }
}
