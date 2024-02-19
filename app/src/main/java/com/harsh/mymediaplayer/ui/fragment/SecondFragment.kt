package com.harsh.mymediaplayer.ui.fragment

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            Log.d("PhotoPicker", "Selected URI: $uri")
            openFileWithUri(uri)
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

        cameraExecutor = Executors.newSingleThreadExecutor()

        return binding.root
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.surfaceView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(FLASH_MODE_AUTO)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this@SecondFragment, cameraSelector, imageCapture, preview
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun openPicker() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object: ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Log.d(TAG, "MyCaptureEvent: ${output.savedUri}")
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    output.savedUri?.let { openFileWithUri(it) }
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun openFileWithUri(uri: Uri) {
        try {
            if (findNavController().currentDestination?.id != R.id.fullScreenImageFragment) {
                findNavController().navigate(R.id.fullScreenImageFragment, Bundle().apply {
                    putString(FullScreenImageFragment.IMAGE_URI, uri.toString())
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "onImageSaved: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SecondFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    }
}