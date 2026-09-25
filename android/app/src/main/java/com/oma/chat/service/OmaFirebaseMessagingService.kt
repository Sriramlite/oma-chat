package com.oma.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.oma.chat.MainActivity
import com.oma.chat.R
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.domain.usecase.user.UpdatePushTokenUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OmaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var updatePushTokenUseCase: UpdatePushTokenUseCase

    @Inject
    lateinit var authPreferences: AuthPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (authPreferences.hasToken()) {
            serviceScope.launch {
                updatePushTokenUseCase(token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data

        val type = data["type"] ?: "message"
        val title = remoteMessage.notification?.title ?: data["title"] ?: "OMA-CHAT"
        val body = remoteMessage.notification?.body ?: data["body"] ?: data["content"] ?: "New message"

        if (type == "call_offer") {
            val callerName = data["callerName"] ?: "Someone"
            val callerId = data["callerId"] ?: ""
            val callType = data["callType"] ?: "voice"
            showIncomingCallNotification(callerName, callerId, callType)
        } else {
            val senderId = data["senderId"] ?: ""
            val senderName = data["senderName"] ?: title
            showChatNotification(senderName, body, senderId)
        }
    }

    private fun showChatNotification(title: String, message: String, senderId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "chat_messages_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct and group chat message notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", senderId)
            putExtra("chatName", title)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Reply RemoteInput Action
        val remoteInput = androidx.core.app.RemoteInput.Builder(com.oma.chat.receiver.NotificationActionReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply to $title...")
            .build()

        val replyIntent = Intent(this, com.oma.chat.receiver.NotificationActionReceiver::class.java).apply {
            action = com.oma.chat.receiver.NotificationActionReceiver.ACTION_DIRECT_REPLY
            putExtra(com.oma.chat.receiver.NotificationActionReceiver.EXTRA_CHAT_ID, senderId)
            putExtra(com.oma.chat.receiver.NotificationActionReceiver.EXTRA_CHAT_NAME, title)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            senderId.hashCode() + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Mark as Read Action
        val markReadIntent = Intent(this, com.oma.chat.receiver.NotificationActionReceiver::class.java).apply {
            action = com.oma.chat.receiver.NotificationActionReceiver.ACTION_MARK_AS_READ
            putExtra(com.oma.chat.receiver.NotificationActionReceiver.EXTRA_CHAT_ID, senderId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            this,
            senderId.hashCode() + 2,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Mark as Read",
            markReadPendingIntent
        ).build()

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)

        notificationManager.notify(senderId.hashCode(), notificationBuilder.build())
    }

    private fun showIncomingCallNotification(callerName: String, callerId: String, callType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "call_channel_v3"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video call notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap on notification body -> Opens incoming call screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incomingCall", true)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callType", callType)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer Action Button
        val answerIntent = Intent(this, MainActivity::class.java).apply {
            action = com.oma.chat.receiver.NotificationActionReceiver.ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incomingCall", true)
            putExtra("autoAccept", true)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callType", callType)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Answer",
            answerPendingIntent
        ).build()

        // Decline Action Button
        val declineIntent = Intent(this, com.oma.chat.receiver.NotificationActionReceiver::class.java).apply {
            action = com.oma.chat.receiver.NotificationActionReceiver.ACTION_DECLINE_CALL
            putExtra(com.oma.chat.receiver.NotificationActionReceiver.EXTRA_CALLER_ID, callerId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            1003,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declineAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Decline",
            declinePendingIntent
        ).build()

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming ${callType.replaceFirstChar { it.uppercase() }} Call")
            .setContentText("$callerName is calling you...")
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(declineAction)
            .addAction(answerAction)

        notificationManager.notify(com.oma.chat.receiver.NotificationActionReceiver.NOTIFICATION_ID_CALL, notificationBuilder.build())
    }
}
