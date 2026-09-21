package com.oma.chat.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oma.chat.data.local.dao.MessageDao
import com.oma.chat.data.local.dao.SyncQueueDao
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.ChatApi
import com.oma.chat.data.remote.dto.SendMessageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OutgoingMessageWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val messageDao: MessageDao,
    private val chatApi: ChatApi,
    private val authPreferences: AuthPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ownerUserId = authPreferences.getUserId() ?: return Result.success()
        val pendingQueue = syncQueueDao.getPendingQueueList(ownerUserId)

        if (pendingQueue.isEmpty()) {
            return Result.success()
        }

        for (syncItem in pendingQueue) {
            try {
                // Section 10 Safe Pre-Check:
                // Check recent chat history to verify if the message was already ingested during previous connection blips
                val historyResponse = chatApi.getChatHistory(
                    chatId = syncItem.receiverId,
                    since = syncItem.createdAt - 60000L
                )

                var alreadyIngestedId: String? = null
                if (historyResponse.isSuccessful && historyResponse.body() != null) {
                    val messages = historyResponse.body()!!
                    val matched = messages.firstOrNull { 
                        (it.tempId == syncItem.tempId) || (it.content == syncItem.content && it.senderId == ownerUserId)
                    }
                    if (matched != null) {
                        alreadyIngestedId = matched.id
                    }
                }

                if (alreadyIngestedId != null) {
                    // Update local message with server UUID and mark sent
                    val localMsg = messageDao.getMessageByTempId(ownerUserId, syncItem.tempId)
                    if (localMsg != null) {
                        messageDao.deleteMessage(ownerUserId, localMsg.id)
                        messageDao.insertMessage(
                            localMsg.copy(
                                id = alreadyIngestedId,
                                status = "sent"
                            )
                        )
                    }
                    syncQueueDao.removeFromQueue(syncItem.tempId)
                } else {
                    // Send message via REST API
                    val response = chatApi.sendMessage(
                        SendMessageRequest(
                            content = syncItem.content,
                            type = syncItem.type,
                            receiverId = syncItem.receiverId,
                            replyToId = syncItem.replyToId,
                            tempId = syncItem.tempId
                        )
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val serverMsg = response.body()!!
                        val localMsg = messageDao.getMessageByTempId(ownerUserId, syncItem.tempId)
                        if (localMsg != null) {
                            messageDao.deleteMessage(ownerUserId, localMsg.id)
                            messageDao.insertMessage(
                                localMsg.copy(
                                    id = serverMsg.id,
                                    status = "sent"
                                )
                            )
                        }
                        syncQueueDao.removeFromQueue(syncItem.tempId)
                    } else {
                        syncQueueDao.incrementRetryCount(syncItem.tempId)
                    }
                }
            } catch (e: Exception) {
                syncQueueDao.incrementRetryCount(syncItem.tempId)
            }
        }

        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OutgoingMessageWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
