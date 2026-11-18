package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CardCoverImageHelper {

  private const val COVERS_DIR = "card_covers"
  private const val MAX_DIMENSION_PX = 1080

  suspend fun saveCoverFromUri(
    context: Context,
    cardId: Long,
    sourceUri: Uri,
  ): String? =
    withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      val coversDir = File(appContext.filesDir, COVERS_DIR).apply { mkdirs() }
      val outFile = File(coversDir, "cover_${cardId}_${System.currentTimeMillis()}.jpg")

      val resolver = appContext.contentResolver
      val decoded =
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(sourceUri)?.use { stream ->
              BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val sampleSize =
              calculateSampleSize(
                width = boundsOpts.outWidth,
                height = boundsOpts.outHeight,
                maxDim = MAX_DIMENSION_PX,
              )
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(sourceUri)?.use { stream ->
              BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
          }
          .getOrNull()

      if (decoded != null) {
        return@withContext runCatching {
            FileOutputStream(outFile).use { fos ->
              decoded.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            decoded.recycle()
            outFile.absolutePath
          }
          .getOrNull()
      }

      runCatching {
          resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
          } ?: return@withContext null
          if (outFile.length() > 0L) outFile.absolutePath else null
        }
        .getOrNull()
    }

  suspend fun loadBitmap(path: String): Bitmap? =
    withContext(Dispatchers.IO) {
      val file = File(path)
      if (!file.exists()) return@withContext null
      runCatching {
          val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
          val sample =
            calculateSampleSize(
              width = boundsOpts.outWidth,
              height = boundsOpts.outHeight,
              maxDim = MAX_DIMENSION_PX,
            )
          val opts = BitmapFactory.Options().apply { inSampleSize = sample }
          BitmapFactory.decodeFile(file.absolutePath, opts)
        }
        .getOrNull()
    }

  fun deleteCoverFile(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching {
      val file = File(path)
      if (file.exists()) {
        file.delete()
      }
    }
  }

  private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    var curW = width
    var curH = height
    while (curW > maxDim || curH > maxDim) {
      sample *= 2
      curW /= 2
      curH /= 2
    }
    return sample
  }
}
