package com.nexora.editorpro.transformer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.graphics.toColorInt
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.nexora.editorpro.model.ExportConfiguration
import com.nexora.editorpro.model.FilterDefinition
import com.nexora.editorpro.model.TextOverlay
import com.nexora.editorpro.model.VideoFilterType
import com.nexora.editorpro.model.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class VideoTransformerEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun exportVideo(
        project: VideoProject,
        config: ExportConfiguration,
        onProgress: (Int, String) -> Unit
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val cacheOutputFile = File(context.cacheDir, "export_${System.currentTimeMillis()}_${config.filename}.mp4")
        val videoUri = Uri.parse(project.videoUri)

        // 1. Clipping for trimming
        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
        if (project.trimStartMs > 0) clippingBuilder.setStartPositionMs(project.trimStartMs)
        if (project.trimEndMs > project.trimStartMs && project.trimEndMs < project.durationMs) {
            clippingBuilder.setEndPositionMs(project.trimEndMs)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        // 2. Visual Effects (Resolution, Color Filter, Text Overlays)
        val effectsList = mutableListOf<Effect>()
        val targetWidth = if (project.width > project.height) config.resolution.width else config.resolution.height
        val targetHeight = if (project.width > project.height) config.resolution.height else config.resolution.width
        effectsList.add(Presentation.createForWidthAndHeight(targetWidth, targetHeight, Presentation.LAYOUT_SCALE_TO_FIT))

        createFilterEffect(project.filterType)?.let { effectsList.add(it) }
        if (project.textOverlays.isNotEmpty()) {
            createTextOverlayEffect(project.textOverlays, targetWidth, targetHeight)?.let { effectsList.add(it) }
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(ImmutableList.of(), ImmutableList.copyOf(effectsList)))
            .setRemoveAudio(project.originalAudioMuted)
            .build()

        // 3. Media3 Transformer execution
        suspendCancellableCoroutine { continuation ->
            val progressHolder = ProgressHolder()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        stopProgressPolling()
                        continuation.resume(Unit)
                    }
                    override fun onError(c: Composition, res: ExportResult, err: ExportException) {
                        stopProgressPolling()
                        continuation.resumeWithException(err)
                    }
                }).build()

            activeTransformer = transformer
            transformer.start(editedMediaItem, cacheOutputFile.absolutePath)

            progressRunnable = object : Runnable {
                override fun run() {
                    activeTransformer?.let { t ->
                        val state = t.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val pct = progressHolder.progress.coerceIn(0, 99)
                            onProgress(pct, "Rendering video with Media3 Transformer ($pct%)…")
                        }
                        mainHandler.postDelayed(this, 250)
                    }
                }
            }
            mainHandler.post(progressRunnable!!)
        }

        onProgress(98, "Saving video to Android MediaStore…")
        val savedUri = saveToMediaStore(cacheOutputFile, config.filename)
        onProgress(100, "Export completed successfully!")
        Pair(savedUri, cacheOutputFile.absolutePath)
    }

    private fun saveToMediaStore(sourceFile: File, title: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NexoraEditorPro")
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { it.copyTo(out) }
        }
        return uri
    }
}