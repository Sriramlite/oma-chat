package com.oma.chat.data.remote.api

import com.oma.chat.data.remote.dto.AuthResponseDto
import com.oma.chat.data.remote.dto.ChangePasswordRequest
import com.oma.chat.data.remote.dto.CompleteProfileRequest
import com.oma.chat.data.remote.dto.DeleteAccountRequest
import com.oma.chat.data.remote.dto.ForgotPasswordRequest
import com.oma.chat.data.remote.dto.GoogleAuthRequest
import com.oma.chat.data.remote.dto.LoginRequest
import com.oma.chat.data.remote.dto.PhoneAuthRequest
import com.oma.chat.data.remote.dto.ResetPasswordRequest
import com.oma.chat.data.remote.dto.SignupRequest
import com.oma.chat.data.remote.dto.SimpleMessageResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponseDto>

    @POST("auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): Response<AuthResponseDto>

    @POST("auth/google")
    suspend fun googleAuth(
        @Body request: GoogleAuthRequest
    ): Response<AuthResponseDto>

    @POST("auth/phone")
    suspend fun phoneAuth(
        @Body request: PhoneAuthRequest
    ): Response<AuthResponseDto>

    @POST("auth/complete-profile")
    suspend fun completeProfile(
        @Body request: CompleteProfileRequest
    ): Response<AuthResponseDto>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<SimpleMessageResponseDto>

    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<SimpleMessageResponseDto>

    @POST("auth/change-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<SimpleMessageResponseDto>

    @POST("auth/delete-account")
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest
    ): Response<SimpleMessageResponseDto>
}
