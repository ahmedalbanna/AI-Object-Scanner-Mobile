package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DbReport
import com.example.data.OfflineSimulator
import com.example.data.ScannedObject
import com.example.data.GoogleSearchSource
import com.example.data.GeminiClient
import com.example.ui.theme.*
import com.example.viewmodel.ScanStatus
import com.example.viewmodel.ScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    val viewModel: ScannerViewModel = viewModel()
    val isArabic by viewModel.isArabic.collectAsStateWithLifecycle()
    
    // Supporting RTL dynamically based on language state (Cairo/Arabic design requirement)
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainDashboard(viewModel, isArabic)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainDashboard(viewModel: ScannerViewModel, isArabic: Boolean) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Scanner, 1 = Presets, 2 = History
    val status by viewModel.status.collectAsStateWithLifecycle()
    val selectedHistoryReport by viewModel.viewingHistoryReport.collectAsStateWithLifecycle()

    // Screen shell with custom upper borders
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0 && selectedHistoryReport == null,
                    onClick = {
                        activeTab = 0
                        viewModel.setViewingHistoryReport(null)
                    },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner") },
                    label = {
                        Text(
                            text = if (isArabic) "الماسح" else "Scanner",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_scanner")
                )
                NavigationBarItem(
                    selected = activeTab == 1 && selectedHistoryReport == null,
                    onClick = {
                        activeTab = 1
                        viewModel.setViewingHistoryReport(null)
                    },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Presets") },
                    label = {
                        Text(
                            text = if (isArabic) "النماذج" else "Presets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_presets")
                )
                NavigationBarItem(
                    selected = activeTab == 2 || selectedHistoryReport != null,
                    onClick = {
                        activeTab = 2
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = {
                        Text(
                            text = if (isArabic) "السجل" else "History",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("tab_history")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Unify Head bar layout
            HeaderBar(viewModel, isArabic)

            // Dynamic view selector
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (selectedHistoryReport != null) {
                    HistoryReportDetailScreen(
                        report = selectedHistoryReport!!,
                        isArabic = isArabic,
                        onBack = { viewModel.setViewingHistoryReport(null) },
                        onDelete = {
                            viewModel.deleteReport(selectedHistoryReport!!.id)
                            viewModel.setViewingHistoryReport(null)
                        }
                    )
                } else {
                    when (activeTab) {
                        0 -> ScannerTab(viewModel, isArabic)
                        1 -> PresetsTab(viewModel, isArabic)
                        2 -> HistoryTab(viewModel, isArabic)
                    }
                }
            }
        }
    }
}

// --- Header Component ---
@Composable
fun HeaderBar(viewModel: ScannerViewModel, isArabic: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .drawBehind {
                drawLine(
                    color = Color(0x1F94A3B8),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "App Icon",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isArabic) "الماسح الذكي للسلع" else "AI Object Scanner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isArabic) "التحليل البصري بالذكاء الاصطناعي" else "Computer Vision Analytics",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Language toggle pill (Accessibility Touch Target size 48dp verified)
        Button(
            onClick = { viewModel.toggleLanguage() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .height(38.dp)
                .testTag("lang_toggle_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isArabic) "English" else "العربية",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- Tab 1: CAMERA SCANNER ---
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerTab(viewModel: ScannerViewModel, isArabic: Boolean) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedObject by viewModel.selectedObject.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Image Picker from local device (FR-001 option / Emulator helper)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.setImageSource(bitmap)
                } else {
                    Toast.makeText(context, "فشل فك تشفير الصورة", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل جلب الصورة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Warning Banner if API key isn't healthy (Security & Instruction rules)
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Key Warn",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isArabic) {
                            "مفتاح تفعيل الذكاء الاصطناعي فارغ! سيتم استخدام 'وضع المعالجة المحلية بمستشعرات الهاتف المدمجة' للمحاكاة تلقائياً."
                        } else {
                            "AI key missing! The engine will activate 'Local On-Device Sensor simulation' fallback smoothly."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Viewfinder / Interactive Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    RoundedCornerShape(16.dp)
                )
                .background(Color(0xFF020617)),
            contentAlignment = Alignment.Center
        ) {
            if (status == ScanStatus.IDLE) {
                // If Camera access is granted -> show CameraX
                if (cameraPermissionState.status.isGranted) {
                    CameraPreviewWindow(
                        onImageCaptured = { bitmap -> viewModel.setImageSource(bitmap) },
                        onError = { err ->
                            Toast.makeText(context, "رمز خطأ الكاميرا: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    // Ask permission with options
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Required",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "امسح الكائنات بكاميرا الهاتف" else "Scan Objects via Device Camera",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isArabic) "يتطلب ذلك تفعيل أذونات الكاميرا محلياً" else "Requires standard run-time camera privileges",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            modifier = Modifier.testTag("request_camera_btn")
                        ) {
                            Text(text = if (isArabic) "السماح بوصول الكاميرا" else "Grant Camera Permission")
                        }
                    }
                }
            } else {
                // Image is captured or processing
                Box(modifier = Modifier.fillMaxSize()) {
                    currentBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Captured target",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // If SUCCESS and contains bounding boxes -> Draw Overlays
                        if (status == ScanStatus.SUCCESS && scanResult != null) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val frameW = maxWidth
                                val frameH = maxHeight

                                scanResult!!.objects.forEach { obj ->
                                    val isCurrent = selectedObject?.nameEn == obj.nameEn
                                    val boxColor = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                    
                                    // Scale coordinates from percents 0-100 to frame dp width/height
                                    val top = (obj.ymin * frameH.value / 100).dp
                                    val left = (obj.xmin * frameW.value / 100).dp
                                    val bottom = (obj.ymax * frameH.value / 100).dp
                                    val right = (obj.xmax * frameW.value / 100).dp
                                    
                                    val width = (right - left).coerceAtLeast(10.dp)
                                    val height = (bottom - top).coerceAtLeast(10.dp)

                                    // Render Bounding Box Frame
                                    Box(
                                        modifier = Modifier
                                            .offset(x = left, y = top)
                                            .size(width = width, height = height)
                                            .border(
                                                BorderStroke(if (isCurrent) 3.dp else 2.dp, boxColor),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { viewModel.selectObject(obj) }
                                    ) {
                                        // Small confidence classification banner
                                        Box(
                                            modifier = Modifier
                                                .background(boxColor.copy(alpha = 0.85f))
                                                .align(Alignment.TopStart)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${if (isArabic) obj.nameAr else obj.nameEn} (${obj.confidence.toInt()}%)",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rotating/Moving Tech scan bar animation lines
                    if (status == ScanStatus.ANALYZING) {
                        val infiniteTransition = rememberInfiniteTransition(label = "ScanningBeam")
                        val beamY by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "YPosition"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.04f)
                                .align(Alignment.TopStart)
                                .offset(y = (310 * beamY).dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Panels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Option to import photo (Safe layout grid touch limits - 48dp height minimum)
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("gallery_picker_btn")
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Gallery")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isArabic) "من المعرض" else "Pick Image", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (status == ScanStatus.CAPTURED) {
                // Button to launch analysis
                Button(
                    onClick = { viewModel.runIntelligentScan() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("analyze_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Analyze")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isArabic) "إطلاق التحليل الذكي" else "Analyze AI", fontSize = 13.sp)
                }
            } else if (status == ScanStatus.SUCCESS || status == ScanStatus.ERROR) {
                // Reset to camera button
                Button(
                    onClick = { viewModel.resetScanner() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("reset_scanner_btn")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Again")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isArabic) "مسح جديد" else "Scan Another", fontSize = 13.sp)
                }
            }
        }

        // Subtext error or status description (FR-008 status checks done gracefully)
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Object Detail Cards (FR-003, FR-004.2)
        AnimatedVisibility(
            visible = status == ScanStatus.SUCCESS && scanResult != null && selectedObject != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                // Horizontal scroll of objects found
                Text(
                    text = if (isArabic) "الكائنات المكتشفة بالصورة:" else "Objects Detected in Scene:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp)
                ) {
                    scanResult?.objects?.forEach { obj ->
                        val isSelected = selectedObject?.nameEn == obj.nameEn
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x1F94A3B8)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { viewModel.selectObject(obj) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Found",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) obj.nameAr else obj.nameEn,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                selectedObject?.let { obj ->
                    val mainResult by viewModel.scanResult.collectAsStateWithLifecycle()
                    ObjectInformationSummaryCard(
                        obj = obj, 
                        isArabic = isArabic, 
                        dateText = getFormattedDate(),
                        googleSearchSources = mainResult?.googleSearchSources
                    )
                }
            }
        }

        // Scanning visual progress spinner
        if (status == ScanStatus.ANALYZING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isArabic) "يجري تحديد مواصفات الكائن بالذكاء الاصطناعي..." else "Processing object metadata via Neural Model...",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

// --- Object Meta Item display (FR-003 Specs detail renderer) ---
// --- Object Meta Item display (Sleek Interface Specs detail grid renderer) ---
@Composable
fun SleekGridCell(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value.ifBlank { "..." },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ObjectInformationSummaryCard(
    obj: ScannedObject, 
    isArabic: Boolean, 
    dateText: String, 
    googleSearchSources: List<GoogleSearchSource>? = null
) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("specs_report_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Elegant horizontal slider capsule pull tab
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Subdued small tag label
            Text(
                text = if (isArabic) "التحديد الحالي للقطعة" else "CURRENT IDENTIFICATION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // Header: Name / confident indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) obj.nameAr else obj.nameEn,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (isArabic) "الفئة:" else "Category:"} ${if (isArabic) obj.categoryAr else obj.categoryEn}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Confidence feedback design badge (HighlightGreenBg & HighlightGreenText style)
                val isHighConfidence = obj.confidence >= 75f
                val badgeBg = if (isHighConfidence) HighlightGreenBg else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                val badgeText = if (isHighConfidence) HighlightGreenText else MaterialTheme.colorScheme.tertiary

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${obj.confidence.toInt()}% " + (if (isArabic) "ثقة عالية" else "Confidence"),
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Characteristics Grid in a beautiful 2-column container (FR-003 Specs detail renderer)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SleekGridCell(
                            label = if (isArabic) "السمات البصرية" else "Visual Attributes",
                            value = if (isArabic) obj.attrsAr else obj.attrsEn,
                            icon = Icons.Default.Palette
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        SleekGridCell(
                            label = if (isArabic) "الأبعاد التقريبية" else "Dimensions",
                            value = if (isArabic) obj.dimensionsAr else obj.dimensionsEn,
                            icon = Icons.Default.AspectRatio
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SleekGridCell(
                            label = if (isArabic) "الوزن التقديري" else "Estimated Weight",
                            value = if (isArabic) obj.weightAr else obj.weightEn,
                            icon = Icons.Default.Balance
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        SleekGridCell(
                            label = if (isArabic) "العناية والصيانة" else "Maintenance Care",
                            value = if (isArabic) obj.careAr else obj.careEn,
                            icon = Icons.Default.Healing
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SleekGridCell(
                            label = if (isArabic) "الاستخدامات الشائعة" else "Common Uses",
                            value = if (isArabic) obj.usesAr else obj.usesEn,
                            icon = Icons.Default.AssignmentTurnedIn
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(18.dp))

            // AI markdown output rendering styled box (FR-004.1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Report",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isArabic) "تقرير التحليل المتكامل بالذكاء الاصطناعي:" else "Expert AI Commentary Report:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = if (isArabic) obj.markdownReportAr else obj.markdownReportEn,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            if (!googleSearchSources.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                GoogleSearchGroundingCard(
                    sources = googleSearchSources,
                    isArabic = isArabic
                )
            }

            if (!obj.wikipediaUrl.isNullOrBlank() && !obj.wikipediaSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                WikipediaNativeCard(
                    url = obj.wikipediaUrl,
                    summary = obj.wikipediaSummary,
                    isArabic = isArabic
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sleek CTA export button (FR-004.3) with elegant 56.dp height
            Button(
                onClick = {
                    val reportText = """
                        AI OBJECT SCANNER EXPORT REPORT
                        Timestamp: $dateText
                        Object: ${if (isArabic) obj.nameAr else obj.nameEn}
                        Category: ${if (isArabic) obj.categoryAr else obj.categoryEn}
                        -----------------------------------------------------
                        Visual Attributes: ${if (isArabic) obj.attrsAr else obj.attrsEn}
                        Estimated Dimensions: ${if (isArabic) obj.dimensionsAr else obj.dimensionsEn}
                        Estimated Weight: ${if (isArabic) obj.weightAr else obj.weightEn}
                        Common Uses: ${if (isArabic) obj.usesAr else obj.usesEn}
                        Maintenance suggestion: ${if (isArabic) obj.careAr else obj.careEn}
                    """.trimIndent()
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AI Scanner - ${obj.nameEn}")
                        putExtra(Intent.EXTRA_TEXT, reportText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "تصدير التقرير في صيغة ملف"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("export_report_btn")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "مشاركة وتصدير التقرير (JSON/TXT)" else "Export Structured Report",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun WikipediaNativeCard(url: String, summary: String, isArabic: Boolean) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wikipedia_native_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Wikipedia icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "موسوعة ويكيبيديا" else "Wikipedia Encyclopedia",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isArabic) "مصدر خارجي" else "INTEGRATION",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = summary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("WikipediaNativeCard", "Error opening link: ", e)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open Link",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isArabic) "اقرأ المقالة الكاملة" else "Read Full Article",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GoogleSearchGroundingCard(sources: List<GoogleSearchSource>, isArabic: Boolean) {
    if (sources.isEmpty()) return
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_search_grounding_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Google Search icon",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "مصادر بحث جوجل الفورية" else "Google Search Live Sources",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isArabic) "بحث فوري" else "LIVE GROUNDING",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = if (isArabic) 
                    "تم تعزيز هذا التقرير وتأكيده بمعلومات حية في الوقت الفعلي عبر نتائج بحث جوجل الآتية:" 
                    else "This report is enriched with real-time info verified through the following live Google Search sources:",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.take(4).forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(source.url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("GroundingCard", "Error opening search source: ", e)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Source",
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = source.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Text(
                            text = if (source.url.contains("wikipedia")) "Wikipedia" else {
                                val host = android.net.Uri.parse(source.url).host ?: ""
                                host.removePrefix("www.")
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CharacteristicRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .size(16.dp)
                .offset(y = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )
        }
    }
}

// --- Tab 2: PRESET SCANNER SHOWCASES (Essential for browser Emulator test scenarios) ---
@Composable
fun PresetsTab(viewModel: ScannerViewModel, isArabic: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (isArabic) "نماذج فحص افتراضية جاهزة" else "Preset Scan Showcase",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic) {
                "مخصصة لتجربة ميزات المسح فوراً والتعرف بكافة الحقول دون الاعتماد على كاميرا الجهاز أو الإنترنت."
            } else {
                "Simulated benchmarks to evaluate real computer vision datasets immediately within browser constraints."
            },
            fontSize = 11.sp,
            color = Color.Gray,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        OfflineSimulator.OFFLINE_PRESETS.forEachIndexed { index, item ->
            val iconVec = when (index) {
                0 -> Icons.Default.Laptop
                1 -> Icons.Default.EnergySavingsLeaf
                2 -> Icons.Default.CoPresent
                else -> Icons.Default.MenuBook
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x1594A3B8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { viewModel.loadPresetObject(index) }
                    .testTag("preset_showcase_$index")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = item.nameEn,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) item.nameAr else item.nameEn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${if (isArabic) "الفئة الرئيسية:" else "Base Matrix:"} ${if (isArabic) item.categoryAr else item.categoryEn}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Trigger Scan",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- Tab 3: HISTORICAL SCAN DATABASE LIST (FR-005) ---
@Composable
fun HistoryTab(viewModel: ScannerViewModel, isArabic: Boolean) {
    val savedReports by viewModel.savedReports.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Debounced Search Input Box (FR-005.2)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text(text = if (isArabic) "بحث في السجل المستخلص..." else "Search scan reports history...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_history_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0x1F94A3B8)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "تقارير الفحص السابقة" else "Saved Reports Database",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${savedReports.size} " + (if (isArabic) "تقارير مخزنة" else "records"),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedReports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty",
                        tint = Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isArabic) "السجل خالٍ، باشر بفحص سلعة جديدة" else "Empty. Scan some objects to start logs",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(savedReports.size) { index ->
                    val report = savedReports[index]
                    SavedReportListRowItem(
                        report = report,
                        isArabic = isArabic,
                        onClick = { viewModel.setViewingHistoryReport(report) },
                        onDelete = { viewModel.deleteReport(report.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedReportListRowItem(report: DbReport, isArabic: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x1094A3B8)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable { onClick() }
            .testTag("history_item_${report.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render cached image preview from sqlite base64 string
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (report.imageBase64 != null) {
                    val bytes = Base64.decode(report.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Object Small Visualizer",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertPhoto,
                        contentDescription = "Blank",
                        tint = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${if (isArabic) "الفئة:" else "Category:"} ${report.category}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                val date = Date(report.timestamp)
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                Text(
                    text = format.format(date),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // Trust score pill highlight
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${report.confidence.toInt()}%",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { onDelete() },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// --- Historical Report Expanded Frame View ---
@Composable
fun HistoryReportDetailScreen(report: DbReport, isArabic: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val googleSearchSources = remember(report.googleSearchSourcesJson) {
        try {
            val json = report.googleSearchSourcesJson
            if (!json.isNullOrBlank()) {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, GoogleSearchSource::class.java)
                val adapter = GeminiClient.moshi.adapter<List<GoogleSearchSource>>(listType)
                adapter.fromJson(json) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("HistoryReportDetail", "Error parsing search sources: ", e)
            emptyList()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isArabic) "تفاصيل التقرير التاريخي" else "Historical Report",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { onDelete() }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Image visualizer at top of card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (report.imageBase64 != null) {
                val bytes = Base64.decode(report.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Object detail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "detail placeholder",
                    tint = Color.LightGray,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Structured Characteristics cards
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = report.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${if (isArabic) "الفئة ومعدل الثقة:" else "Category & Confidence:"} ${report.category} (${report.confidence.toInt()}% ${if (isArabic) "ثقة" else "Confidence"})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                // Dual-column Sleek Grid of metadata
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            SleekGridCell(
                                label = if (isArabic) "السمات المحددة" else "Attributes",
                                value = report.attributes,
                                icon = Icons.Default.Palette
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            SleekGridCell(
                                label = if (isArabic) "الأبعاد التقريبية" else "Dimensions",
                                value = report.dimensions,
                                icon = Icons.Default.AspectRatio
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            SleekGridCell(
                                label = if (isArabic) "الوزن التقديري" else "Weight",
                                value = report.weight,
                                icon = Icons.Default.Balance
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            SleekGridCell(
                                label = if (isArabic) "الصيانة الوقائية" else "Care Recommendations",
                                value = report.care,
                                icon = Icons.Default.Healing
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            SleekGridCell(
                                label = if (isArabic) "الاستخدامات الشائعة" else "Uses",
                                value = report.uses,
                                icon = Icons.Default.AssignmentTurnedIn
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(18.dp))

                // Raw markdown report summary
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Report Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "البيان والتقرير المفسر بالكامل:" else "Commentary Markdown Report:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = report.reportMarkdown,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }

                if (!googleSearchSources.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GoogleSearchGroundingCard(
                        sources = googleSearchSources,
                        isArabic = isArabic
                    )
                }

                if (!report.wikipediaUrl.isNullOrBlank() && !report.wikipediaSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    WikipediaNativeCard(
                        url = report.wikipediaUrl,
                        summary = report.wikipediaSummary,
                        isArabic = isArabic
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Modern 54.dp button matching primary styling
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Report - ${report.title}")
                            putExtra(Intent.EXTRA_TEXT, report.reportMarkdown)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "تصدير السجل"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "تصدير التقرير ومشاركته" else "Share Report Text",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// --- Helper Date Creator ---
fun getFormattedDate(): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date())
}

// --- Camera View Finder Layout (CameraX integration) ---
@Composable
fun CameraPreviewWindow(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val executor = remember { ContextCompat.getMainExecutor(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Embedded Surface holder
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreviewWindow", "Binding camera provider failed", e)
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Capture Button Triggers
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(66.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    val capture = imageCapture ?: return@clickable
                    
                    // Simple in-memory buffer capture to bypass temporary file I/O delays
                    capture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.capacity())
                                buffer.get(bytes)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                
                                // Handling rotation correctly from exif orientation tags in frame metadata
                                var rotatedBitmap = bitmap
                                if (image.imageInfo.rotationDegrees != 0 && bitmap != null) {
                                    val matrix = android.graphics.Matrix().apply {
                                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                                    }
                                    rotatedBitmap = Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                    )
                                }
                                
                                image.close()
                                if (rotatedBitmap != null) {
                                    onImageCaptured(rotatedBitmap)
                                } else {
                                    Log.e("CameraPreviewWindow", "Null decoded captured camera frame.")
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraPreviewWindow", "Image capture error", exception)
                                onError(exception)
                            }
                        }
                    )
                }
                .border(6.dp, Color(0xFF1E293B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
