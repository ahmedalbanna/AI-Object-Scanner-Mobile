package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class ScanStatus {
    IDLE,               // Ready to capture / wait for input
    CAPTURED,           // Image chosen, waiting to trigger analysis
    ANALYZING,          // Ring of analysis spinning
    SUCCESS,            // AI Scan processed correctly
    ERROR               // Failure happened
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ScannerViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = ReportRepository(database.reportDao())

    // --- Core States ---
    private val _status = MutableStateFlow(ScanStatus.IDLE)
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResultJson?>(null)
    val scanResult: StateFlow<ScanResultJson?> = _scanResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Active object selected within the scanner outcomes for detail display
    private val _selectedObject = MutableStateFlow<ScannedObject?>(null)
    val selectedObject: StateFlow<ScannedObject?> = _selectedObject.asStateFlow()

    // Preferences and local parameters
    private val _isArabic = MutableStateFlow(true)
    val isArabic: StateFlow<Boolean> = _isArabic.asStateFlow()

    private val _showOnlyOffline = MutableStateFlow(false)
    val showOnlyOffline: StateFlow<Boolean> = _showOnlyOffline.asStateFlow()

    private val _scannedReportId = MutableStateFlow<Long?>(null)
    val scannedReportId: StateFlow<Long?> = _scannedReportId.asStateFlow()

    // --- Search & History states ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Reactive list of saved reports
    val savedReports: StateFlow<List<DbReport>> = _searchQuery
        .debounce(250)
        .flatMapLatest { query ->
            if (query.trim().isEmpty()) {
                repository.allReports
            } else {
                repository.searchReports(query)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently viewed historical record (when clicked in the History tab)
    private val _viewingHistoryReport = MutableStateFlow<DbReport?>(null)
    val viewingHistoryReport: StateFlow<DbReport?> = _viewingHistoryReport.asStateFlow()

    // --- Operations ---

    fun setLanguage(isAr: Boolean) {
        _isArabic.value = isAr
    }

    fun toggleLanguage() {
        _isArabic.value = !_isArabic.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectObject(obj: ScannedObject?) {
        _selectedObject.value = obj
    }

    fun setViewingHistoryReport(report: DbReport?) {
        _viewingHistoryReport.value = report
    }

    /**
     * Resets the scanning state machine back to idle step
     */
    fun resetScanner() {
        _status.value = ScanStatus.IDLE
        _currentBitmap.value = null
        _scanResult.value = null
        _errorMessage.value = null
        _selectedObject.value = null
        _scannedReportId.value = null
    }

    /**
     * Invoked when an image is taken by the camera or picked from storage
     */
    fun setImageSource(bitmap: Bitmap) {
        _currentBitmap.value = bitmap
        _status.value = ScanStatus.CAPTURED
        _scanResult.value = null
        _errorMessage.value = null
        _selectedObject.value = null
        _scannedReportId.value = null
    }

    /**
     * Executes the cloud Gemini analysis OR fallback local simulation based on parameters
     */
    /**
     * Enriches scan results with Wikipedia articles for highly confident targets (>= 70%)
     */
    private suspend fun enrichWithWikipedia(scanResult: ScanResultJson?): ScanResultJson? {
        if (scanResult == null) return null
        
        val enrichedObjects = scanResult.objects.map { obj ->
            if (obj.confidence >= 70.0) {
                var updatedMarkdownEn = obj.markdownReportEn
                var updatedMarkdownAr = obj.markdownReportAr
                var finalWikiUrl: String? = null
                var finalWikiSummary: String? = null
                
                // Fetch English Wikipedia details
                try {
                    val enResult = WikipediaClient.fetchSummaryAndUrl(obj.nameEn, useArabic = false)
                    if (enResult != null) {
                        val (title, extract, url) = enResult
                        finalWikiUrl = url
                        finalWikiSummary = extract
                        
                        updatedMarkdownEn = """
                            $updatedMarkdownEn
                            
                            ---
                            ### 🌐 External Knowledge (Wikipedia)
                            **_${title}_**
                            
                            $extract
                            
                            [Read full article on Wikipedia]($url)
                        """.trimIndent()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching English Wikipedia info for ${obj.nameEn}", e)
                }
                
                // Fetch Arabic Wikipedia details
                try {
                    val arResult = WikipediaClient.fetchSummaryAndUrl(obj.nameAr, useArabic = true)
                    if (arResult != null) {
                        val (title, extract, url) = arResult
                        if (_isArabic.value || finalWikiUrl == null) {
                            finalWikiUrl = url
                            finalWikiSummary = extract
                        }
                        
                        updatedMarkdownAr = """
                            $updatedMarkdownAr
                            
                            ---
                            ### 🌐 مصادر المعرفة الخارجية (ويكيبيديا)
                            **_${title}_**
                            
                            $extract
                            
                            [اقرأ المقالة الكاملة على ويكيبيديا]($url)
                        """.trimIndent()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching Arabic Wikipedia info for ${obj.nameAr}", e)
                }
                
                obj.copy(
                    markdownReportEn = updatedMarkdownEn,
                    markdownReportAr = updatedMarkdownAr,
                    wikipediaUrl = finalWikiUrl,
                    wikipediaSummary = finalWikiSummary
                )
            } else {
                obj
            }
        }
        
        return scanResult.copy(objects = enrichedObjects)
    }

    /**
     * Executes the cloud Gemini analysis OR fallback local simulation based on parameters
     */
    fun runIntelligentScan(forceOffline: Boolean = false) {
        val bitmap = _currentBitmap.value
        if (bitmap == null) {
            _errorMessage.value = if (_isArabic.value) "برجاء توفير صورة للمسح أولاً!" else "Please provide an image first!"
            _status.value = ScanStatus.ERROR
            return
        }

        _status.value = ScanStatus.ANALYZING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                var result: ScanResultJson? = null

                if (!forceOffline) {
                    // Attempt real vision search via Gemini REST
                    result = GeminiClient.analyzeImage(bitmap)
                }

                if (result != null) {
                    // Enrich result with Wikipedia (FR-004) before updating UI state and saving!
                    val enrichedResult = enrichWithWikipedia(result) ?: result
                    
                    // Success! Store findings & update states
                    _scanResult.value = enrichedResult
                    _status.value = ScanStatus.SUCCESS
                    if (enrichedResult.objects.isNotEmpty()) {
                        _selectedObject.value = enrichedResult.objects.first()
                        
                        // Automatically save this scan report to DB for history (FR-005.1)
                        saveReportToDatabase(bitmap, enrichedResult)
                    }
                } else {
                    // Fallback locally as required by NFR-008 (Offline Mode / Missing keys)
                    Log.i(TAG, "Entering offline analyzer fallback.")
                    val offlineResult = withContext(Dispatchers.Default) {
                        OfflineSimulator.analyzeOffline(bitmap)
                    }
                    
                    // Live Wikipedia lookup if internet is active during local simulation
                    val enrichedOffline = enrichWithWikipedia(offlineResult) ?: offlineResult
                    
                    _scanResult.value = enrichedOffline
                    _status.value = ScanStatus.SUCCESS
                    _errorMessage.value = if (_isArabic.value) {
                        "تم تفعيل المعالجة المحلية على الهاتف لعدم توفر خادم سحابي"
                    } else {
                        "On-device processing active (unavailable Cloud connection)"
                    }
                    if (enrichedOffline.objects.isNotEmpty()) {
                        _selectedObject.value = enrichedOffline.objects.first()
                        saveReportToDatabase(bitmap, enrichedOffline)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during scan procedure", e)
                _status.value = ScanStatus.ERROR
                _errorMessage.value = e.localizedMessage ?: "Unknown processing failure"
            }
        }
    }

    /**
     * Load an built-in preset object directly into the scanner state machine
     * (Provides an amazing emulator experience when no camera is attached)
     */
    fun loadPresetObject(index: Int) {
        val preset = OfflineSimulator.OFFLINE_PRESETS.getOrNull(index) ?: return
        
        // Simulating a dummy small color bitmap for preset representing the object
        val width = 300
        val height = 300
        val conf = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(width, height, conf)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        
        // Coloring the dummy bitmap with descriptive color themes
        paint.color = when (index) {
            0 -> android.graphics.Color.DKGRAY // Laptop Slate
            1 -> android.graphics.Color.rgb(46, 125, 50) // Plant green
            2 -> android.graphics.Color.rgb(198, 40, 40) // Crimson Cup
            else -> android.graphics.Color.rgb(22, 160, 133) // Book Teal
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        _currentBitmap.value = bitmap
        _status.value = ScanStatus.ANALYZING

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                kotlinx.coroutines.delay(1000) // Small aesthetic transition lag
            }
            val result = ScanResultJson(
                scanTitle = if (_isArabic.value) "مسح مُدرج: ${preset.nameAr}" else "Preset: ${preset.nameEn}",
                objects = listOf(preset)
            )
            
            // Enrich presets too
            val enrichedResult = enrichWithWikipedia(result) ?: result
            
            _scanResult.value = enrichedResult
            _status.value = ScanStatus.SUCCESS
            _selectedObject.value = enrichedResult.objects.firstOrNull() ?: preset
            
            saveReportToDatabase(bitmap, enrichedResult)
        }
    }

    /**
     * Saves the ScanResult to local SQLite DB using Room
     */
    private suspend fun saveReportToDatabase(bitmap: Bitmap, response: ScanResultJson) {
        withContext(Dispatchers.IO) {
            try {
                val primaryObj = response.objects.firstOrNull() ?: return@withContext
                
                // Compress bitmap to a tiny preview base64 to save space in SQLite
                val outStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 45, outStream)
                val base64Thumbnail = Base64.encodeToString(outStream.toByteArray(), Base64.DEFAULT)

                // Structure a clean combined report to show in detail screen
                val combinedReportAr = """
                    # ${response.scanTitle}
                    
                    **اسم الشيء:** ${primaryObj.nameAr} (${primaryObj.confidence}% ثقة)
                    **الفئة:** ${primaryObj.categoryAr}
                    
                    ---
                    
                    ### 📋 الخصائص والسمات المكتشفة
                    *   **السمات البصرية:** ${primaryObj.attrsAr}
                    *   **الأبعاد التقريبية:** ${primaryObj.dimensionsAr}
                    *   **الوزن المقدر:** ${primaryObj.weightAr}
                    *   **الاستخدام الشائع:** ${primaryObj.usesAr}
                    *   **بروتوكول الصيانة والعناية:** ${primaryObj.careAr}
                    
                    ---
                    
                    ### 🔬 تقرير التحليل المفصل بالذكاء الاصطناعي
                    ${primaryObj.markdownReportAr}
                """.trimIndent()

                val combinedReportEn = """
                    # ${response.scanTitle}
                    
                    **Detected Object:** ${primaryObj.nameEn} (Confidence: ${primaryObj.confidence}%)
                    **Category:** ${primaryObj.categoryEn}
                    
                    ---
                    
                    ### 📋 Primary Characteristics
                    *   **Visual Elements:** ${primaryObj.attrsEn}
                    *   **Proportional Size:** ${primaryObj.dimensionsEn}
                    *   **Estimated Weight:** ${primaryObj.weightEn}
                    *   **Optimal Purposes:** ${primaryObj.usesEn}
                    *   **Care & Maintenance:** ${primaryObj.careEn}
                    
                    ---
                    
                    ### 🔬 Artificial Intelligence Inspection
                    ${primaryObj.markdownReportEn}
                """.trimIndent()

                val sourcesList = response.googleSearchSources ?: emptyList()
                val serializedSources = if (sourcesList.isNotEmpty()) {
                    val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, GoogleSearchSource::class.java)
                    val adapter = GeminiClient.moshi.adapter<List<GoogleSearchSource>>(listType)
                    adapter.toJson(sourcesList)
                } else {
                    null
                }

                val report = DbReport(
                    timestamp = System.currentTimeMillis(),
                    title = primaryObj.nameAr,
                    category = primaryObj.categoryAr,
                    confidence = primaryObj.confidence,
                    imageBase64 = base64Thumbnail,
                    reportMarkdown = if (_isArabic.value) combinedReportAr else combinedReportEn,
                    language = if (_isArabic.value) "ar" else "en",
                    attributes = primaryObj.attrsAr,
                    dimensions = primaryObj.dimensionsAr,
                    weight = primaryObj.weightAr,
                    uses = primaryObj.usesAr,
                    care = primaryObj.careAr,
                    boundingBox = "${primaryObj.ymin},${primaryObj.xmin},${primaryObj.ymax},${primaryObj.xmax}",
                    wikipediaUrl = primaryObj.wikipediaUrl,
                    wikipediaSummary = primaryObj.wikipediaSummary,
                    googleSearchSourcesJson = serializedSources
                )

                val insertedId = repository.insertReport(report)
                _scannedReportId.value = insertedId
            } catch (e: Exception) {
                Log.e(TAG, "Error saving scanned record inside Room database: ", e)
            }
        }
    }

    /**
     * Erase report record permanently
     */
    fun deleteReport(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReportById(id)
            if (_viewingHistoryReport.value?.id == id) {
                _viewingHistoryReport.value = null
            }
        }
    }

    /**
     * Updates the audio path of a report in the database, syncing the active detail view
     */
    fun updateReportAudioPath(reportId: Long, audioPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAudioPath(reportId, audioPath)
            
            val currentViewing = _viewingHistoryReport.value
            if (currentViewing != null && currentViewing.id == reportId) {
                _viewingHistoryReport.value = currentViewing.copy(audioPath = audioPath)
            }
        }
    }
}
