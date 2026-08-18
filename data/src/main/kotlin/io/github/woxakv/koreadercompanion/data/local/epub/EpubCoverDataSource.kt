package io.github.woxakv.koreadercompanion.data.local.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

class EpubCoverDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun extractTitle(documentFile: DocumentFile): String? = withContext(dispatcherProvider.io) {
        EpubCoverExtractor.extractTitle { openStream(documentFile) }
    }

    suspend fun extractCoverBytes(documentFile: DocumentFile): ByteArray? = withContext(dispatcherProvider.io) {
        val raw = EpubCoverExtractor.extractCoverBytes { openStream(documentFile) } ?: return@withContext null
        downsample(raw)
    }

    private fun openStream(documentFile: DocumentFile): InputStream =
        context.contentResolver.openInputStream(documentFile.uri)
            ?: throw FileNotFoundException(documentFile.uri.toString())

    /**
     * Real book covers can be a full-page-resolution JPEG/PNG (easily
     * 1000px+ and several hundred KB to a few MB), but this is only ever
     * shown at a small thumbnail size. Beyond wasted memory, an
     * undownsampled bitmap embedded in the widget's RemoteViews can exceed
     * the binder transaction size limit - which fails intermittently rather
     * than obviously, showing up as "the cover disappears on a later
     * (background) refresh" even though it worked the first time.
     */
    private fun downsample(rawBytes: ByteArray, maxDimensionPx: Int = 480): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return rawBytes

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimensionPx || bounds.outHeight / (sampleSize * 2) >= maxDimensionPx) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return rawBytes

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            output.toByteArray()
        }
    }
}
