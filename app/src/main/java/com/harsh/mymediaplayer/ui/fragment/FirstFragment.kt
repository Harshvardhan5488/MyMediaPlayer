package com.harsh.mymediaplayer.ui.fragment

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.harsh.mymediaplayer.PlaybackService
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFirstBinding

class FirstFragment: Fragment() {

    private lateinit var binding: FragmentFirstBinding
    private lateinit var sessionToken: SessionToken
    private var isMediaPlayerServiceRunning = false

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser?
        get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() =
            if (controllerFuture.isDone && !controllerFuture.isCancelled) controllerFuture.get() else null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFirstBinding.inflate(inflater, container, false)
        binding.startButton.setOnClickListener {
            if (!isMediaPlayerServiceRunning) {
                if (controller?.isPlaying == true) {
                    controller?.stop()
                }
                run {
                    val browser = this.browser ?: return@run
                    browser.setMediaItem(
                        MediaItem.fromUri(LINK)
                    )
                    browser.shuffleModeEnabled = false
                    browser.prepare()
                    browser.play()
                    browser.sessionActivity?.send()
                    isMediaPlayerServiceRunning = true
                }
            }
        }
        binding.playPauseBtn.setOnClickListener {
            if (controller?.isPlaying == true) {
                controller?.pause()
            } else if (controller?.isPlaying == false) {
                controller?.play()
            }
        }

        binding.nextFragmentBt.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_firstFragment_to_secondFragment)
            } catch (e: Exception) {
                Log.e(TAG, "onCreateView: ${e.message}")
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        sessionToken = SessionToken(requireContext(), ComponentName(requireActivity(), PlaybackService::class.java))
        initializeBrowser()
        initializeController()
    }

    private fun initializeBrowser() {
        browserFuture = MediaBrowser.Builder(requireContext(), sessionToken).buildAsync()
        browserFuture.addListener({ }, MoreExecutors.directExecutor())
    }

    private fun initializeController() {
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture.addListener({ addController() }, MoreExecutors.directExecutor())
    }

    private fun addController() {
        val controller = this.controller ?: return

        controller.addListener(
            object: Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        Log.d(TAG, "onEvents: $player--$events")
                    }
                    /*
                    if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                        binding.playerView.setShowSubtitleButton(player.currentTracks.isTypeSupported(C.TRACK_TYPE_TEXT))
                    }
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                        //updateCurrentPlaylistUI()
                    }
                    if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                        //updateMediaMetadataUI()
                    }
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        // Trigger adapter update to change highlight of current item.
                        //mediaItemListAdapter.notifyDataSetChanged()
                    }
                    */
                }
            }
        )

    }

    private fun releaseBrowser() {
        MediaBrowser.releaseFuture(browserFuture)
    }

    override fun onStop() {
        super.onStop()
        releaseBrowser()
    }

    companion object {
        private const val TAG = "FirstFragment"
        private const val LINK = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
    }

}