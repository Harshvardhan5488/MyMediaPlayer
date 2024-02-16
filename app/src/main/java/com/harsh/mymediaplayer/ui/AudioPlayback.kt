package com.harsh.mymediaplayer.ui

interface AudioPlayback {
    fun playAudio(link: String)
    fun togglePausePlay()
    fun stopAudio()
    fun isPlaying(): Boolean
    fun releasePlayer()
}