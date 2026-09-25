package com.oma.chat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class OmaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var updatePushTokenUseCase: UpdatePushTokenUseCase

    @Inject
    lateinit var authPreferences: AuthPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Sliding window rate limiter for High-Priority chat notifications (3 per minute per unique user)
        private val messageTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()
        private const val MAX_HIGH_PRIORITY_PER_MINUTE = 3
        private const val WINDOW_MILLIS = 60_000L

        private fun isHighPriorityAllowed(senderId: String): Boolean {
            if (senderId.isBlank()) return true
            val now = System.currentTimeMillis()
            val deque = messageTimestamps.computeIfAbsent(senderId) { ArrayDeque() }
            synchronized(deque) {
                while (deque.isNotEmpty() && now - deque.first() > WINDOW_MILLIS) {
                    deque.removeFirst()
                }
                return if (deque.size < MAX_HIGH_PRIORITY_PER_MINUTE) {
                    deque.addLast(now)
                    true
                } else {
                    false
                }
            }
        }
    }

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
        val title = data["title"] ?: data["senderName"] ?: remoteMessage.notification?.title ?: "OMA-CHAT"
        val body = data["body"] ?: data["content"] ?: remoteMessage.notification?.body ?: "New message"

        if (type == "call_offer") {
            val callerName = data["callerName"] ?: title
            val callerId = data["callerId"] ?: ""
            val callerAvatar = data["callerAvatar"] ?: ""
            val callType = data["callType"] ?: "voice"
            val sdp = data["sdp"] ?: ""
            showIncomingCallNotification(callerName, callerId, callerAvatar, callType, sdp)
        } else {
            val senderId = data["senderId"] ?: data["chatId"] ?: ""
            val senderName = data["senderName"] ?: title
            showChatNotification(senderName, body, senderId)
        }
    }

    private fun showChatNotification(title: String, message: String, senderId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isHighPriority = isHighPriorityAllowed(senderId)

        val highPriorityChannelId = "chat_high_priority_v3"
        val normalPriorityChannelId = "chat_normal_priority_v3"
        val activeChannelId = if (isHighPriority) highPriorityChannelId else normalPriorityChannelId

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val highChannel = NotificationChannel(
                highPriorityChannelId,
                "Priority Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority heads-up message alerts (up to 3 per min)"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(soundUri, audioAttributes)
            }

            val normalChannel = NotificationChannel(
                normalPriorityChannelId,
                "Standard Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard message notifications"
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(highChannel)
            notificationManager.createNotificationChannel(normalChannel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val notificationId = if (senderId.isNotBlank()) senderId.hashCode() else System.currentTimeMillis().toInt()
        val notificationBuilder = NotificationCompat.Builder(this, activeChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)

        if (isHighPriority) {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(defaultSoundUri)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
        } else {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun showIncomingCallNotification(
        callerName: String,
        callerId: String,
        callerAvatar: String,
        callType: String,
        sdp: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "call_channel_high_priority_v3"
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video call notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap on notification body -> Opens incoming call screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incomingCall", true)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("callType", callType)
            putExtra("sdp", sdp)
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incomingCall", true)
            putExtra("autoAccept", true)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            putExtra("callerAvatar", callerAvatar)
            putExtra("callType", callType)
            putExtra("sdp", sdp)
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

        val formattedCallType = callType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $formattedCallType Call")
            .setContentText("$callerName is calling you...")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$callerName is calling you..."))
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 1000, 1000, 1000))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .addAction(declineAction)
            .addAction(answerAction)

        notificationManager.notify(com.oma.chat.receiver.NotificationActionReceiver.NOTIFICATION_ID_CALL, notificationBuilder.build())
    }
}
