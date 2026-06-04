package com.musicplayer.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor() : ViewModel() {
    private val _isGlassMode = MutableStateFlow(false)
    val isGlassMode: StateFlow<Boolean> = _isGlassMode.asStateFlow()

    private val _useBlurredBackground = MutableStateFlow(true)
    val useBlurredBackground: StateFlow<Boolean> = _useBlurredBackground.asStateFlow()

    private val _fontWeightScale = MutableStateFlow(1f)
    val fontWeightScale: StateFlow<Float> = _fontWeightScale.asStateFlow()

    fun toggleGlassMode() {
        _isGlassMode.value = !_isGlassMode.value
    }

    fun toggleBlurredBackground() {
        _useBlurredBackground.value = !_useBlurredBackground.value
    }

    fun setFontWeightScale(scale: Float) {
        _fontWeightScale.value = scale
    }
}
