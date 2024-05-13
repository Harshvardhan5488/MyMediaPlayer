package com.harsh.mymediaplayer.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
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
import androidx.camera.core.impl.utils.Exif
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.data.repos.computeExifOrientation
import com.harsh.mymediaplayer.data.repos.decodeExifOrientation
import com.harsh.mymediaplayer.databinding.FragmentSecondBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
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
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                //.setTargetResolution(Size(720, 1280))
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

    @SuppressLint("RestrictedApi")
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

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object: ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "onImageSaved: ${output.savedUri}")
                    val savedUri = output.savedUri ?: return

                    // Read EXIF data for rotation information
                    val inputStream = requireContext().contentResolver.openInputStream(savedUri)
                    val exif = Exif.createFromInputStream(inputStream!!)
                    val rotation = exif.rotation

                    // Handle image based on rotation
                    handleImageWithRotation(savedUri, rotation)
                    //openFileWithUri(savedUri, false)
                }
            }
        )
    }

    private fun handleImageWithRotation(uri: Uri, rotation: Int) {
        Log.d(TAG, "handleImageWithRotation: $uri $rotation")
        when (val exifOrientation = computeExifOrientation(rotation, false)) {
            // No rotation needed
            0, ExifInterface.ORIENTATION_NORMAL -> openFileWithUri(uri)
            // Handle other orientations as needed
            else -> {
                Log.w(TAG, "Unsupported EXIF orientation: $rotation")
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val matrix = decodeExifOrientation(exifOrientation)
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    // Recycle the original bitmap
                    bitmap.recycle()
                    openFileWithUri(getUriFromBitmap(uri, rotatedBitmap)) // Pass the rotated bitmap's Uri
                } else {
                    Log.w(TAG, "Failed to open input stream for image: $uri")
                }
            }
        }
    }

    private fun getUriFromBitmap(uri: Uri, bitmap: Bitmap): Uri {
        Log.d(TAG, "getUriFromBitmap: ${bitmap.byteCount}")
        val directory = File(requireContext().filesDir, "Camera_Image")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, getFileName(uri))
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return Uri.fromFile(file)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme.equals("content")) {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.let {
                if (it.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
                cursor.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "temp_file"
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

        private val MEDIA_PERMISSIONS =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                arrayOf()
            }

    }
}