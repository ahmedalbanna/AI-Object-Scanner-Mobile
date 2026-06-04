package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini Request/Response Models ---

data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

data class InlineData(
    val mimeType: String,
    val data: String
)

data class Content(
    val parts: List<Part>
)

data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Candidate(
    val content: Content
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

// --- Domain Models for Scanner Output ---

data class ScannedObject(
    val nameEn: String,
    val nameAr: String,
    val categoryEn: String,
    val categoryAr: String,
    val confidence: Double,
    val ymin: Int, // 0 to 100 percentage
    val xmin: Int, // 0 to 100 percentage
    val ymax: Int, // 0 to 100 percentage
    val xmax: Int, // 0 to 100 percentage
    val attrsAr: String,
    val attrsEn: String,
    val dimensionsAr: String,
    val dimensionsEn: String,
    val weightAr: String,
    val weightEn: String,
    val usesAr: String,
    val usesEn: String,
    val careAr: String,
    val careEn: String,
    val markdownReportAr: String,
    val markdownReportEn: String
)

data class ScanResultJson(
    val scanTitle: String,
    val objects: List<ScannedObject>
)

// --- Retrofit API Service Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- API Client ---

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    // Helper to convert Bitmap to Base64 (using low height/width to stay within safety limits & token limits)
    fun Bitmap.toResizedBase64(): String {
        val maxSize = 720
        val width = this.width
        val height = this.height
        val bitmap = if (width > maxSize || height > maxSize) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
            val newHeight = if (ratio > 1) (maxSize / ratio).toInt() else maxSize
            Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
        } else {
            this
        }
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Call the Gemini REST model on the captured image to detect items and fetch structural details
     */
    suspend fun analyzeImage(bitmap: Bitmap): ScanResultJson? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured or placeholder.")
            return null
        }

        val base64Image = bitmap.toResizedBase64()
        
        val systemPrompt = "You are a professional computer vision assistant and advanced object scanner. Analyze the image and output structured JSON only. Do not wrap response in markdown blocks."
        
        val userPrompt = """
            Analyze the attached image. Identify all distinct major objects.
            For each object. detected:
            1. Return its English/Arabic name, general English/Arabic category.
            2. Compute bounding box coordinates (ymin, xmin, ymax, xmax) on a scale from 0 to 100 percentage.
            3. Extract its estimated physical properties (Visual Attributes, Estimated Dimensions, Estimated Weight, Common Uses, Maintenance suggestions) in both English and Arabic.
            4. Provide a structured markdown report for this object in both languages.
            
            Format the response strictly as a clean JSON matching this pattern, do not include any other text or markdown markers:
            {
              "scanTitle": "Title of the scanned scene/room in Arabic",
              "objects": [
                {
                  "nameEn": "Primary English name",
                  "nameAr": "الاسم بالعربية",
                  "categoryEn": "English Category",
                  "categoryAr": "الفئة بالعربية",
                  "confidence": 95.0,
                  "ymin": 10,
                  "xmin": 20,
                  "ymax": 80,
                  "xmax": 90,
                  "attrsAr": "السمات المرئية بالتفصيل (مثل فضي، معدني، مستطيل الشاشات)",
                  "attrsEn": "detailed visual attributes (e.g. silver screen, metallic body, sleek dimensions)",
                  "dimensionsAr": "الأبعاد التقديرية (مثل 14 بوصة أو 32 سم طول)",
                  "dimensionsEn": "estimated dimensions (e.g. 14 inches or 32 cm)",
                  "weightAr": "الوزن التقديري (مثل 1.2 كجم)",
                  "weightEn": "estimated weight (e.g. 1.2 kg)",
                  "usesAr": "الإنتاجية، كتابة الأكواد، الترفيه",
                  "usesEn": "productivity, coding, entertainment",
                  "careAr": "تجنب السوائل واستخدام منظف شاشات ناعم",
                  "careEn": "avoid liquids and use a clean microfiber cloth",
                  "markdownReportAr": "### 💻 الاسم الأساسي\n- **التصنيف:** فئة الكائن\n- **العناية الأفضل:** توصية الصيانة",
                  "markdownReportEn": "### 💻 Primary Name\n- **Category:** category\n- **Best Care:** care guidelines"
                }
              ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)),
                        Part(text = userPrompt)
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                Log.d(TAG, "Gemini response text: $jsonText")
                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()
                val adapter = moshi.adapter(ScanResultJson::class.java)
                adapter.fromJson(cleanJson)
            } else {
                Log.e(TAG, "Response candidates or parts are empty.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Gemini API: ", e)
            null
        }
    }
}
