package com.harsh.mymediaplayer.ui.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFirstBinding
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FirstFragment: Fragment() {

    private lateinit var binding: FragmentFirstBinding
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            val isGranted = permissions.entries.all { it.value }

            if (!isGranted) {

                var shouldShowRequestPermissionRationale: Boolean = false
                REQUIRED_PERMISSIONS.forEach { p ->
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

        binding.startButton.setOnClickListener {
            mainActivityViewModel.playCentralAudio(LINK)
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

        return binding.root
    }

    private fun showStoragePermissionRationale() {
        MaterialAlertDialogBuilder(requireContext(), androidx.transition.R.style.AlertDialog_AppCompat)
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

    companion object {
        private const val TAG = "FirstFragment"
        private const val LINK = "https://firebasestorage.googleapis.com/v0/b/weheal-debug.appspot.com/o/Test%2Fsmaple_audio_MP3_700KB.mp3?alt=media&token=f7de2722-c11f-4395-92f3-e986a6bf4d91"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                //Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()

    }

}