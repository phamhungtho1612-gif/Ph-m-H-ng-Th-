package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

data class SubjectAiSuggestion(
    val subjectLabel: String,
    val moodTag: String,
    val suggestedTexts: List<String>,
    val recommendedShape: OverlayTextShape,
    val recommendedStyle: OverlayTextStyle,
    val recommendedFont: OverlayFontStyle,
    val recommendedColorHex: String
)

object SubjectAiManager {

    private const val TAG = "SubjectAiManager"
    private const val SEGMENT_MAX_DIM = 1024
    private const val GEMINI_MAX_DIM = 768
    private const val SEGMENT_TIMEOUT_MS = 25_000L
    private const val GEMINI_TIMEOUT_MS = 45_000L

    private val subjectSchema = Schema.obj(
        properties = mapOf(
            "subjectLabel" to Schema.string(
                description = "Tên ngắn gọn bằng tiếng Việt mô tả chủ thể chính ở tiền cảnh (2 đến 5 từ)"
            ),
            "moodTag" to Schema.string(
                description = "Từ khóa cảm xúc/phong cách bằng tiếng Việt (1 đến 3 từ)"
            ),
            "suggestedTexts" to Schema.array(
                items = Schema.string(),
                description = "4 câu chữ nghệ thuật ngắn tiếng Việt (2 đến 4 từ) phù hợp đặt phía sau chủ thể"
            ),
            "recommendedShape" to Schema.enumeration(
                values = OverlayTextShape.entries.map { it.name },
                description = "Kiểu dáng chữ phù hợp nhất với bố cục ảnh"
            ),
            "recommendedStyle" to Schema.enumeration(
                values = OverlayTextStyle.entries.map { it.name },
                description = "Phong cách hiệu ứng chữ phù hợp nhất với màu sắc ảnh"
            ),
            "recommendedFont" to Schema.enumeration(
                values = OverlayFontStyle.entries.map { it.name },
                description = "Phông chữ phù hợp nhất với tinh thần bức ảnh"
            ),
            "recommendedColorHex" to Schema.string(
                description = "Mã màu HEX 6 ký tự nổi bật hài hòa với bức ảnh, ví dụ #FFD426 hoặc #FFFFFF"
            )
        )
    )

    private val geminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = subjectSchema
            }
        )
    }

    private fun createSegmenter(): SubjectSegmenter {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        return SubjectSegmentation.getClient(options)
    }

    /**
     * Extracts the foreground subject cutout bitmap using on-device ML Kit Subject Segmentation.
     * Transparent pixels remain around the subject so creative text can sit cleanly behind it.
     */
    suspend fun segmentForeground(context: Context, source: Bitmap): Result<Bitmap> =
        withContext(Dispatchers.Default) {
            if (source.isRecycled || source.width <= 0 || source.height <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Ảnh đầu vào không hợp lệ."))
            }

            var scaledTemp: Bitmap? = null
            val segmenter = createSegmenter()
            try {
                val workBitmap = scaleDownIfNeeded(source, SEGMENT_MAX_DIM).also {
                    if (it !== source) scaledTemp = it
                }

                // Best-effort check to trigger Play Services ML Kit module installation if missing
                runCatching {
                    val installClient = ModuleInstall.getClient(context.applicationContext)
                    val availability = installClient.areModulesAvailable(segmenter).await()
                    if (!availability.areModulesAvailable()) {
                        val request = ModuleInstallRequest.newBuilder()
                            .addApi(segmenter)
                            .build()
                        installClient.installModules(request).await()
                    }
                }

                val result = withTimeout(SEGMENT_TIMEOUT_MS) {
                    val inputImage = InputImage.fromBitmap(workBitmap, 0)
                    segmenter.process(inputImage).await()
                }

                val fgBitmap = result.foregroundBitmap
                if (fgBitmap != null && !fgBitmap.isRecycled) {
                    val safeCopy = fgBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: fgBitmap
                    Result.success(safeCopy)
                } else {
                    Result.failure(
                        IllegalStateException("Không phát hiện rõ chủ thể nổi bật để tách nền.")
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Subject segmentation timed out", e)
                Result.failure(
                    IllegalStateException("Quá thời gian tách chủ thể (25 giây). Vui lòng thử lại.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Subject segmentation failed", e)
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Thiết bị chưa tải xong mô hình tách chủ thể ML Kit."
                    )
                )
            } finally {
                runCatching { segmenter.close() }
                scaledTemp?.let {
                    if (!it.isRecycled) it.recycle()
                }
            }
        }

    /**
     * Uses Firebase AI Logic (Gemini 3.8 Flash) to recognize the foreground subject,
     * suggest short Vietnamese phrases, and pick matching typography, shape, style & color.
     */
    suspend fun analyzeSubjectForText(source: Bitmap): Result<SubjectAiSuggestion> =
        withContext(Dispatchers.IO) {
            if (source.isRecycled || source.width <= 0 || source.height <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Không có ảnh để phân tích AI."))
            }

            var scaledForAi: Bitmap? = null
            try {
                val inputBitmap = scaleDownIfNeeded(source, GEMINI_MAX_DIM).also {
                    if (it !== source) scaledForAi = it
                }

                val prompt = """
                    Hãy quan sát bức ảnh này bằng tiếng Việt.
                    1. Xác định chính xác chủ thể nổi bật nhất ở tiền cảnh (người, bạn bè, đồ vật, thú cưng...).
                    2. Gợi ý 4 câu chữ nghệ thuật cực ngắn bằng tiếng Việt (2 đến 4 từ, có thể in hoa trẻ trung hoặc tự nhiên) để chèn phía sau chủ thể giống hiệu ứng tạp chí / Locket Gold.
                    3. Chọn kiểu dáng chữ, phong cách hiệu ứng, phông chữ và mã màu HEX hài hòa nhất với chủ thể và ánh sáng của ảnh.
                """.trimIndent()

                val response = withTimeout(GEMINI_TIMEOUT_MS) {
                    geminiModel.generateContent(
                        content {
                            image(inputBitmap)
                            text(prompt)
                        }
                    )
                }

                val jsonString = response.text
                    ?: return@withContext Result.failure(
                        IllegalStateException("AI không trả về dữ liệu phân tích.")
                    )

                val json = JSONObject(jsonString)
                val subjectLabel = json.optString("subjectLabel").trim()
                    .ifBlank { "Chủ thể chính" }
                val moodTag = json.optString("moodTag").trim()
                    .ifBlank { "Sáng tạo" }

                val textsArray = json.optJSONArray("suggestedTexts")
                val suggestions = buildList {
                    if (textsArray != null) {
                        for (i in 0 until textsArray.length()) {
                            val item = textsArray.optString(i).trim()
                            if (item.isNotBlank()) add(item)
                        }
                    }
                }

                val shape = parseEnumOrDefault(
                    json.optString("recommendedShape"),
                    OverlayTextShape.ARC_UP
                )
                val style = parseEnumOrDefault(
                    json.optString("recommendedStyle"),
                    OverlayTextStyle.FROSTED_GLASS
                )
                val font = parseEnumOrDefault(
                    json.optString("recommendedFont"),
                    OverlayFontStyle.ANTON
                )
                val rawHex = json.optString("recommendedColorHex").trim()
                val validHex = if (Regex("^#[0-9A-Fa-f]{6}$").matches(rawHex)) {
                    rawHex.uppercase()
                } else {
                    "#FFFFFF"
                }

                Result.success(
                    SubjectAiSuggestion(
                        subjectLabel = subjectLabel,
                        moodTag = moodTag,
                        suggestedTexts = suggestions,
                        recommendedShape = shape,
                        recommendedStyle = style,
                        recommendedFont = font,
                        recommendedColorHex = validHex
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini subject analysis timed out", e)
                Result.failure(
                    IllegalStateException("AI phản hồi quá lâu (hết 45 giây). Vui lòng thử lại.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini subject analysis failed", e)
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Không thể kết nối AI nhận diện chủ thể."
                    )
                )
            } finally {
                scaledForAi?.let {
                    if (!it.isRecycled) it.recycle()
                }
            }
        }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / currentMax.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private inline fun <reified T : Enum<T>> parseEnumOrDefault(raw: String, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: fallback
    }
}
