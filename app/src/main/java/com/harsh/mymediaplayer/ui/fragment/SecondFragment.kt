package com.harsh.mymediaplayer.ui.fragment

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentSecondBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class SecondFragment: Fragment() {

    private lateinit var binding: FragmentSecondBinding

    private var imageCapture: ImageCapture? = null

    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d("PhotoPicker", "Selected URI: $uri")
            openFileWithUri(uri, true)
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentSecondBinding.inflate(inflater, container, false)
        startCamera()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.openGalleryBt.setOnClickListener { openPicker() }
        binding.clickBt.setOnClickListener { takePhoto() }
        binding.flipBt.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.surfaceView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(FLASH_MODE_AUTO)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(720, 1280))
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@SecondFragment, cameraSelector, imageCapture, preview)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(requireContext(), "Failed to open", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun openPicker() {
        try {
            if (hasPermissions(requireContext(), *MEDIA_PERMISSIONS)) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                requestPermissionLauncher.launch(MEDIA_PERMISSIONS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "openPicker: Error Photo picker", e)
            Toast.makeText(requireContext(), "Error opening media", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val isGranted = permissions.entries.all { it.value }

        if (!isGranted) { // PERMISSION NOT GRANTED
            var shouldShowRequestPermissionRationale = false
            permissions.keys.forEach { p ->
                if (shouldShowRequestPermissionRationale(p)) {
                    shouldShowRequestPermissionRationale = true
                }
            } // PERMISSION NOT GRANTED
            if (shouldShowRequestPermissionRationale) {
                showStoragePermissionRationale()
            } else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
                showStoragePermissionSettingsDialog()
            }
        }
    }

    private fun showStoragePermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext(), androidx.appcompat.R.style.AlertDialog_AppCompat)
            .setTitle("Calling Permission Denied")
            .setMessage("Storage Access permission is required, without this Photo selection feature will not work properly. " + "Please allow permission from the app settings -> permissions")
            .setPositiveButton("Ok") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${requireActivity().packageName}")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Change it manually from the app settings", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showStoragePermissionRationale() {
        MaterialAlertDialogBuilder(requireContext(), androidx.appcompat.R.style.AlertDialog_AppCompat)
            .setTitle("Calling Permission Alert")
            .setMessage("Storage Access permission is required, without this Photo selection feature will not work properly. Please allow permissions")
            .setPositiveButton("Ok") { _, _ ->
                requestPermissionLauncher.launch(MEDIA_PERMISSIONS)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object: ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { savedUri ->
                        openFileWithUri(savedUri, false)
                    }
                }
            }
        )
    }

    private fun openFileWithUri(uri: Uri, isFromGallery: Boolean) {
        try {
            if (findNavController().currentDestination?.id != R.id.fullScreenImageFragment) {
                findNavController().navigate(R.id.fullScreenImageFragment, Bundle().apply {
                    putString(FullScreenImageFragment.IMAGE_URI, uri.toString())
                    putBoolean(FullScreenImageFragment.IS_FROM_GALLERY, isFromGallery)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "onImageSaved: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SecondFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private val MEDIA_PERMISSIONS =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf()
            }

    }
}