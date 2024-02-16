package com.harsh.mymediaplayer.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentHomeBinding
import com.harsh.mymediaplayer.ui.adapter.MediaFileRecyclerAdapter
import com.harsh.mymediaplayer.ui.adapter.OnItemPlayClickListener
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class HomeFragment: Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private val feedList: List<String> = listOf(LINK, LINK2)

    private val onItemPlayClickListener = object: OnItemPlayClickListener {
        override fun playAudio(link: String) {
            mainActivityViewModel.playCentralAudio(link)
        }
    }
    private val mediaFileRecyclerAdapter: MediaFileRecyclerAdapter by lazy {
        MediaFileRecyclerAdapter(feedList, onItemPlayClickListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.nextFragmentBt.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_homeFragment_to_firstFragment)
            } catch (e: Exception) {
                Log.e(TAG, "onCreateView: ${e.message}")
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.feedRV.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.feedRV.adapter = mediaFileRecyclerAdapter
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

    companion object {
        private const val TAG = "HomeFragment"
        private const val LINK2 = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
        private const val LINK = "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/06_-_No_Pain_No_Gain.mp3"
    }

}