package com.tototools.catalogo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Guarda fotos de productos en almacenamiento interno (comprimidas).
 * Devuelve rutas absolutas que se guardan en Product.fotos.
 */
object PhotoUtils {

    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 82
    private const val DIR = "product_photos"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).also { if (!it.exists()) it.mkdirs() }

    fun createTempCameraFile(context: Context): File {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.cacheDir, "camera_$time.jpg")
    }

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Copia + comprime una imagen desde Uri (cámara o galería).
     * Devuelve la ruta absoluta del JPEG guardado, o null si falla.
     */
    fun saveCompressed(context: Context, source: Uri): String? {
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val scaled = scaleDown(original, MAX_SIDE)
                if (scaled !== original) original.recycle()

                val dest = File(dir(context), "${UUID.randomUUID()}.jpg")
                FileOutputStream(dest).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                }
                scaled.recycle()
                dest.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteIfLocal(path: String) {
        if (path.isBlank()) return
        try {
            val f = when {
                path.startsWith("file:") -> File(Uri.parse(path).path ?: return)
                path.startsWith("/") -> File(path)
                else -> return
            }
            if (f.exists()) f.delete()
        } catch (_: Exception) { }
    }

    fun deleteAll(paths: List<String>) = paths.forEach { deleteIfLocal(it) }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        val nh = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
