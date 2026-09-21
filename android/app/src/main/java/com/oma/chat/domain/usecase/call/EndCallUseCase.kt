package com.oma.chat.domain.usecase.call

import com.oma.chat.domain.repository.CallRepository
import javax.inject.Inject

class EndCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke() {
        callRepository.endCall()
    }
}
