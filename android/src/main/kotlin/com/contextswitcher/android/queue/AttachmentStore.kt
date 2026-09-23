package com.contextswitcher.android.queue

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/// Images attached to a message on the phone, stored under the attachments
/// directory `MessageSender` uploads from (its `[image: …]` markers must point
/// in there). Each is re-encoded as a JPEG of at most [MAX_SIDE] px on its long
/// side, upright per its EXIF orientation: a camera photo is several MB, which
/// is slow over mobile data and more than Claude needs to read a screen.
// [impl->dsn~android-message-images~1]
class AttachmentStore(private val dir: File) {

    /// An empty file for the camera app to write a photo into; [store] it afterwards.
    fun newCameraFile(): File {
        dir.mkdirs()
        return File(dir, "${stamp()}-camera-raw.jpg").apply { createNewFile() }
    }

    /// Decodes the image `open` yields (called twice: bounds, then pixels),
    /// scales and rotates it, and writes it as `<timestamp>-<name>.jpg`.
    fun store(name: String, open: () -> InputStream): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("not an image")
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IOException("cannot decode the image")
        val rotation = runCatching { open().use { orientationDegrees(ExifInterface(it)) } }.getOrDefault(0)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val image = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        dir.mkdirs()
        // Sanitized: the remote path lands verbatim in the message text.
        val safeName = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "image" }
        val file = File(dir, "${stamp()}-$safeName.jpg")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file
    }

    private fun orientationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun stamp() = LocalDateTime.now().format(STAMP)

    companion object {
        const val MAX_SIDE = 2048
        private const val JPEG_QUALITY = 85
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}
