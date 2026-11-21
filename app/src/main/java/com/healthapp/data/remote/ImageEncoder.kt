package com.healthapp.data.remote

import com.healthapp.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Prepares a photo for upload: downsamples to at most [maxSide] px on the long edge, applies the
 * EXIF rotation (camera photos are often stored sideways), and re-encodes as JPEG. This keeps
 * uploads ~150–300 KB and strips the original EXIF metadata (GPS, device) from what is sent.
 */
object ImageEncoder {
    data class Encoded(val base64: String, val mimeType: String, val width: Int, val height: Int, val bytes: Int)

    fun encode(context: Context, uri: Uri, maxSide: Int = 1024, quality: Int = 82): Encoded {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds, decodeStream() always returns null and only fills `bounds`.
        val stream = resolver.openInputStream(uri) ?: throw IllegalArgumentException(com.healthapp.core.AppLocale.localized(context).getString(R.string.err_image_open))
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { com.healthapp.core.AppLocale.localized(context).getString(R.string.err_image_invalid) }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException(com.healthapp.core.AppLocale.localized(context).getString(R.string.err_image_read))

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
        }.getOrDefault(0)

        val longSide = maxOf(decoded.width, decoded.height)
        val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
        val matrix = Matrix().apply { if (scale != 1f) postScale(scale, scale); if (rotation != 0) postRotate(rotation.toFloat()) }
        val bitmap = if (scale != 1f || rotation != 0) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        if (bitmap !== decoded) decoded.recycle()
        return Encoded(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg", bitmap.width, bitmap.height, bytes.size)
    }

    private fun ExifInterface.rotationDegrees(): Int = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
