package com.oma.chat.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.repository.CallRepository
import com.oma.chat.domain.usecase.chat.MarkChatReadUseCase
import com.oma.chat.domain.usecase.chat.SendMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sendMessageUseCase: SendMessageUseCase

    @Inject
    lateinit var markChatReadUseCase: MarkChatReadUseCase

    @Inject
    lateinit var callRepository: CallRepository

    @Inject
    lateinit var authPreferences: AuthPreferences

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ACTION_DIRECT_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
                val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
                val ownerUserId = authPreferences.getUserId() ?: ""

                if (replyText.isNullOrBlank() || chatId.isBlank() || ownerUserId.isBlank()) {
                    return
                }

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        sendMessageUseCase(
                            ownerUserId = ownerUserId,
                            receiverId = chatId,
                            content = replyText,
                            type = "text"
                        )
                        notificationManager.cancel(chatId.hashCode())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_MARK_AS_READ -> {
                val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
                val ownerUserId = authPreferences.getUserId() ?: ""

                notificationManager.cancel(chatId.hashCode())

                if (chatId.isNotBlank() && ownerUserId.isNotBlank()) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            markChatReadUseCase(ownerUserId, chatId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_DECLINE_CALL -> {
                val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: ""
                notificationManager.cancel(NOTIFICATION_ID_CALL)

                if (callerId.isNotBlank()) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            callRepository.rejectCall(callerId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "com.oma.chat.ACTION_DIRECT_REPLY"
        const val ACTION_MARK_AS_READ = "com.oma.chat.ACTION_MARK_AS_READ"
        const val ACTION_DECLINE_CALL = "com.oma.chat.ACTION_DECLINE_CALL"
        const val ACTION_ANSWER_CALL = "com.oma.chat.ACTION_ANSWER_CALL"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CHAT_NAME = "extra_chat_name"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALL_TYPE = "extra_call_type"

        const val NOTIFICATION_ID_CALL = 1001
    }
}
