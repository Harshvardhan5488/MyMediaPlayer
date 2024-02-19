package com.harsh.mymediaplayer.ui.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.harsh.mymediaplayer.PlaybackService
import com.harsh.mymediaplayer.ui.AudioPlayback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject


class AudioPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalCoroutineScope: CoroutineScope
): AudioPlayback {

    private val audioPlaybackLoadingMutableStateFlow: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val audioPlaybackLoadingFlow: Flow<Boolean> = audioPlaybackLoadingMutableStateFlow.filterNotNull()

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser?
        get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null


    fun initializePlayer() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture.addListener({ addController() }, MoreExecutors.directExecutor())
    }

    private fun addController() {
        browser?.addListener(object: Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        externalCoroutineScope.launch {
                            audioPlaybackLoadingMutableStateFlow.emit(true)
                        }
                    }

                    Player.STATE_READY -> {
                        externalCoroutineScope.launch {
                            audioPlaybackLoadingMutableStateFlow.emit(false)
                        }
                    }

                    Player.STATE_ENDED -> {

                    }

                    Player.STATE_IDLE -> {

                    }
                }
                super.onPlaybackStateChanged(playbackState)
            }
        })
    }

    override fun playAudio(link: String) {
        browser?.let { browser ->
            if (browser.isPlaying) {
                browser.stop()
            }
            browser.setMediaItem(
                MediaItem.fromUri(link)
            )
            browser.prepare()
            browser.play()
        }
    }

    override fun togglePausePlay() {
        browser?.let { browser ->
            if (browser.isPlaying) {
                browser.pause()
            } else {
                browser.play()
            }
        }
    }

    override fun stopAudio() {
        browser?.stop()
    }

    override fun isPlaying() = browser?.isPlaying ?: false

    override fun releasePlayer() {
        MediaBrowser.releaseFuture(browserFuture)
    }

}
