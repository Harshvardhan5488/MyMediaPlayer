package com.harsh.mymediaplayer.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.mymediaplayer.ui.controller.AudioPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val audioPlaybackController: AudioPlaybackController
): ViewModel() {

    val audioPlaybackLoadingFlow: Flow<Boolean> get() = audioPlaybackController.audioPlaybackLoadingFlow

    private val playCentralAudioMutableStateFlow: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val playCentralAudioFlow: Flow<Boolean> = playCentralAudioMutableStateFlow.filterNotNull()

    fun playCentralAudio(link: String) {
        audioPlaybackController.playAudio(link)
        viewModelScope.launch {
            playCentralAudioMutableStateFlow.emit(true)
        }
    }

    fun stopCentralAudio() {
        audioPlaybackController.stopAudio()
        viewModelScope.launch {
            playCentralAudioMutableStateFlow.emit(false)
        }
    }

    fun togglePausePlay() = audioPlaybackController.togglePausePlay()

    fun isCentralAudioPlaying() = audioPlaybackController.isPlaying()

    fun clearCentralAudioFlow() {
        viewModelScope.launch {
            playCentralAudioMutableStateFlow.emit(null)
        }
    }

    private val photoUriMutableStatFlow: MutableStateFlow<Uri?> = MutableStateFlow(null)
    val photoUriFlow: Flow<Uri> = photoUriMutableStatFlow.filterNotNull()

    fun sendPhotoUri(uri: Uri) {
        viewModelScope.launch {
            photoUriMutableStatFlow.emit(uri)
        }
    }

    init {
        audioPlaybackController.initializePlayer()
    }

    override fun onCleared() {
        audioPlaybackController.releasePlayer()
        super.onCleared()
    }
}