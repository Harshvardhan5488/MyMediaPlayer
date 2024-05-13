package com.harsh.mymediaplayer.ui.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFirstBinding
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import com.harsh.taptargetview.TapTarget
import com.harsh.taptargetview.TapTargetSequence
import com.harsh.taptargetview.TapTargetView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FirstFragment: Fragment() {

    private lateinit var binding: FragmentFirstBinding
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private var newUri: Uri? = null

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            val isGranted = permissions.entries.all { it.value }

            if (!isGranted) {

                var shouldShowRequestPermissionRationale: Boolean = false
                permissions.keys.forEach { p ->
                    if (shouldShowRequestPermissionRationale(p)) {
                        shouldShowRequestPermissionRationale = true
                    }
                }
                // PERMISSION NOT GRANTED
                if (shouldShowRequestPermissionRationale) {
                    showStoragePermissionRationale()
                } else {
                    Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
                    showSettingDialog()
                }
            } else {
                navigateToCameraFragment()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TapTargetView.showFor(
            requireActivity(),

            TapTarget.forView(binding.startButton, "This is a imageView", "Click here to Start Tutorial")
                    .outerCircleColor(R.color.purple_700)      // Specify a color for the outer circle
                    .outerCircleAlpha(0.7f)            // Specify the alpha amount for the outer circle
                    .targetCircleColor(R.color.purple_500)   // Specify a color for the target circle
                    .titleTextSize(20)                  // Specify the size (in sp) of the title text
                    .titleTextColor(R.color.white)      // Specify the color of the title text
                    .descriptionTextSize(10)            // Specify the size (in sp) of the description text
                    .textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
                    .dimColor(R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
                    .drawShadow(true)                   // Whether to draw a drop shadow or not
                    .cancelable(false)                  // Whether tapping outside the outer circle dismisses the view
                    .tintTarget(false)                   // Whether to tint the target view's color
                    .transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
                    //.icon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_photo_24))                     // Specify a custom drawable to draw as the target
                    .targetRadius(60),                  // Specify the target radius (in dp)

            object: TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView?) {
                    super.onTargetClick(view)
                    Toast.makeText(requireContext(), "Clicked", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.startButton.setOnClickListener {
            //mainActivityViewModel.playCentralAudio(LINK)
            startTutorial()
        }

        binding.playPauseIv.setOnClickListener {
            mainActivityViewModel.togglePausePlay()
        }

        binding.nextFragmentBt.setOnClickListener {
            try {
                if (checkCameraHardware(requireContext())) {
                    Log.d(TAG, "onCreateView: ${allPermissionsGranted()}")
                    if (allPermissionsGranted()) {
                        navigateToCameraFragment()
                    } else {
                        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
                    }
                } else {
                    Toast.makeText(requireContext(), "No camera found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "onCreateView: ${e.message}")
            }
        }

        binding.imageIv.setOnClickListener {
            newUri?.let { it1 ->
                navigateToFullScreenImage(it1)
            }
        }

        attachPhotoObserver()

    }

    private fun startTutorial() {
        TapTargetSequence(requireActivity())
                .targets(
                    TapTarget.forView(binding.startButton, "Start button", "Click here to go")
                            .targetRadius(binding.startButton.width/4),
                    TapTarget.forView(binding.playPauseIv, "Play / Pause Button", "Click here to go")
                            .targetRadius(binding.playPauseIv.width/4),
                    TapTarget.forView(binding.nextFragmentBt, "Next Fragment Button", "Click here to End")
                            .targetRadius(binding.nextFragmentBt.width/4)
                )

                .listener(object: TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        Toast.makeText(requireContext(), "Tutorial Finished", Toast.LENGTH_SHORT).show()
                    }

                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {

                    }

                    override fun onSequenceCanceled(lastTarget: TapTarget?) {

                    }

                })
                .start()
    }

    private fun attachPhotoObserver() {
        lifecycleScope.launch(Dispatchers.Main) {
            mainActivityViewModel.photoUriFlow.collect { fileUri ->
                newUri = fileUri
                Glide.with(binding.imageIv.context).asBitmap()
                        .load(fileUri)
                        .placeholder(R.drawable.ic_photo_24)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .dontAnimate()
                        .into(binding.imageIv)
            }
        }
    }

    private fun showStoragePermissionRationale() {
        MaterialAlertDialogBuilder(requireContext(), androidx.appcompat.R.style.AlertDialog_AppCompat)
                .setTitle("Alert")
                .setMessage("Storage permission, without this some of the feature will not work properly. Please allow read storage permission")
                .setPositiveButton("Ok") { _, _ ->
                    activityResultLauncher.launch(REQUIRED_PERMISSIONS)
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun showSettingDialog() {
        MaterialAlertDialogBuilder(requireContext(), androidx.appcompat.R.style.AlertDialog_AppCompat)
                .setTitle("Storage Permission")
                .setMessage("Storage permission is required, without this some of the feature will not work properly. Please allow storage permission from setting")
                .setPositiveButton("Ok") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${requireContext().packageName}")
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun navigateToCameraFragment() {
        try {
            if (findNavController().currentDestination?.id != R.id.secondFragment) {
                findNavController().navigate(R.id.action_firstFragment_to_secondFragment)
            }
        } catch (e: Exception) {
            Log.e(TAG, "navigateToCameraFragment: ${e.message}")
        }
    }

    private fun navigateToFullScreenImage(uri: Uri) {
        try {
            if (findNavController().currentDestination?.id != R.id.fullScreenImageFragment) {
                findNavController().navigate(R.id.fullScreenImageFragment, Bundle().apply {
                    putString(FullScreenImageFragment.IMAGE_URI, uri.toString())
                    putBoolean(FullScreenImageFragment.IS_VIEW_ONLY, true)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "onImageSaved: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FirstFragment"
        private const val LINK = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                // Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }.toTypedArray()

    }

}