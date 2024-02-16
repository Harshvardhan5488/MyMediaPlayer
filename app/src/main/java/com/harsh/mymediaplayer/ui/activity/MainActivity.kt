package com.harsh.mymediaplayer.ui.activity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.ActivityMainBinding
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainActivityViewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        attachAllAudioListener()

        binding.playPauseIv.setOnClickListener {
            mainActivityViewModel.togglePausePlay()
            togglePlayPauseIcon()
        }

        binding.close.setOnClickListener {
            mainActivityViewModel.stopCentralAudio()
        }
    }

    private fun togglePlayPauseIcon() {
        if (mainActivityViewModel.isCentralAudioPlaying()) {
            binding.playPauseIv.setImageResource(R.drawable.ic_pause_24)
        } else {
            binding.playPauseIv.setImageResource(R.drawable.ic_play_arrow_24)
        }
    }

    private fun attachAllAudioListener() {
        lifecycleScope.launch {
            mainActivityViewModel.playCentralAudioFlow.collectLatest {
                binding.allAudio.isVisible = it
                if (it) {
                    binding.playPauseIv.setImageResource(R.drawable.ic_pause_24)
                } else {
                    binding.playPauseIv.setImageResource(R.drawable.ic_play_arrow_24)
                }
                mainActivityViewModel.clearCentralAudioFlow()
            }
        }

        lifecycleScope.launch {
            mainActivityViewModel.audioPlaybackLoadingFlow.collectLatest {
                binding.mediaLoadingBar.isVisible = it
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}