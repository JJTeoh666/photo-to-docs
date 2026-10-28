package com.jjteo.phototodocs.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

object PhotoFiles {
    fun newCaptureUri(context: Context): Uri {
        val folder = File(context.cacheDir, "captures").apply { mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", File(folder, "capture-${UUID.randomUUID()}.jpg"))
    }

    fun import(context: Context, uri: Uri): PhotoPage {
        val directory = File(context.filesDir, "photos").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val original = File(directory, "$id-original.jpg")
        val working = File(directory, "$id.jpg")
        val temp = File(directory, "$id-input")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= 80L * 1024 * 1024) { "Please choose a photo smaller than 80 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("The photo could not be opened.")
            val decoded = readBitmap(temp, 2800)
            val exif = runCatching { ExifInterface(temp).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (exif) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            write(upright, original)
            original.copyTo(working, overwrite = true)
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
            return PhotoPage(id, working.absolutePath, original.absolutePath)
        } catch (e: Throwable) {
            original.delete(); working.delete()
            throw e
        } finally { temp.delete() }
    }

    fun rotate(page: PhotoPage): PhotoPage {
        val bitmap = readBitmap(File(page.path), 2800)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(90f) }, true)
        val file = File(File(page.path).parentFile, "${page.id}-${UUID.randomUUID()}.jpg")
        write(rotated, file)
        rotated.recycle(); bitmap.recycle()
        return page.copy(path = file.absolutePath)
    }

    fun crop(page: PhotoPage, left: Float, top: Float, right: Float, bottom: Float): PhotoPage {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right - left >= .05f && bottom - top >= .05f) { "Select a larger crop area." }
        val bitmap = readBitmap(File(page.path), 2800)
        val x = (left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val width = ((right - left) * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val height = ((bottom - top) * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
        val crop = Bitmap.createBitmap(bitmap, x, y, width, height)
        val file = File(File(page.path).parentFile, "${page.id}-${UUID.randomUUID()}.jpg")
        write(crop, file)
        if (crop !== bitmap) crop.recycle()
        bitmap.recycle()
        return page.copy(path = file.absolutePath)
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) { "Could not save photo." } }
    }

    fun readBitmap(file: File, maxEdge: Int = 1600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format could not be read. Try a JPEG or PNG photo." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        val image = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode photo.")
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(image.width, image.height))
        if (scale == 1f) return image
        val resized = Bitmap.createScaledBitmap(image, (image.width * scale).roundToInt().coerceAtLeast(1), (image.height * scale).roundToInt().coerceAtLeast(1), true)
        if (resized !== image) image.recycle()
        return resized
    }
}
