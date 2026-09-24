package com.oma.chat.data.remote.socket

import com.google.gson.Gson
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.dto.ChatMessageDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val gson: Gson
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _connectionState = MutableStateFlow<SocketConnectionState>(SocketConnectionState.Disconnected)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessageDto>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<ChatMessageDto> = _incomingMessages.asSharedFlow()

    private val _userStatusEvents = MutableSharedFlow<UserStatusEvent>(extraBufferCapacity = 64)
    val userStatusEvents: SharedFlow<UserStatusEvent> = _userStatusEvents.asSharedFlow()

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    private val _callOffers = MutableSharedFlow<CallOfferEvent>(extraBufferCapacity = 16)
    val callOffers: SharedFlow<CallOfferEvent> = _callOffers.asSharedFlow()

    private val _callAnswers = MutableSharedFlow<CallAnswerEvent>(extraBufferCapacity = 16)
    val callAnswers: SharedFlow<CallAnswerEvent> = _callAnswers.asSharedFlow()

    private val _iceCandidates = MutableSharedFlow<IceCandidateEvent>(extraBufferCapacity = 64)
    val iceCandidates: SharedFlow<IceCandidateEvent> = _iceCandidates.asSharedFlow()

    private val _endCallEvents = MutableSharedFlow<EndCallEvent>(extraBufferCapacity = 16)
    val endCallEvents: SharedFlow<EndCallEvent> = _endCallEvents.asSharedFlow()

    private val _callUnreachableEvents = MutableSharedFlow<CallUnreachableEvent>(extraBufferCapacity = 16)
    val callUnreachableEvents: SharedFlow<CallUnreachableEvent> = _callUnreachableEvents.asSharedFlow()

    private val _messageEditedEvents = MutableSharedFlow<MessageEditedEvent>(extraBufferCapacity = 64)
    val messageEditedEvents: SharedFlow<MessageEditedEvent> = _messageEditedEvents.asSharedFlow()

    private val _messageDeletedEvents = MutableSharedFlow<MessageDeletedEvent>(extraBufferCapacity = 64)
    val messageDeletedEvents: SharedFlow<MessageDeletedEvent> = _messageDeletedEvents.asSharedFlow()

    private val _chatDeletedEvents = MutableSharedFlow<ChatDeletedEvent>(extraBufferCapacity = 32)
    val chatDeletedEvents: SharedFlow<ChatDeletedEvent> = _chatDeletedEvents.asSharedFlow()

    private val _messageStarredEvents = MutableSharedFlow<MessageStarredEvent>(extraBufferCapacity = 64)
    val messageStarredEvents: SharedFlow<MessageStarredEvent> = _messageStarredEvents.asSharedFlow()

    private val _messagePinnedEvents = MutableSharedFlow<MessagePinnedEvent>(extraBufferCapacity = 64)
    val messagePinnedEvents: SharedFlow<MessagePinnedEvent> = _messagePinnedEvents.asSharedFlow()

    fun connect() {
        if (socket?.connected() == true) return

        val userId = authPreferences.getUserId() ?: return
        val token = authPreferences.getToken()

        try {
            _connectionState.value = SocketConnectionState.Connecting

            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000
                transports = arrayOf("websocket", "polling")
                if (!token.isNullOrBlank()) {
                    auth = mapOf("token" to token)
                }
            }

            socket = IO.socket("https://api.pdktdev.in", options).apply {
                on(Socket.EVENT_CONNECT) {
                    _connectionState.value = SocketConnectionState.Connected
                    // Emit join event with userId to register presence and room
                    emit("join", userId)
                }

                on(Socket.EVENT_DISCONNECT) {
                    _connectionState.value = SocketConnectionState.Disconnected
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val errorMsg = args.firstOrNull()?.toString() ?: "Connection error"
                    _connectionState.value = SocketConnectionState.Error(errorMsg)
                }

                on("receive_message") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val jsonString = data.toString()
                            val msgDto = gson.fromJson(jsonString, ChatMessageDto::class.java)
                            scope.launch { _incomingMessages.emit(msgDto) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("message_edited") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val messageId = json.optString("messageId")
                            val chatId = json.optString("chatId")
                            val senderId = json.optString("senderId")
                            val receiverId = json.optString("receiverId")
                            val newContent = json.optString("newContent")
                            val isEdited = json.optBoolean("isEdited", true)

                            if (messageId.isNotBlank()) {
                                scope.launch {
                                    _messageEditedEvents.emit(
                                        MessageEditedEvent(
                                            messageId = messageId,
                                            chatId = chatId,
                                            senderId = senderId,
                                            receiverId = receiverId,
                                            newContent = newContent,
                                            isEdited = isEdited
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("message_deleted") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val messageId = json.optString("messageId")
                            val chatId = json.optString("chatId")
                            val senderId = json.optString("senderId")
                            val receiverId = json.optString("receiverId")
                            val mode = json.optString("mode", "everyone")
                            val deletedFor = if (json.has("deletedFor")) json.optString("deletedFor") else null
                            val content = json.optString("content", "🚫 This message was deleted")

                            if (messageId.isNotBlank()) {
                                scope.launch {
                                    _messageDeletedEvents.emit(
                                        MessageDeletedEvent(
                                            messageId = messageId,
                                            chatId = chatId,
                                            senderId = senderId,
                                            receiverId = receiverId,
                                            mode = mode,
                                            deletedFor = deletedFor,
                                            isDeleted = true,
                                            content = content
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("chat_deleted") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val chatId = json.optString("chatId")
                            val deletedBy = json.optString("deletedBy")
                            if (chatId.isNotBlank()) {
                                scope.launch {
                                    _chatDeletedEvents.emit(
                                        ChatDeletedEvent(
                                            chatId = chatId,
                                            deletedBy = deletedBy
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("message_starred") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val messageId = json.optString("messageId")
                            val uId = json.optString("userId")
                            val isStarred = json.optBoolean("isStarred")
                            if (messageId.isNotBlank()) {
                                scope.launch {
                                    _messageStarredEvents.emit(
                                        MessageStarredEvent(
                                            messageId = messageId,
                                            userId = uId,
                                            isStarred = isStarred
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("message_pinned") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val messageId = json.optString("messageId")
                            val chatId = json.optString("chatId")
                            val isPinned = json.optBoolean("isPinned")
                            if (messageId.isNotBlank()) {
                                scope.launch {
                                    _messagePinnedEvents.emit(
                                        MessagePinnedEvent(
                                            messageId = messageId,
                                            chatId = chatId,
                                            isPinned = isPinned
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("user_status") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetUid = json.optString("userId")
                            val online = json.optBoolean("online", false)
                            val lastSeen = json.optLong("lastSeen", 0L)
                            
                            var batteryLevel: Int? = null
                            var isCharging = false
                            if (json.has("battery")) {
                                val bVal = json.opt("battery")
                                if (bVal is JSONObject) {
                                    if (bVal.has("level")) batteryLevel = bVal.optInt("level")
                                    isCharging = bVal.optBoolean("charging", false)
                                } else if (bVal is Number) {
                                    batteryLevel = bVal.toInt()
                                }
                            }

                            if (targetUid.isNotBlank()) {
                                val current = _onlineUsers.value.toMutableSet()
                                if (online) current.add(targetUid) else current.remove(targetUid)
                                _onlineUsers.value = current
                                scope.launch {
                                    _userStatusEvents.emit(
                                        UserStatusEvent(
                                            userId = targetUid,
                                            online = online,
                                            lastSeen = lastSeen,
                                            batteryLevel = batteryLevel,
                                            isCharging = isCharging
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("online_users") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val userIds = mutableSetOf<String>()
                            when (data) {
                                is JSONArray -> {
                                    for (i in 0 until data.length()) {
                                        userIds.add(data.getString(i))
                                    }
                                }
                                is String -> {
                                    val arr = JSONArray(data)
                                    for (i in 0 until arr.length()) {
                                        userIds.add(arr.getString(i))
                                    }
                                }
                            }
                            _onlineUsers.value = userIds
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("typing") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val senderId = json.optString("senderId")
                            val receiverId = json.optString("receiverId")
                            if (senderId.isNotBlank()) {
                                scope.launch {
                                    _typingEvents.emit(
                                        TypingEvent(
                                            senderId = senderId,
                                            receiverId = receiverId,
                                            isTyping = true
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("stop_typing") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val senderId = json.optString("senderId")
                            val receiverId = json.optString("receiverId")
                            if (senderId.isNotBlank()) {
                                scope.launch {
                                    _typingEvents.emit(
                                        TypingEvent(
                                            senderId = senderId,
                                            receiverId = receiverId,
                                            isTyping = false
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("offer") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetId = json.optString("targetId")
                            val callerId = json.optString("callerId")
                            val callerName = json.optString("callerName")
                            val callerAvatar = json.optString("callerAvatar")
                            val offerObj = json.optJSONObject("offer")
                            val sdp = if (offerObj != null) {
                                offerObj.optString("sdp")
                            } else {
                                json.optString("sdp")
                            }
                            val rawType = if (json.has("callType")) json.optString("callType") else json.optString("type", "video")
                            val type = if (rawType.equals("audio", ignoreCase = true) || rawType.equals("voice", ignoreCase = true)) "voice" else "video"
                            if (sdp.isNotBlank()) {
                                scope.launch {
                                    _callOffers.emit(
                                        CallOfferEvent(
                                            targetId = targetId,
                                            callerId = callerId,
                                            callerName = callerName,
                                            callerAvatar = callerAvatar,
                                            sdp = sdp,
                                            type = type
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("answer") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetId = json.optString("targetId")
                            val answerObj = json.optJSONObject("answer")
                            val sdp = if (answerObj != null) {
                                answerObj.optString("sdp")
                            } else {
                                json.optString("sdp")
                            }
                            val rawType = if (json.has("callType")) json.optString("callType") else json.optString("type", "video")
                            val type = if (rawType.equals("audio", ignoreCase = true) || rawType.equals("voice", ignoreCase = true)) "voice" else "video"
                            if (sdp.isNotBlank()) {
                                scope.launch {
                                    _callAnswers.emit(
                                        CallAnswerEvent(
                                            targetId = targetId,
                                            sdp = sdp,
                                            type = type
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("ice-candidate") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetId = json.optString("targetId")
                            val rawCandidate = json.opt("candidate")
                            
                            var candidateStr = ""
                            var sdpMid: String? = null
                            var sdpMLineIndex = 0

                            if (rawCandidate is JSONObject) {
                                candidateStr = rawCandidate.optString("candidate", "")
                                sdpMid = if (rawCandidate.has("sdpMid") && !rawCandidate.isNull("sdpMid")) {
                                    rawCandidate.getString("sdpMid")
                                } else null
                                sdpMLineIndex = rawCandidate.optInt("sdpMLineIndex", 0)
                            } else if (rawCandidate is String) {
                                val trimmed = rawCandidate.trim()
                                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                                    try {
                                        val innerJson = JSONObject(trimmed)
                                        candidateStr = innerJson.optString("candidate", "")
                                        sdpMid = if (innerJson.has("sdpMid") && !innerJson.isNull("sdpMid")) {
                                            innerJson.getString("sdpMid")
                                        } else null
                                        sdpMLineIndex = innerJson.optInt("sdpMLineIndex", 0)
                                    } catch (_: Exception) {
                                        candidateStr = trimmed
                                    }
                                } else {
                                    candidateStr = trimmed
                                }
                            }

                            if (candidateStr.isBlank() && json.has("sdp")) {
                                candidateStr = json.optString("sdp", "")
                            }
                            if (sdpMid == null && json.has("sdpMid") && !json.isNull("sdpMid")) {
                                sdpMid = json.getString("sdpMid")
                            }
                            if (sdpMLineIndex == 0 && json.has("sdpMLineIndex")) {
                                sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                            }

                            if (candidateStr.isNotBlank()) {
                                scope.launch {
                                    _iceCandidates.emit(
                                        IceCandidateEvent(
                                            targetId = targetId,
                                            sdpMid = sdpMid,
                                            sdpMLineIndex = sdpMLineIndex,
                                            candidate = candidateStr
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("end-call") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetId = json.optString("targetId")
                            val reason = json.optString("reason", "")
                            scope.launch {
                                _endCallEvents.emit(EndCallEvent(targetId = targetId, reason = reason))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                on("call_unreachable") { args ->
                    args.firstOrNull()?.let { data ->
                        try {
                            val json = when (data) {
                                is JSONObject -> data
                                else -> JSONObject(data.toString())
                            }
                            val targetId = json.optString("targetId")
                            val reason = json.optString("reason", "offline")
                            scope.launch {
                                _callUnreachableEvents.emit(CallUnreachableEvent(targetId = targetId, reason = reason))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            _connectionState.value = SocketConnectionState.Error(e.localizedMessage ?: "Socket init error")
        }
    }

    fun emitTyping(receiverId: String) {
        val senderId = authPreferences.getUserId() ?: return
        try {
            val json = JSONObject().apply {
                put("senderId", senderId)
                put("receiverId", receiverId)
            }
            socket?.emit("typing", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitStopTyping(receiverId: String) {
        val senderId = authPreferences.getUserId() ?: return
        try {
            val json = JSONObject().apply {
                put("senderId", senderId)
                put("receiverId", receiverId)
            }
            socket?.emit("stop_typing", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitCallOffer(
        targetId: String,
        sdp: String,
        type: String,
        callerName: String?,
        callerAvatar: String?
    ) {
        val callerId = authPreferences.getUserId() ?: return
        try {
            val offerObj = JSONObject().apply {
                put("type", "offer")
                put("sdp", sdp)
            }
            val wireType = if (type.equals("voice", ignoreCase = true) || type.equals("audio", ignoreCase = true)) "audio" else "video"
            val json = JSONObject().apply {
                put("targetId", targetId)
                put("callerId", callerId)
                put("callerName", callerName ?: "")
                put("callerAvatar", callerAvatar ?: "")
                put("offer", offerObj)
                put("sdp", sdp)
                put("type", wireType)
                put("callType", wireType)
            }
            socket?.emit("offer", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitCallAnswer(
        targetId: String,
        sdp: String,
        type: String
    ) {
        try {
            val answerObj = JSONObject().apply {
                put("type", "answer")
                put("sdp", sdp)
            }
            val wireType = if (type.equals("voice", ignoreCase = true) || type.equals("audio", ignoreCase = true)) "audio" else "video"
            val json = JSONObject().apply {
                put("targetId", targetId)
                put("answer", answerObj)
                put("sdp", sdp)
                put("type", wireType)
                put("callType", wireType)
            }
            socket?.emit("answer", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitIceCandidate(
        targetId: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        try {
            val candidateObj = JSONObject().apply {
                put("candidate", candidate)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            }
            val json = JSONObject().apply {
                put("targetId", targetId)
                put("candidate", candidateObj)
            }
            socket?.emit("ice-candidate", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitEndCall(targetId: String, reason: String = "ended") {
        try {
            val myId = authPreferences.getUserId() ?: ""
            val json = JSONObject().apply {
                put("targetId", targetId)
                put("callerId", myId)
                put("reason", reason)
            }
            socket?.emit("end-call", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitBattery(level: Int, charging: Boolean) {
        try {
            val json = JSONObject().apply {
                put("level", level)
                put("charging", charging)
            }
            socket?.emit("battery", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = SocketConnectionState.Disconnected
        _onlineUsers.value = emptySet()
    }
}
