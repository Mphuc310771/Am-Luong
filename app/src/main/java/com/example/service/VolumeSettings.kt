package com.example.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VolumeSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "volume_settings"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_HANDLE_ENABLED = "handle_enabled"
        private const val KEY_HANDLE_SIDE = "handle_side" // "LEFT" or "RIGHT"
        private const val KEY_HANDLE_OPACITY = "handle_opacity"
        private const val KEY_HANDLE_HEIGHT = "handle_height"
        private const val KEY_HANDLE_Y_OFFSET = "handle_y_offset"
        private const val KEY_HANDLE_DRAG_ACTION = "handle_drag_action" // "VOLUME" or "MOVE"
        private const val KEY_COMPACT_MODE = "compact_mode_v2"
        private const val KEY_SHOW_SYSTEM_UI = "show_system_ui"
    }

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    var isHandleEnabled: Boolean
        get() = prefs.getBoolean(KEY_HANDLE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HANDLE_ENABLED, value).apply()
            _isHandleEnabledFlow.value = value
        }

    var handleSide: String
        get() = prefs.getString(KEY_HANDLE_SIDE, "RIGHT") ?: "RIGHT"
        set(value) {
            prefs.edit().putString(KEY_HANDLE_SIDE, value).apply()
            _handleSideFlow.value = value
        }

    var handleOpacity: Float
        get() = prefs.getFloat(KEY_HANDLE_OPACITY, 0.6f)
        set(value) {
            prefs.edit().putFloat(KEY_HANDLE_OPACITY, value).apply()
            _handleOpacityFlow.value = value
        }

    var handleHeight: Int
        get() = prefs.getInt(KEY_HANDLE_HEIGHT, 70) // dp
        set(value) {
            prefs.edit().putInt(KEY_HANDLE_HEIGHT, value).apply()
            _handleHeightFlow.value = value
        }

    var handleYOffset: Int
        get() = prefs.getInt(KEY_HANDLE_Y_OFFSET, 0)
        set(value) {
            prefs.edit().putInt(KEY_HANDLE_Y_OFFSET, value).apply()
            _handleYOffsetFlow.value = value
        }

    var handleDragAction: String
        get() = prefs.getString(KEY_HANDLE_DRAG_ACTION, "VOLUME") ?: "VOLUME"
        set(value) {
            prefs.edit().putString(KEY_HANDLE_DRAG_ACTION, value).apply()
            _handleDragActionFlow.value = value
        }

    var showSystemUi: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_UI, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM_UI, value).apply()

    var isCompactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_MODE, true) // Default to true for better out-of-the-box ergonomics as requested
        set(value) {
            prefs.edit().putBoolean(KEY_COMPACT_MODE, value).apply()
            _isCompactModeFlow.value = value
        }

    // Reactive states for the service
    private val _isHandleEnabledFlow = MutableStateFlow(isHandleEnabled)
    val isHandleEnabledFlow: StateFlow<Boolean> = _isHandleEnabledFlow

    private val _handleSideFlow = MutableStateFlow(handleSide)
    val handleSideFlow: StateFlow<String> = _handleSideFlow

    private val _handleOpacityFlow = MutableStateFlow(handleOpacity)
    val handleOpacityFlow: StateFlow<Float> = _handleOpacityFlow

    private val _handleHeightFlow = MutableStateFlow(handleHeight)
    val handleHeightFlow: StateFlow<Int> = _handleHeightFlow

    private val _handleYOffsetFlow = MutableStateFlow(handleYOffset)
    val handleYOffsetFlow: StateFlow<Int> = _handleYOffsetFlow

    private val _handleDragActionFlow = MutableStateFlow(handleDragAction)
    val handleDragActionFlow: StateFlow<String> = _handleDragActionFlow

    private val _isCompactModeFlow = MutableStateFlow(isCompactMode)
    val isCompactModeFlow: StateFlow<Boolean> = _isCompactModeFlow
}
