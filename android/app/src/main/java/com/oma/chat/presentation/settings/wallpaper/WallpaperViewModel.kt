package com.oma.chat.presentation.settings.wallpaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.UpdateProfileRequest
import com.oma.chat.data.remote.api.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userApi: UserApi
) : ViewModel() {

    private val _selectedWallpaper = MutableStateFlow(authPreferences.getWallpaper())
    val selectedWallpaper: StateFlow<String> = _selectedWallpaper.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun selectWallpaper(wallpaperId: String) {
        _selectedWallpaper.value = wallpaperId
        authPreferences.setWallpaper(wallpaperId)

        // Sync with backend so it's remembered across devices and logins
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userApi.updateProfile(
                    UpdateProfileRequest(
                        wallpaper = wallpaperId,
                        settings = mapOf("wallpaper" to wallpaperId)
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSaving.value = false
            }
        }
    }
}
