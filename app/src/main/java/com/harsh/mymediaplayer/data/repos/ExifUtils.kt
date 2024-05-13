package com.harsh.mymediaplayer.data.repos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface

private const val TAG: String = "ExifUtils"

/** Transforms rotation and mirroring information into one of the [ExifInterface] constants */
fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = when {
    rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
    rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
    rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
    rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
    rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
    rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
    rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
    rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
    rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
    else -> ExifInterface.ORIENTATION_UNDEFINED
}

/**
 * Helper function used to convert an EXIF orientation enum into a transformation matrix
 * that can be applied to a bitmap.
 *
 * @return matrix - Transformation required to properly display [Bitmap]
 */
fun decodeExifOrientation(exifOrientation: Int): Matrix {
    val matrix = Matrix()

    // Apply transformation corresponding to declared EXIF orientation
    when (exifOrientation) {
        ExifInterface.ORIENTATION_NORMAL -> Unit
        ExifInterface.ORIENTATION_UNDEFINED -> Unit
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(270F)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(90F)
        }

        // Error out if the EXIF orientation is invalid
        else -> Log.e(TAG, "Invalid orientation: $exifOrientation")
    }

    // Return the resulting matrix
    return matrix
}

fun getExifOrientation(context: Context, uri: Uri): Int {
    val inputStream = context.contentResolver.openInputStream(uri)
    if (inputStream != null) {
        val exif = ExifInterface(inputStream)
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    }
    return ExifInterface.ORIENTATION_UNDEFINED
}


object Exif {
    private const val TAG = "CameraExif"

    // Returns the degrees in clockwise. Values are 0, 90, 180, or 270.
    fun getOrientation(jpeg: ByteArray?): Int {
        if (jpeg == null) {
            return 0
        }
        var offset = 0
        var length = 0

        // ISO/IEC 10918-1:1993(E)
        while (offset + 3 < jpeg.size && jpeg[offset++].toInt() and 0xFF == 0xFF) {
            val marker = jpeg[offset].toInt() and 0xFF

            // Check if the marker is a padding.
            if (marker == 0xFF) {
                continue
            }
            offset++

            // Check if the marker is SOI or TEM.
            if (marker == 0xD8 || marker == 0x01) {
                continue
            }
            // Check if the marker is EOI or SOS.
            if (marker == 0xD9 || marker == 0xDA) {
                break
            }

            // Get the length and check if it is reasonable.
            length = pack(jpeg, offset, 2, false)
            if (length < 2 || offset + length > jpeg.size) {
                Log.e(TAG, "Invalid length")
                return 0
            }

            // Break if the marker is EXIF in APP1.
            if (marker == 0xE1 && length >= 8 && pack(jpeg, offset + 2, 4, false) == 0x45786966 && pack(jpeg, offset + 6, 2, false) == 0) {
                offset += 8
                length -= 8
                break
            }

            // Skip other markers.
            offset += length
            length = 0
        }

        // JEITA CP-3451 Exif Version 2.2
        if (length > 8) {
            // Identify the byte order.
            var tag = pack(jpeg, offset, 4, false)
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                Log.e(TAG, "Invalid byte order")
                return 0
            }
            val littleEndian = tag == 0x49492A00

            // Get the offset and check if it is reasonable.
            var count = pack(jpeg, offset + 4, 4, littleEndian) + 2
            if (count < 10 || count > length) {
                Log.e(TAG, "Invalid offset")
                return 0
            }
            offset += count
            length -= count

            // Get the count and go through all the elements.
            count = pack(jpeg, offset - 2, 2, littleEndian)
            while (count-- > 0 && length >= 12) {
                // Get the tag and check if it is orientation.
                tag = pack(jpeg, offset, 2, littleEndian)
                if (tag == 0x0112) {
                    // We do not really care about type and count, do we?
                    val orientation = pack(jpeg, offset + 8, 2, littleEndian)
                    when (orientation) {
                        1 -> return 0
                        3 -> return 180
                        6 -> return 90
                        8 -> return 270
                    }
                    Log.i(TAG, "Unsupported orientation")
                    return 0
                }
                offset += 12
                length -= 12
            }
        }
        Log.i(TAG, "Orientation not found")
        return 0
    }

    private fun pack(bytes: ByteArray, offset: Int, length: Int,
        littleEndian: Boolean): Int {
        var offset = offset
        var length = length
        var step = 1
        if (littleEndian) {
            offset += length - 1
            step = -1
        }
        var value = 0
        while (length-- > 0) {
            value = value shl 8 or (bytes[offset].toInt() and 0xFF)
            offset += step
        }
        return value
    }
}

