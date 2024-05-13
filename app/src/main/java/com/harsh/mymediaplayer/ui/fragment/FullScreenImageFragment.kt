package com.harsh.mymediaplayer.ui.fragment

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.harsh.mymediaplayer.R
import com.harsh.mymediaplayer.databinding.FragmentFullScreenImageBinding
import com.harsh.mymediaplayer.ui.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@AndroidEntryPoint
class FullScreenImageFragment: Fragment() {

    private lateinit var binding: FragmentFullScreenImageBinding
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private val imageUri: String by lazy { arguments?.getString(IMAGE_URI)!! }
    private val isViewOnly: Boolean by lazy { arguments?.getBoolean(IS_VIEW_ONLY) ?: false }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFullScreenImageBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "MyCaptureEvent onCreateView: $imageUri")

        // Efficient way to show large size Pictures
        Glide.with(binding.fullscreenIv.context).asBitmap()
            .load(Uri.parse(imageUri))
            .placeholder(R.drawable.ic_photo_24)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .dontAnimate()
            .into(binding.fullscreenIv)

        binding.captionLl.isVisible = !isViewOnly

        val fileSize = createFileFromUri(Uri.parse(imageUri))?.length()?.div(1024)
        binding.sizeTv.text = "${fileSize.toString()} Kb"

        binding.sendBt.setOnClickListener {
            showPleaseWaitDialog()
            viewLifecycleOwner.lifecycleScope.launch {
                compressPhotoAndSave(Uri.parse(imageUri))?.let { uri ->
                    mainActivityViewModel.sendPhotoUri(uri)
                }
                dismissPleaseWaitDialog()
                findNavController().popBackStack(R.id.firstFragment, false)
            }
        }
    }

    private fun compressPhotoAndSave(uri: Uri): Uri? {
        val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(uri))
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height)

        /*
        // Optionally, delete the original captured image
        if (!isFromGallery) {
            requireContext().contentResolver.delete(uri, null, null)
        }
        */

        return saveImageToMediaStore(uri, finalBitmap)
    }

    private fun saveImageToMediaStore(uri: Uri, bitmap: Bitmap): Uri? {

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getFileName(uri))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val resolver = requireContext().contentResolver
        val newImageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (newImageUri != null) {
            try {
                val outputStream = resolver.openOutputStream(newImageUri)
                getResizedBitmap(bitmap, 1280).compress(Bitmap.CompressFormat.JPEG, 50, outputStream!!)
                //bitmap
                outputStream?.close()
                return newImageUri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save mirrored image: ${e.message}")
            }
        }
        return null
    }

    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.getWidth()
        var height = image.getHeight()
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private val pleaseWaitDialog: AlertDialog by lazy {
        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(false)
            .create()
    }
    private var showingPleaseWaitDialog: Boolean = false
    private fun showPleaseWaitDialog() {
        if (!showingPleaseWaitDialog && activity?.isFinishing == false) {
            pleaseWaitDialog.show()
            showingPleaseWaitDialog = true
        }
    }

    private fun dismissPleaseWaitDialog() {
        if (showingPleaseWaitDialog) {
            pleaseWaitDialog.dismiss()
            showingPleaseWaitDialog = false
        }
    }

    private fun createFileFromUri(uri: Uri): File? {
        val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
        inputStream?.let {
            val outputFile = File(requireContext().cacheDir, getFileName(uri))
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            return outputFile
        }
        return null
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

    companion object {
        private const val TAG = "FullScreenImageFragment"
        const val IMAGE_URI = "IMAGE_URI"
        const val IS_VIEW_ONLY = "IS_VIEW_ONLY"
    }
}