package com.harsh.mymediaplayer.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFirstBinding
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FirstFragment: Fragment() {

    private lateinit var binding: FragmentFirstBinding
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFirstBinding.inflate(inflater, container, false)
        binding.startButton.setOnClickListener {
            mainActivityViewModel.playCentralAudio(LINK)
        }
        binding.playPauseIv.setOnClickListener {
            mainActivityViewModel.togglePausePlay()
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

    companion object {
        private const val TAG = "FirstFragment"
        private const val LINK = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
    }

}