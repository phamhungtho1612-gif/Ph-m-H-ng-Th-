package com.example.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropAspectPreset(val label: String, val ratio: Float?) {
    FREE("Gốc", null),
    SQUARE_1_1("1:1", 1f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    LANDSCAPE_4_3("4:3", 4f / 3f)
}

data class CropAdjustParams(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
    val zoom: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f
)

object ImageProcessing {

    private const val TAG = "ImageProcessing"
    private const val MAX_CAPTURED_DIMENSION = 1800
    private const val URI_DECODE_TIMEOUT_MS = 15_000L

    fun applyCropAndAdjust(source: Bitmap, params: CropAdjustParams): Bitmap {
        val normRotation = ((params.rotationDegrees % 360) + 360) % 360
        val orientationMatrix = Matrix().apply {
            if (normRotation != 0) {
                postRotate(normRotation.toFloat())
            }
            if (params.flipHorizontal) {
                postScale(-1f, 1f)
            }
        }

        val oriented = if (!orientationMatrix.isIdentity) {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                orientationMatrix,
                true
            )
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }

        val ow = oriented.width.toFloat()
        val oh = oriented.height.toFloat()
        val targetAspect = params.aspectPreset.ratio ?: (ow / oh)

        val baseCropW: Float
        val baseCropH: Float
        if (ow / oh > targetAspect) {
            baseCropH = oh
            baseCropW = oh * targetAspect
        } else {
            baseCropW = ow
            baseCropH = ow / targetAspect
        }

        val clampedZoom = params.zoom.coerceIn(1f, 3.5f)
        val cropW = (baseCropW / clampedZoom).coerceIn(16f, ow)
        val cropH = (baseCropH / clampedZoom).coerceIn(16f, oh)

        val maxShiftX = max(0f, (ow - cropW) / 2f)
        val maxShiftY = max(0f, (oh - cropH) / 2f)
        val centerX = ow / 2f - params.offsetXFraction.coerceIn(-1f, 1f) * maxShiftX
        val centerY = oh / 2f - params.offsetYFraction.coerceIn(-1f, 1f) * maxShiftY

        val left = (centerX - cropW / 2f).roundToInt().coerceIn(0, (oriented.width - 1).coerceAtLeast(0))
        val top = (centerY - cropH / 2f).roundToInt().coerceIn(0, (oriented.height - 1).coerceAtLeast(0))
        val finalW = cropW.roundToInt().coerceIn(1, (oriented.width - left).coerceAtLeast(1))
        val finalH = cropH.roundToInt().coerceIn(1, (oriented.height - top).coerceAtLeast(1))

        val cropped = Bitmap.createBitmap(oriented, left, top, finalW, finalH)
        if (oriented !== source && oriented !== cropped && !oriented.isRecycled) {
            oriented.recycle()
        }

        val needsColorAdjust = abs(params.brightness) > 0.5f || abs(params.contrast - 1f) > 0.01f
        val adjusted = if (needsColorAdjust) {
            val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val c = params.contrast.coerceIn(0.6f, 1.6f)
            val b = params.brightness.coerceIn(-100f, 100f) * 0.8f
            val translate = (1f - c) * 128f + b
            val cm = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(cropped, 0f, 0f, paint)
            if (!cropped.isRecycled) {
                cropped.recycle()
            }
            out
        } else {
            cropped
        }

        return scaleDownIfNeeded(adjusted, MAX_CAPTURED_DIMENSION)
    }

    fun imageProxyToBitmap(
        imageProxy: ImageProxy,
        mirrorHorizontal: Boolean
    ): Bitmap {
        val rawBitmap = if (imageProxy.format == ImageFormat.JPEG) {
            decodeSampledJpeg(imageProxy)
        } else {
            imageProxy.toBitmap()
        }
        require(rawBitmap.width > 0 && rawBitmap.height > 0) {
            "Khung hình camera rỗng"
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            if (mirrorHorizontal) {
                postScale(-1f, 1f)
            }
        }

        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
        } else {
            rawBitmap
        }

        if (transformed !== rawBitmap) {
            rawBitmap.recycle()
        }

        return scaleDownIfNeeded(transformed, MAX_CAPTURED_DIMENSION)
    }

    fun normalizePreviewBitmap(source: Bitmap): Bitmap {
        val copy = source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        return scaleDownIfNeeded(copy, MAX_CAPTURED_DIMENSION)
    }

    suspend fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? =
        withTimeoutOrNull(URI_DECODE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver

                // Strategy 1: ImageDecoder on API 28+ (handles EXIF & subsampling natively)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val decodedByDecoder = runCatching {
                        val source = ImageDecoder.createSource(resolver, uri)
                        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val largest = max(info.size.width, info.size.height)
                            if (largest > MAX_CAPTURED_DIMENSION) {
                                val ratio = MAX_CAPTURED_DIMENSION.toFloat() / largest.toFloat()
                                decoder.setTargetSize(
                                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                                    (info.size.height * ratio).toInt().coerceAtLeast(1)
                                )
                            }
                        }
                        if (decoded.width > 0 && decoded.height > 0) {
                            scaleDownIfNeeded(decoded, MAX_CAPTURED_DIMENSION)
                        } else {
                            null
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "ImageDecoder failed for $uri, falling back to stream", err)
                    }.getOrNull()

                    if (decodedByDecoder != null) {
                        return@withContext decodedByDecoder
                    }
                }

                // Strategy 2: ContentResolver InputStream + BitmapFactory + ExifInterface
                val decodedByStream = runCatching {
                    val exifRotation = resolver.openInputStream(uri)?.use { stream ->
                        readExifRotationDegrees(stream)
                    } ?: 0

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }

                    val decoded = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        val sampleSize = calculateInSampleSize(
                            bounds.outWidth,
                            bounds.outHeight,
                            MAX_CAPTURED_DIMENSION
                        )
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        resolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, opts)
                        }
                    } else {
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        resolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, opts)
                        }
                    } ?: return@runCatching null

                    if (decoded.width <= 0 || decoded.height <= 0) {
                        return@runCatching null
                    }

                    val oriented = if (exifRotation != 0) {
                        val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(
                            decoded, 0, 0, decoded.width, decoded.height, matrix, true
                        )
                        if (rotated !== decoded) {
                            decoded.recycle()
                        }
                        rotated
                    } else {
                        decoded
                    }

                    scaleDownIfNeeded(oriented, MAX_CAPTURED_DIMENSION)
                }.onFailure { err ->
                    Log.w(TAG, "Stream decode failed for $uri", err)
                }.getOrNull()

                if (decodedByStream != null) {
                    return@withContext decodedByStream
                }

                // Strategy 3: ParcelFileDescriptor fallback
                runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val fd = pfd.fileDescriptor
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
                        val sampleSize = calculateInSampleSize(
                            bounds.outWidth,
                            bounds.outHeight,
                            MAX_CAPTURED_DIMENSION
                        )
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val bmp = BitmapFactory.decodeFileDescriptor(fd, null, opts)
                            ?: return@runCatching null
                        if (bmp.width > 0 && bmp.height > 0) {
                            scaleDownIfNeeded(bmp, MAX_CAPTURED_DIMENSION)
                        } else {
                            null
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "FileDescriptor decode failed for $uri", err)
                }.getOrNull()
            }
        }

    private fun readExifRotationDegrees(inputStream: java.io.InputStream): Int {
        return runCatching {
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun decodeSampledJpeg(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val sampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxDimension = MAX_CAPTURED_DIMENSION
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        if (decoded != null) {
            return decoded
        }
        buffer.rewind()
        return imageProxy.toBitmap()
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        if (largest > maxDimension) {
            while (largest / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
