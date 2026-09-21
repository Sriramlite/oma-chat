package com.oma.chat.data.remote.api

import com.oma.chat.data.remote.dto.CreateGroupRequest
import com.oma.chat.data.remote.dto.GroupDto
import com.oma.chat.data.remote.dto.ManageGroupRequest
import com.oma.chat.data.remote.dto.SimpleMessageResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GroupApi {

    @POST("groups/create")
    suspend fun createGroup(
        @Body request: CreateGroupRequest
    ): Response<GroupDto>

    @GET("groups/list")
    suspend fun getGroups(): Response<List<GroupDto>>

    @POST("chat/manage_group")
    suspend fun manageGroup(
        @Body request: ManageGroupRequest
    ): Response<SimpleMessageResponseDto>
}
