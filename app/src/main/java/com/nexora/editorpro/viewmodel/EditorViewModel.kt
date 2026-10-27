package com.nexora.editorpro.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.nexora.editorpro.NexoraApplication
import com.nexora.editorpro.model.*
import com.nexora.editorpro.transformer.VideoTransformerEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as NexoraApplication).projectRepository
    private val transformerEngine = VideoTransformerEngine(application)
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
    // ExoPlayer lifecycle, playback looping, trim controls, audio mixer, filter presets, and export
}