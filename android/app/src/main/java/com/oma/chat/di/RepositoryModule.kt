package com.oma.chat.di

import com.oma.chat.data.repository.AuthRepositoryImpl
import com.oma.chat.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: com.oma.chat.data.repository.ChatRepositoryImpl
    ): com.oma.chat.domain.repository.ChatRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        groupRepositoryImpl: com.oma.chat.data.repository.GroupRepositoryImpl
    ): com.oma.chat.domain.repository.GroupRepository

    @Binds
    @Singleton
    abstract fun bindCallRepository(
        callRepositoryImpl: com.oma.chat.data.repository.CallRepositoryImpl
    ): com.oma.chat.domain.repository.CallRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: com.oma.chat.data.repository.UserRepositoryImpl
    ): com.oma.chat.domain.repository.UserRepository
}
