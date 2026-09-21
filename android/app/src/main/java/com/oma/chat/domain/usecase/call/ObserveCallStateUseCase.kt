package com.oma.chat.domain.usecase.call

import com.oma.chat.domain.model.CallState
import com.oma.chat.domain.repository.CallRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveCallStateUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(): StateFlow<CallState> {
        return callRepository.callState
    }
}
