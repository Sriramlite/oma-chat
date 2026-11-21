package com.oma.chat.data.mesh

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Lightweight envelope for peer-to-peer and relayed mesh packets.
 * Keys are short to minimize packet size over BLE.
 */
data class MeshPacket(
    @SerializedName("id")
    val packetId: String = UUID.randomUUID().toString(),

    @SerializedName("sid")
    val senderId: String,

    @SerializedName("sname")
    val senderName: String,

    @SerializedName("rid")
    val receiverId: String, // Target user UUID or "*" for broadcast

    @SerializedName("c")
    val content: String,

    @SerializedName("t")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("ttl")
    var ttl: Int = 4, // Max remaining hops

    @SerializedName("hops")
    var hopCount: Int = 0, // Number of hops traversed

    @SerializedName("type")
    val type: String = "text"
)
