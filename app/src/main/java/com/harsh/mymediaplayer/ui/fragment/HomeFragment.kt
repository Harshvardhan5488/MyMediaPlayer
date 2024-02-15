package com.harsh.mymediaplayer.ui.fragment

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.harsh.mymediaplayer.PlaybackService
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class HomeFragment: Fragment() {

    private lateinit var binding: FragmentHomeBinding

    private lateinit var sessionToken: SessionToken

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private val browser: MediaBrowser?
        get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.startButton.setOnClickListener {
            startAudio(LINK)
            /*
            if (browser?.isPlaying == false) {
            }
            */
        }

        binding.stopButton.setOnClickListener {
            if (browser?.isPlaying == true) {
                releaseBrowser()
                browser?.release()
            }
        }

        binding.playPauseBtn1.setOnClickListener {
            if (browser?.isPlaying == false) {
                binding.playPauseBtn1.setImageResource(R.drawable.ic_pause_24)
                binding.mediaLoadingBar1.isVisible = true
                startAudio(LINK)
                binding.mediaLoadingBar1.isVisible = false
            } else if (browser?.isPlaying == true) {
                binding.playPauseBtn1.setImageResource(R.drawable.ic_play_arrow_24)
                browser?.pause()
            }
        }

        binding.playPauseBtn2.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (browser?.isPlaying == false) {
                    binding.playPauseBtn2.setImageResource(R.drawable.ic_pause_24)
                    binding.mediaLoadingBar2.isVisible = true
                    startAudio(LINK2)
                    binding.mediaLoadingBar2.isVisible = false
                } else if (browser?.isPlaying == true) {
                    binding.playPauseBtn2.setImageResource(R.drawable.ic_play_arrow_24)
                    browser?.pause()
                }
            }
        }

        binding.nextFragmentBt.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_homeFragment_to_firstFragment)
            } catch (e: Exception) {
                Log.e(TAG, "onCreateView: ${e.message}")
            }
        }

        return binding.root
    }

    /*
    private fun attachSeekBar(seekBar: AppCompatSeekBar) {
        viewLifecycleOwner.lifecycleScope.launch {
            val duration = browser?.duration ?: 0
            var current = 0L
            while (current != duration) {
                current = browser?.currentPosition ?: 0L
                seekBar.progress = (current * 100L / duration).toInt()
            }
        }
    }
    */

    private fun startAudio(link: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            run {
                val browser = this@HomeFragment.browser ?: return@launch
                browser.setMediaItem(
                    MediaItem.fromUri(link)
                )
                browser.prepare()
                browser.play()
            }
        }
    }

    private fun initializeBrowser() {
        browserFuture = MediaBrowser.Builder(requireContext(), sessionToken).buildAsync()
        browserFuture.addListener({ addController() }, MoreExecutors.directExecutor())
    }

    private fun addController() {
        val browser = this.browser ?: return

        browser.addListener(
            object: Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        Log.d(TAG, "onEvents: $player--$events")
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            // buffering
                        }

                        Player.STATE_READY -> {
                            //attachSeekBar(R.id.seek_bar)
                        }
                    }
                    super.onPlaybackStateChanged(playbackState)
                }
            }
        )

    }

    private fun releaseBrowser() {
        MediaBrowser.releaseFuture(browserFuture)
    }

    override fun onStart() {
        super.onStart()
        sessionToken = SessionToken(requireContext(), ComponentName(requireActivity(), PlaybackService::class.java))
        initializeBrowser()
    }

    override fun onStop() {
        super.onStop()
        releaseBrowser()
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val LINK2 = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
        private const val LINK = "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/06_-_No_Pain_No_Gain.mp3"
    }

}