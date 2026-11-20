package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GameScreen {
    Menu,
    Play,
    Pause
}

data class GameUiState(
    val isEngineReady: Boolean = false,
    val loadError: String? = null,
    val currentScreen: GameScreen = GameScreen.Menu,
    val isImmersive: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val landscapeLocked: Boolean = true,
    val showDeviceSheet: Boolean = false,
    val showExitDialog: Boolean = false,
    val statusMessage: String? = null,
    val reloadNonce: Int = 0
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("block_space_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        GameUiState(
            isImmersive = prefs.getBoolean(KEY_IMMERSIVE, true),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            landscapeLocked = prefs.getBoolean(KEY_LANDSCAPE, true)
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun onEngineReady() {
        _uiState.update { it.copy(isEngineReady = true, loadError = null) }
    }

    fun onEngineError(message: String) {
        _uiState.update { it.copy(isEngineReady = false, loadError = message) }
    }

    fun retryLoad() {
        _uiState.update {
            it.copy(
                isEngineReady = false,
                loadError = null,
                reloadNonce = it.reloadNonce + 1
            )
        }
    }

    fun onScreenChanged(screenName: String) {
        val screen = when (screenName) {
            "play" -> GameScreen.Play
            "pause" -> GameScreen.Pause
            else -> GameScreen.Menu
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setImmersive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMMERSIVE, enabled).apply()
        _uiState.update { it.copy(isImmersive = enabled) }
    }

    fun toggleImmersive() {
        setImmersive(!_uiState.value.isImmersive)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _uiState.update { it.copy(hapticsEnabled = enabled) }
    }

    fun setLandscapeLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_LANDSCAPE, locked).apply()
        _uiState.update { it.copy(landscapeLocked = locked) }
    }

    fun setShowDeviceSheet(show: Boolean) {
        _uiState.update { it.copy(showDeviceSheet = show) }
    }

    fun setShowExitDialog(show: Boolean) {
        _uiState.update { it.copy(showExitDialog = show) }
    }

    fun showStatusMessage(message: String?) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    private companion object {
        const val KEY_IMMERSIVE = "immersive"
        const val KEY_HAPTICS = "haptics"
        const val KEY_LANDSCAPE = "landscape_locked"
    }
}
