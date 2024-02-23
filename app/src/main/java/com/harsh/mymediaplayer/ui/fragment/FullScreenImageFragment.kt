package com.harsh.mymediaplayer.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFullScreenImageBinding

class FullScreenImageFragment: Fragment() {

    private lateinit var binding: FragmentFullScreenImageBinding
    private val imageUri: String? by lazy { arguments?.getString(IMAGE_URI) }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFullScreenImageBinding.inflate(inflater, container, false)
        Log.d(TAG, "MyCaptureEvent onCreateView: $imageUri")
        imageUri?.let {
            binding.fullscreenIv.setImageURI(Uri.parse(imageUri))
        }
        binding.sendBt.setOnClickListener {
            findNavController().popBackStack(R.id.firstFragment, false)
        }
        return binding.root
    }

    companion object {
        private const val TAG = "FullScreenImageFragment"
        const val IMAGE_URI = "IMAGE_URI"
    }
}