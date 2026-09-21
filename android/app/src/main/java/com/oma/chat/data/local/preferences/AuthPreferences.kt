package com.oma.chat.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.oma.chat.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _isLoggedInState = MutableStateFlow(hasToken())
    val isLoggedInState: StateFlow<Boolean> = _isLoggedInState.asStateFlow()

    fun saveAuthSession(token: String, user: User) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_DATA, gson.toJson(user))
            .putString(KEY_USER_ID, user.id)
            .apply()
        _isLoggedInState.value = true
    }

    fun updateUserData(user: User) {
        prefs.edit()
            .putString(KEY_USER_DATA, gson.toJson(user))
            .putString(KEY_USER_ID, user.id)
            .apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun getUser(): User? {
        val json = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _isLoggedInState.value = false
    }

    companion object {
        private const val PREFS_NAME = "oma_auth_secure_prefs"
        private const val KEY_AUTH_TOKEN = "key_jwt_token"
        private const val KEY_USER_DATA = "key_user_json"
        private const val KEY_USER_ID = "key_user_uuid"
    }
}
