package com.example.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.CapturedPhoto
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val capturedPhotos by viewModel.capturedPhotos.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.setCameraPermission(granted)
        }
    )

    // Verify permission on first launch
    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setCameraPermission(permissionCheck)
        if (!permissionCheck) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Main screen card layers
    var isGalleryOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Control Bar (Sleek minimalist hardware chassis)
            TopControlBar(
                state = state,
                onSettingsClick = { viewModel.toggleSettings() },
                onToggleCamera = { viewModel.toggleCameraSource() },
                onToggleAi = { viewModel.toggleAiSceneDetection() },
                onSelectScene = { viewModel.toggleAiPortraitLighting() } // quick action
            )

            // 2. Immersive Viewfinder Grid Frame (90% hero layout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                    .testTag("viewfinder_box")
            ) {
                // Background Viewfinder content (Procedural scene simulation)
                ViewfinderPreview(state = state, viewModel = viewModel)

                // Static Center Focus Ring matching Professional Polish UI Spec
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color.White.copy(alpha = 0.5f), CircleShape)
                            .align(Alignment.Center)
                    )
                }

                // Floating Lens Selectors matching Professional Polish UI Spec (0.6, 1x, 2, 3.5 buttons)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val lenses = listOf(
                            "0.6" to "28mm",
                            "1x" to "35mm",
                            "2" to "50mm",
                            "3.5" to "90mm"
                        )
                        lenses.forEach { (label, focalValue) ->
                            val isSelected = state.currentFocalLength == focalValue
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) DarkSurface else Color.Black.copy(alpha = 0.40f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) LeicaRed else Color.White.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.selectFocalLength(focalValue) }
                                    .testTag("lens_select_${label}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) LeicaRed else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Shutter snapshot flash curtain cover
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isShutterSnapping,
                    enter = fadeIn(animationSpec = twinPulse120()),
                    exit = fadeOut(animationSpec = twinPulse200OrLess())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }

                // Level meter overlay
                if (state.showLevelMeter) {
                    LevelMeterOverlay(
                        roll = state.levelRollAngle,
                        pitch = state.levelPitchAngle
                    )
                }

                // Frame format crop borders overlay depending on selected aspect ratios
                AspectCropOverlay(aspect = state.selectedAspect)

                // Lens focus peaking red outlines indicator
                if (!state.isAutofocus && state.manualFocus < 98f) {
                    FocusPeakingStatusBadge(focusValue = state.manualFocus)
                }

                // Video capture elapsed HUD indicator
                if (state.currentMode == CameraMode.VIDEO && state.isRecordingVideo) {
                    VideoHUDOverlay(state = state)
                }

                // AI Active overlay badge
                if (state.isAiSceneDetectionEnabled && state.aiSceneLabel != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeicaRed.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LEICA AI: ${state.aiSceneLabel}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // 3. Technical Live Parameters Exif Telemetry Bar (Permanent M11 status strip)
            LiveInformationTelemetryBar(state = state, viewModel = viewModel)

            // 4. Pro Mode Dial console drawer
            AnimatedVisibility(
                visible = state.currentMode == CameraMode.PRO,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                ProModeDialConsole(state = state, viewModel = viewModel)
            }

            // 5. Grid/Film Looks Horizontal selector (Drawer)
            AnimatedVisibility(
                visible = state.isLooksDrawerOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LeicaLooksDrawer(state = state, onLookSelected = {
                    viewModel.selectLook(it)
                    viewModel.setLooksDrawerOpen(false)
                })
            }

            // 6. Horizontal Smooth Snap Mode Carousel
            CameraModeCarousel(
                selectedMode = state.currentMode,
                onModeSelected = { viewModel.selectMode(it) }
            )

            // 7. Core Premium Hardware controls chassis (Shutter releases, Gallery feeds, Looks triggers)
            ShutterControlPlatform(
                state = state,
                viewModel = viewModel,
                galleryCount = capturedPhotos.size,
                onGalleryClick = { isGalleryOpen = true },
                onShutterClick = { viewModel.triggerShutter() },
                onLooksClick = { viewModel.toggleLooksDrawer() }
            )
        }

        // Sub-Layers & Drawers (Settings, Exif photoroll gallery view)
        if (state.isSettingsOpen) {
            LeicaSettingsTray(
                state = state,
                viewModel = viewModel,
                onClose = { viewModel.setSettingsOpen(false) }
            )
        }

        if (isGalleryOpen) {
            LeicaExifGalleryOverlay(
                capturedPhotos = capturedPhotos,
                onClose = { isGalleryOpen = false },
                onDelete = { viewModel.deletePhoto(it) },
                onClearAll = { viewModel.clearAllPhotos() }
            )
        }

        // Branding Footer matching Professional Polish UI Spec (Aitox • Leica Edition)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .alpha(0.35f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AITOX • LEICA EDITION",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
        }
    }
}

// ---------------------- Component Library ----------------------

@Composable
fun TopControlBar(
    state: CameraUiState,
    onSettingsClick: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleAi: () -> Unit,
    onSelectScene: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red dot logo + Title (Wetzlar metal plate engineering)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Leica legendary Red Dot
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(LeicaRed, CircleShape)
                    .align(Alignment.CenterVertically),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-1).dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "AITOX LEICA",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Text(
                    text = "WETZLAR GERMANY",
                    color = LightTextSecondary,
                    fontSize = 7.sp,
                    letterSpacing = 1.0.sp
                )
            }
        }

        // Tactile action utilities
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // AI indicator button (Computational Xiaomi co-processor logic)
            IconButton(
                onClick = onToggleAi,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (state.isAiSceneDetectionEnabled) LeicaRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = "AI",
                    color = if (state.isAiSceneDetectionEnabled) ColorsTextActive() else LightTextSecondary,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }

            // Quick Cycle Scene Simulation Button
            IconButton(
                onClick = onSelectScene,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCameraFront,
                    contentDescription = "Portrait Lighting",
                    tint = if (state.isAiPortraitLightingEnabled) QualitySuccess else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Gear system settings
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (state.isSettingsOpen) LeicaRed else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Chassis Settings",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ViewfinderPreview(
    state: CameraUiState,
    viewModel: CameraViewModel
) {
    val activeScene = SCENE_PRESETS[state.liveSceneIndex]
    val transitionState = remember { MutableTransitionState(state.liveSceneIndex) }
    
    // Smooth scene swipe detection and change logic
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.liveSceneIndex) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double tap triggers autofocus simulation
                        viewModel.setAutofocus()
                    },
                    onTap = {
                        // Cycle scene presets
                        val nextIndex = (state.liveSceneIndex + 1) % SCENE_PRESETS.size
                        viewModel.selectScene(nextIndex)
                    }
                )
            }
    ) {
        if (state.useRealCameraIfPossible && state.hasCameraPermission) {
            CameraXPreview(
                modifier = Modifier.fillMaxSize(),
                state = state
            )
        } else {
            // High fidelity procedural landscape scene simulator
            ProceduralSceneryRenderer(
                state = state,
                scene = activeScene
            )
        }

        // Rule grid guide lines overlay
        if (state.showGrid) {
            ViewfinderGridOverlay()
        }

        // Live Histogram overlay (Upper Right Glass coordinate)
        if (state.showHistogram) {
            LiveHistogramOverlay(state = state)
        }

        // Dynamic scene indicator overlay at the bottom border
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "TAP VIEW TO CYCLE SUBJECTS: ${activeScene.name.uppercase()}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                letterSpacing = 1.0.sp
            )
        }
    }
}

@Composable
fun CameraXPreview(
    modifier: Modifier = Modifier,
    state: CameraUiState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Real-Time High-Fidelity Leica Look Overlay using Compose Canvas Blending!
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (state.currentLook) {
                LeicaLook.BW_CLASSIC -> {
                    // Classic silver halide monochrome look via Saturation blend
                    drawRect(color = Color.Gray, blendMode = BlendMode.Saturation)
                }
                LeicaLook.BW_HIGH_CONTRAST -> {
                    // Rich deep dramatic monochrome
                    drawRect(color = Color.DarkGray, blendMode = BlendMode.Color)
                    drawRect(color = Color.Black.copy(alpha = 0.15f))
                }
                LeicaLook.SEPIA -> {
                    // Golden nostalgic sepia filter
                    drawRect(color = Color.Gray, blendMode = BlendMode.Saturation)
                    drawRect(color = Color(0xFFC0A080).copy(alpha = 0.18f), blendMode = BlendMode.Multiply)
                }
                LeicaLook.AUTHENTIC -> {
                    // Vintage color rendering with soft vignetting edges
                    drawRect(color = Color(0xFFE5D5C5).copy(alpha = 0.04f), blendMode = BlendMode.Multiply)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                            center = center,
                            radius = size.maxDimension * 0.7f
                        )
                    )
                }
                LeicaLook.VIBRANT -> {
                    // Rich punchy colors and dynamic highlight depth
                    drawRect(color = Color(0xFFFFCC33).copy(alpha = 0.04f), blendMode = BlendMode.Overlay)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                            center = center,
                            radius = size.maxDimension * 0.85f
                        )
                    )
                }
                LeicaLook.NATURAL -> {
                    // Pristine standard rendering
                }
            }
        }
    }
}

@Composable
fun ProceduralSceneryRenderer(
    state: CameraUiState,
    scene: SimulatedScene
) {
    // Dynamic color adjustments based on Look selection
    val colorFilterTint = when (state.currentLook) {
        LeicaLook.AUTHENTIC -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToScale(1.0f, 0.95f, 0.92f, 1.0f) // Slightly warm, nostalgic authentic tint
        })
        LeicaLook.VIBRANT -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(1.35f) // Rich, saturated Xiaomi co-developed colors
        })
        LeicaLook.NATURAL -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(0.95f)
        })
        LeicaLook.BW_CLASSIC -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(0.0f) // Premium silver chrome portrait gray
        })
        LeicaLook.BW_HIGH_CONTRAST -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(0.0f) // High threshold monochrome
        })
        LeicaLook.SEPIA -> ColorFilter.colorMatrix(ColorMatrix().apply {
            setToSaturation(0.4f)
        })
    }

    // Capture variables
    val focalScale by animateFloatAsState(
        targetValue = when (state.currentFocalLength) {
            "28mm" -> 1.0f
            "35mm" -> 1.25f
            "50mm" -> 1.8f
            "90mm" -> 2.8f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    )

    // Warmth color shift depending on Kelvin degrees white balance
    val wbColorTint = getKelvinTint(state.whiteBalance)
    
    // Brightness offset multiplier
    val brightnessMultiplier = 1.0f + (state.exposureValue * 0.15f)

    // Blurring value based on manual focus value (100 is infinity crisp, lower values blur scenery)
    val blurRadius = if (state.isAutofocus) 0.dp else {
        val dist = 100f - state.manualFocus
        (dist * 0.2f).coerceAtLeast(0.0f).dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(blurRadius)
            .scale(focalScale)
            .drawBehind {
                // Background Base painting
                drawRect(Color(0xFF0C0C0F))

                // Draw specific procedural vector assets
                when (scene.name) {
                    "Leica headquarters" -> drawWetzlarScene(wbColorTint, brightnessMultiplier, state.currentLook)
                    "Munich rain-streaked alley" -> drawMunichRainScene(wbColorTint, brightnessMultiplier, state.currentLook)
                    "Paris boulevard café" -> drawParisCafeScene(wbColorTint, brightnessMultiplier, state.currentLook)
                    "Studio portrait session" -> drawPortraitScene(wbColorTint, brightnessMultiplier, state.currentLook)
                    "Swiss Alps dawn" -> drawSwissAlpsScene(wbColorTint, brightnessMultiplier, state.currentLook)
                }

                // Apply Zebra Overexposure highlights simulation overlay
                if (state.showZebraPattern && state.exposureValue > 1.2f) {
                    drawZebraStripesSim()
                }

                // If focus peaking is active, draw red highlighting lines along geometry edges
                if (!state.isAutofocus && state.manualFocus < 98f) {
                    drawFocusPeakingLines(state.manualFocus, sceneName = scene.name)
                }
            }
    )
}

// ---------------------- Procedural Scene Vector Operations ----------------------

private fun DrawScope.drawWetzlarScene(tint: Color, brightness: Float, look: LeicaLook) {
    val bVal = brightness.coerceIn(0.2f, 2.0f)
    
    // Draw modern curved architectural stones
    val wallColor = Color(0xFFE2E2E6).multiply(tint).scaleColor(bVal)
    val shadowColor = Color(0xFF101115).scaleColor(bVal)
    val skyColor = Color(0xFF42566E).multiply(tint).scaleColor(bVal)
    
    // Sky
    drawRect(skyColor)
    
    // Curved building
    val path = Path().apply {
        moveTo(0f, size.height * 0.2f)
        cubicTo(
            size.width * 0.4f, size.height * 0.15f,
            size.width * 0.8f, size.height * 0.4f,
            size.width, size.height * 0.45f
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color = wallColor)

    // Structural dark column slits
    for (i in 0..5) {
        val x = size.width * (0.15f + i * 0.15f)
        drawLine(
            color = shadowColor,
            start = Offset(x, size.height * 0.35f + (i * 12f)),
            end = Offset(x - 20f, size.height),
            strokeWidth = 8f
        )
    }

    // Signature glowing small red dot on Wetzlar Headquarters facade
    drawCircle(
        color = LeicaRed.scaleColor(bVal),
        radius = 12f,
        center = Offset(size.width * 0.42f, size.height * 0.55f)
    )
}

private fun DrawScope.drawMunichRainScene(tint: Color, brightness: Float, look: LeicaLook) {
    val bVal = brightness.coerceIn(0.2f, 2.0f)
    val asphalt = Color(0xFF1B1B22).multiply(tint).scaleColor(bVal)
    val neonAmber = Color(0xFFFFB74D).multiply(tint).scaleColor(bVal)
    val wallLeft = Color(0xFF0F0F12).scaleColor(bVal)
    
    // Sky
    drawRect(Color(0xFF08080C).scaleColor(bVal))

    // Alley vertical walls projection
    val wallPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width * 0.4f, size.height * 0.5f)
        lineTo(size.width * 0.4f, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(wallPath, color = wallLeft)

    // Cobblestone floor
    val roadPath = Path().apply {
        moveTo(size.width * 0.4f, size.height * 0.5f)
        lineTo(size.width, size.height * 0.5f)
        lineTo(size.width, size.height)
        lineTo(size.width * 0.4f, size.height)
        close()
    }
    drawPath(roadPath, color = asphalt)

    // Streetlamp bright specular reflections along the wet floor
    drawCircle(
        color = neonAmber.copy(alpha = 0.5f),
        radius = 80f * bVal,
        center = Offset(size.width * 0.65f, size.height * 0.65f)
    )

    // Rainy specular linear streaks
    for (i in 0..12) {
        val yStart = size.height * (0.2f + i * 0.06f)
        drawLine(
            color = Color.White.copy(alpha = 0.15f).scaleColor(bVal),
            start = Offset(size.width * (0.5f + i * 0.04f), yStart),
            end = Offset(size.width * (0.45f + i * 0.04f), yStart + 80f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawParisCafeScene(tint: Color, brightness: Float, look: LeicaLook) {
    val bVal = brightness.coerceIn(0.2f, 2.0f)
    val woodWarm = Color(0xFF5D4037).multiply(tint).scaleColor(bVal)
    val glassReflect = Color(0xFF81D4FA).multiply(tint).scaleColor(bVal)
    val darkShadow = Color(0xFF1E1E24).scaleColor(bVal)

    // Wall Cafe window framing
    drawRect(woodWarm)

    // Glass panel window
    drawRect(
        color = darkShadow,
        topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
        size = Size(size.width * 0.8f, size.height * 0.6f)
    )

    // Soft warm illuminated cafe lamps
    drawCircle(
        color = Color(0xFFFFD54F).scaleColor(bVal).copy(alpha = 0.9f),
        radius = 50f,
        center = Offset(size.width * 0.5f, size.height * 0.28f)
    )

    // Glowing string lights cords
    drawLine(
        color = Color.White.copy(alpha = 0.4f),
        start = Offset(0f, size.height * 0.15f),
        end = Offset(size.width, size.height * 0.15f),
        strokeWidth = 3f
    )
}

private fun DrawScope.drawPortraitScene(tint: Color, brightness: Float, look: LeicaLook) {
    val bVal = brightness.coerceIn(0.2f, 2.0f)
    val backdrop = Color(0xFF263238).multiply(tint).scaleColor(bVal)
    val skinTone = Color(0xFFFFE0B2).multiply(tint).scaleColor(bVal)
    val silhouetteColor = Color(0xFF151B1F).scaleColor(bVal)

    // Backdrop grey gradient
    drawRect(backdrop)

    // Beautiful studio silhouette contours of camera model
    drawCircle(
        color = skinTone,
        radius = size.width * 0.25f,
        center = Offset(size.width * 0.5f, size.height * 0.4f)
    )

    // Beautiful glossy eye specularity
    drawCircle(
        color = Color.White,
        radius = 8f,
        center = Offset(size.width * 0.45f, size.height * 0.38f)
    )

    // Sleek clothing modeling block
    val vestPath = Path().apply {
        moveTo(size.width * 0.2f, size.height)
        cubicTo(
            size.width * 0.35f, size.height * 0.7f,
            size.width * 0.65f, size.height * 0.7f,
            size.width * 0.8f, size.height
        )
        lineTo(size.width, size.height)
        close()
    }
    drawPath(vestPath, color = silhouetteColor)
}

private fun DrawScope.drawSwissAlpsScene(tint: Color, brightness: Float, look: LeicaLook) {
    val bVal = brightness.coerceIn(0.2f, 2.0f)
    val sunriseSky = Color(0xFFE1BEE7).multiply(tint).scaleColor(bVal) // soft rose
    val darkRock = Color(0xFF2D3748).multiply(tint).scaleColor(bVal)
    val snowCap = Color(0xFFFFFFFF).scaleColor(bVal)

    // Sky gradient back
    drawRect(sunriseSky)

    // Deep jagged Alpine silhouettes
    val peakPath1 = Path().apply {
        moveTo(0f, size.height)
        lineTo(size.width * 0.35f, size.height * 0.45f)
        lineTo(size.width * 0.65f, size.height * 0.8f)
        lineTo(size.width * 0.9f, size.height * 0.38f)
        lineTo(size.width, size.height * 0.5f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(peakPath1, color = darkRock)

    // Pure brilliant peak snow caps highlights
    val cap1 = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.52f)
        lineTo(size.width * 0.35f, size.height * 0.45f)
        lineTo(size.width * 0.4f, size.height * 0.54f)
        close()
    }
    drawPath(cap1, color = snowCap)

    val cap2 = Path().apply {
        moveTo(size.width * 0.85f, size.height * 0.43f)
        lineTo(size.width * 0.9f, size.height * 0.38f)
        lineTo(size.width * 0.94f, size.height * 0.44f)
        close()
    }
    drawPath(cap2, color = snowCap)
}

private fun DrawScope.drawFocusPeakingLines(focusVal: Float, sceneName: String) {
    // Standard bright focus highlight outlines overlaying the major vectors
    val peakAlpha = 0.85f
    val fStroke = 4f
    
    // Vary active peaking lines based on the manual focus dial to feel interactive
    val ratio = focusVal / 100f
    
    when (sceneName) {
        "Leica headquarters" -> {
            drawLine(
                color = LeicaRed.copy(alpha = peakAlpha),
                start = Offset(0f, size.height * (0.2f + ratio * 0.25f)),
                end = Offset(size.width, size.height * (0.45f + ratio * 0.1f)),
                strokeWidth = fStroke
            )
        }
        "Munich rain-streaked alley" -> {
            drawCircle(
                color = LeicaRed.copy(alpha = peakAlpha),
                radius = 80f * ratio,
                center = Offset(size.width * 0.65f, size.height * 0.65f),
                style = Stroke(fStroke)
            )
        }
        "Paris boulevard café" -> {
            drawRect(
                color = LeicaRed.copy(alpha = peakAlpha),
                topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                size = Size(size.width * 0.8f, size.height * 0.6f),
                style = Stroke(fStroke)
            )
        }
        "Studio portrait session" -> {
            drawCircle(
                color = LeicaRed.copy(alpha = peakAlpha),
                radius = size.width * (0.15f + ratio * 0.15f),
                center = Offset(size.width * 0.5f, size.height * 0.4f),
                style = Stroke(fStroke)
            )
        }
        "Swiss Alps dawn" -> {
            drawLine(
                color = LeicaRed.copy(alpha = peakAlpha),
                start = Offset(size.width * 0.1f, size.height * (0.7f - ratio * 0.2f)),
                end = Offset(size.width * 0.35f, size.height * 0.45f),
                strokeWidth = fStroke
            )
        }
    }
}

private fun DrawScope.drawZebraStripesSim() {
    val stripeWidth = 5f
    val stripeGap = 15f
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(x, 0f),
            end = Offset(x + size.height, size.height),
            strokeWidth = stripeWidth
        )
        x += stripeGap
    }
}

// Helper color tints multipliers
private fun getKelvinTint(kelvin: Int): Color {
    return when {
        kelvin < 4000 -> Color(0xFFFFB300) // Deep warm candle amber
        kelvin < 5000 -> Color(0xFFFFE082) // Warm studio light
        kelvin < 6000 -> Color(0xFFFFFFFF) // Natural white
        kelvin < 7500 -> Color(0xFFE3F2FD) // Cloudy neon cold sky
        else -> Color(0xFF90CAF9) // Artic frost glacier blue
    }
}

private fun Color.multiply(other: Color): Color {
    return Color(
        red = (this.red * other.red).coerceIn(0f, 1f),
        green = (this.green * other.green).coerceIn(0f, 1f),
        blue = (this.blue * other.blue).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

private fun Color.scaleColor(factor: Float): Color {
    return Color(
        red = (this.red * factor).coerceIn(0f, 1f),
        green = (this.green * factor).coerceIn(0f, 1f),
        blue = (this.blue * factor).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

@Composable
fun ViewfinderGridOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeAlpha = 0.2f
            val sColor = Color.White

            // Rule of thirds lines
            drawLine(
                color = sColor,
                start = Offset(size.width / 3f, 0f),
                end = Offset(size.width / 3f, size.height),
                strokeWidth = 1f,
                alpha = strokeAlpha
            )
            drawLine(
                color = sColor,
                start = Offset(size.width * 2 / 3f, 0f),
                end = Offset(size.width * 2 / 3f, size.height),
                strokeWidth = 1f,
                alpha = strokeAlpha
            )
            drawLine(
                color = sColor,
                start = Offset(0f, size.height / 3f),
                end = Offset(size.width, size.height / 3f),
                strokeWidth = 1f,
                alpha = strokeAlpha
            )
            drawLine(
                color = sColor,
                start = Offset(0f, size.height * 2 / 3f),
                end = Offset(size.width, size.height * 2 / 3f),
                strokeWidth = 1f,
                alpha = strokeAlpha
            )
        }
    }
}

@Composable
fun LiveHistogramOverlay(state: CameraUiState) {
    // Draw real-time moving curves based on parameters (EV, WB, Looks)
    Box(
        modifier = Modifier
            .size(width = 90.dp, height = 48.dp)
            .padding(top = 10.dp, end = 10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val evShift = state.exposureValue * 15f
            val lookMultiplier = when(state.currentLook) {
                LeicaLook.BW_CLASSIC -> 0f
                LeicaLook.BW_HIGH_CONTRAST -> 0f
                else -> 1f
            }

            // Draw Red channels
            val rPath = Path().apply {
                moveTo(0f, size.height)
                cubicTo(
                    size.width * 0.2f + evShift, size.height * 0.3f,
                    size.width * 0.6f + evShift, size.height * 0.9f,
                    size.width, size.height
                )
            }
            if (lookMultiplier > 0) drawPath(rPath, color = Color.Red.copy(alpha = 0.25f))

            // Draw Green channels
            val gPath = Path().apply {
                moveTo(0f, size.height)
                cubicTo(
                    size.width * 0.3f + evShift, size.height * 0.5f,
                    size.width * 0.7f + evShift, size.height * 0.4f,
                    size.width, size.height
                )
            }
            if (lookMultiplier > 0) drawPath(gPath, color = Color.Green.copy(alpha = 0.25f))

            // Draw Luminosity white path
            val yPath = Path().apply {
                moveTo(0f, size.height)
                cubicTo(
                    size.width * 0.4f + evShift, size.height * 0.2f,
                    size.width * 0.8f + evShift, size.height * 0.6f,
                    size.width, size.height
                )
            }
            drawPath(yPath, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
fun LevelMeterOverlay(roll: Float, pitch: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Thin background horizontal baseline line stretching across left-4 right-4 (margins 16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.15f))
        )

        // Balanced state calculation
        val isBalanced = Math.abs(roll) < 0.4f && Math.abs(pitch) < 0.3f
        val levelColor = if (isBalanced) QualitySuccess else Color.White.copy(alpha = 0.7f)

        // Dynamic level indicator bar with custom rotation and glow shadow
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(3.dp)
                .rotate(roll * 1.5f) // rotate slightly accentuated for tactile feedback
                .background(
                    color = levelColor,
                    shape = RoundedCornerShape(1.dp)
                )
                .then(
                    if (isBalanced) {
                        Modifier.drawBehind {
                            drawCircle(
                                color = Color(0x3300C853),
                                radius = 24f,
                                center = center
                            )
                        }
                    } else Modifier
                )
        )
    }
}

@Composable
fun AspectCropOverlay(aspect: String) {
    if (aspect == "4:3") return // Default, no overlay crops
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sHeight = size.height
        val sWidth = size.width
        
        when (aspect) {
            "21:9" -> {
                // Draw cinematic widescreen masking band top/bottom
                val maskHeight = sHeight * 0.18f
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = Size(sWidth, maskHeight))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, sHeight - maskHeight), size = Size(sWidth, maskHeight))
            }
            "1:1" -> {
                // Square mask cropping left/right borders
                val cropWidth = (sWidth - sHeight) / 2f
                if (cropWidth > 0) {
                    drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = Size(cropWidth, sHeight))
                    drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(sWidth - cropWidth, 0f), size = Size(cropWidth, sHeight))
                }
            }
            "16:9" -> {
                // Slight cropping masking bands
                val maskHeight = sHeight * 0.08f
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = Size(sWidth, maskHeight))
                drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, sHeight - maskHeight), size = Size(sWidth, maskHeight))
            }
        }
    }
}

@Composable
fun FocusPeakingStatusBadge(focusValue: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(LeicaRed.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "PEAKING MF: ${focusValue.toInt()}",
                color = Color.White,
                fontSize = 8.sp,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VideoHUDOverlay(state: CameraUiState) {
    // Red blinking rec dot + duration counter
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Infinite blink anim
                val infiniteTransition = rememberInfiniteTransition()
                val blinkAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(blinkAlpha)
                        .background(LeicaRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                
                val minutes = state.videoDurationSec / 60
                val seconds = state.videoDurationSec % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Real-time audio peaking lines moving
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIC L ",
                    color = LightTextSecondary,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
                Canvas(modifier = Modifier.size(width = 60.dp, height = 4.dp)) {
                    val progressWidth = size.width * state.audioLevelL
                    drawRect(color = Color.White.copy(alpha = 0.2f))
                    drawRect(
                        color = if (state.audioLevelL > 0.8f) LeicaRed else QualitySuccess,
                        size = Size(progressWidth, size.height)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "R ",
                    color = LightTextSecondary,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace
                )
                Canvas(modifier = Modifier.size(width = 60.dp, height = 4.dp)) {
                    val progressWidth = size.width * state.audioLevelR
                    drawRect(color = Color.White.copy(alpha = 0.2f))
                    drawRect(
                        color = if (state.audioLevelR > 0.8f) LeicaRed else QualitySuccess,
                        size = Size(progressWidth, size.height)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveInformationTelemetryBar(
    state: CameraUiState,
    viewModel: CameraViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fStop = when(state.currentMode) {
            CameraMode.PORTRAIT -> "F1.2 ASPH"
            CameraMode.NIGHT -> "F1.4 SUMMILUX"
            else -> "F1.7 SUMMICRON"
        }

        // 1. ISO Engraving
        TelemetryTick(
            label = "ISO",
            value = "${state.iso}",
            isActive = state.isProDialActive == "ISO",
            onClick = { viewModel.toggleProDial("ISO") }
        )

        // 2. Shutter Engraving
        TelemetryTick(
            label = "S",
            value = state.shutterSpeed,
            isActive = state.isProDialActive == "SHUTTER",
            onClick = { viewModel.toggleProDial("SHUTTER") }
        )

        // 3. F-Lens aperture value label
        Text(
            text = fStop,
            color = LeicaRed,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // 4. EV rating
        val evStr = if (state.exposureValue >= 0f) "+${String.format("%.1f", state.exposureValue)}" else String.format("%.1f", state.exposureValue)
        TelemetryTick(
            label = "EV",
            value = evStr,
            isActive = state.isProDialActive == "EV",
            onClick = { viewModel.toggleProDial("EV") }
        )

        // 5. White balance temperature
        TelemetryTick(
            label = "WB",
            value = "${state.whiteBalance}K",
            isActive = state.isProDialActive == "WB",
            onClick = { viewModel.toggleProDial("WB") }
        )
    }
}

@Composable
fun TelemetryTick(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = LightTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            color = if (isActive) LeicaRed else Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProModeDialConsole(
    state: CameraUiState,
    viewModel: CameraViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(DarkSurface)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val activeDial = state.isProDialActive ?: "ISO"

        when (activeDial) {
            "ISO" -> {
                val values = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
                WheelSlider(
                    options = values.map { it.toString() },
                    currentIndex = values.indexOf(state.iso).coerceAtLeast(0),
                    onSelect = { viewModel.setIso(values[it]) },
                    title = "ISO SENSITIVITY"
                )
            }
            "SHUTTER" -> {
                val values = listOf("1/8000", "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1s", "5s", "15s", "30s")
                WheelSlider(
                    options = values,
                    currentIndex = values.indexOf(state.shutterSpeed).coerceAtLeast(0),
                    onSelect = { viewModel.setShutterSpeed(values[it]) },
                    title = "PHYSICAL SHUTTER"
                )
            }
            "EV" -> {
                val values = listOf(-3.0f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f)
                WheelSlider(
                    options = values.map { String.format("%.1f", it) },
                    currentIndex = values.indexOf(state.exposureValue).coerceAtLeast(0),
                    onSelect = { viewModel.setExposureValue(values[it]) },
                    title = "EXPOSURE VALUE"
                )
            }
            "WB" -> {
                val values = listOf(2000, 3000, 4000, 5000, 5600, 6500, 8000, 10000)
                WheelSlider(
                    options = values.map { "${it}K" },
                    currentIndex = values.indexOf(state.whiteBalance).coerceAtLeast(0),
                    onSelect = { viewModel.setWhiteBalance(values[it]) },
                    title = "COLOR TEMPERATURE"
                )
            }
        }
    }
}

@Composable
fun WheelSlider(
    options: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    title: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = LightTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        // Horizontal scroll selectors imitating knurled dials
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(options.size) { index ->
                val opt = options[index]
                val isSelected = index == currentIndex

                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Knurled hardware tick marks
                        Box(
                            modifier = Modifier
                                .size(width = 1.5.dp, height = 8.dp)
                                .background(if (isSelected) LeicaRed else Color.White.copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = opt,
                            color = if (isSelected) LeicaRed else Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeicaLooksDrawer(
    state: CameraUiState,
    onLookSelected: (LeicaLook) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Text(
            text = "LEICA LOOKS PRESETS",
            color = LeicaRed,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(LeicaLook.values()) { look ->
                val isActive = state.currentLook == look
                
                Card(
                    onClick = { onLookSelected(look) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) LeicaRed.copy(alpha = 0.15f) else DarkCard
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isActive) LeicaRed else Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .width(136.dp)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = look.displayName,
                            color = if (isActive) LeicaRed else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                        Text(
                            text = look.desc,
                            color = LightTextSecondary,
                            fontSize = 7.sp,
                            maxLines = 2,
                            lineHeight = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraModeCarousel(
    selectedMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(CameraMode.values()) { mode ->
                val isSelected = mode == selectedMode
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // No distracting android default ripple overlays
                            onClick = { onModeSelected(mode) }
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name,
                        color = if (isSelected) Color.White else LightTextSecondary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 1.0.sp,
                        fontSize = if (isSelected) 11.sp else 10.sp
                    )
                    
                    if (isSelected) {
                        // Tiny physical Red Dot underneath selected mode
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 10.dp)
                                .size(3.dp)
                                .background(LeicaRed, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShutterControlPlatform(
    state: CameraUiState,
    viewModel: CameraViewModel,
    galleryCount: Int,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onLooksClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(DarkBackground)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left item: Miniature Photoroll Gallery Trigger
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clickable(onClick = onGalleryClick),
            contentAlignment = Alignment.Center
        ) {
            if (galleryCount > 0) {
                // Return a visual grid badge depicting photocount to feel real
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = "Photos database list",
                        tint = LeicaRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "$galleryCount",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoSizeSelectActual,
                    contentDescription = "Empty Roll",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Center item: Outstanding Leica Q3-inspired spring mechanical shutter release button with concentric matte outer casing
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(92.dp)
        ) {
            // Absolute thin matte outer ring ring casing
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            )

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "Tactile Shutter Scale"
            )

            val shutterStroke = 3.dp

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(buttonScale)
                    .border(shutterStroke, Color.White, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (state.currentMode == CameraMode.VIDEO) LeicaRed else Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val pressJob = scope.launch { delay(120) }
                                try {
                                    awaitRelease()
                                } finally {
                                    pressJob.cancel()
                                    onShutterClick()
                                }
                            }
                        )
                    }
                    .testTag("shutter_button")
            )
        }

        // Right item: Quick Leica Looks preset drawer launcher (Monochrome metal crown badge)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (state.isLooksDrawerOpen) LeicaRed.copy(alpha = 0.2f) else DarkCard)
                .border(
                    1.dp,
                    if (state.isLooksDrawerOpen) LeicaRed else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .clickable(onClick = onLooksClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.FilterBAndW,
                    contentDescription = "Looks Selection",
                    tint = if (state.isLooksDrawerOpen) LeicaRed else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "LOOKS",
                    color = if (state.isLooksDrawerOpen) LeicaRed else Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ---------------------- Sub overlays drawers screens ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeicaSettingsTray(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    // Elegant bottom sheet overlay holding core preferences
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "SYSTEM CONTROLS",
                color = LeicaRed,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Settings 1: Grid options
            SettingsRow(
                label = "VIEWFINDER RULE GRID",
                desc = "Overlay composition guidelines (thirds rule)"
            ) {
                Switch(
                    checked = state.showGrid,
                    onCheckedChange = { viewModel.toggleGrid() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LeicaRed)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // Settings 2: Horizontal level meter
            SettingsRow(
                label = "SPIRIT LEVEL METER",
                desc = "Precision horizon guidelines indicator"
            ) {
                Switch(
                    checked = state.showLevelMeter,
                    onCheckedChange = { viewModel.toggleLevelMeter() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LeicaRed)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // Settings 3: Aspect Ratios
            SettingsRow(
                label = "VIEWFINDER ASPECT RATIO",
                desc = "Mask frame format cropping layouts"
            ) {
                val aspects = listOf("4:3", "16:9", "21:9", "1:1")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    aspects.forEach { asp ->
                        val isSel = state.selectedAspect == asp
                        Button(
                            onClick = { viewModel.setAspectRatio(asp) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) LeicaRed else DarkCard,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(text = asp, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // Settings 4: Video modes
            SettingsRow(
                label = "CINEMATIC FRAMERATES",
                desc = "Video mode default resolution preferences"
            ) {
                val resList = listOf("4K60", "4K30", "1080p")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    resList.forEach { res ->
                        val isSel = state.selectedVideoRes == res
                        Button(
                            onClick = { viewModel.setVideoResolution(res) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) LeicaRed else DarkCard,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(text = res, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // Settings 5: Source switcher (Camera simulation vs real hardware)
            SettingsRow(
                label = "VIEWFINDER SIMULATION",
                desc = "Disable to request active hardware lens feeds"
            ) {
                Switch(
                    checked = !state.useRealCameraIfPossible,
                    onCheckedChange = { viewModel.toggleCameraSource() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LeicaRed)
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    label: String,
    desc: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = LightTextSecondary,
                fontSize = 8.sp,
                lineHeight = 10.sp
            )
        }
        action()
    }
}

@Composable
fun LeicaExifGalleryOverlay(
    capturedPhotos: List<CapturedPhoto>,
    onClose: () -> Unit,
    onDelete: (CapturedPhoto) -> Unit,
    onClearAll: () -> Unit
) {
    // Beautiful full screen layout featuring high-contrast photoroll captures
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .clickable(enabled = false, onClick = {}) // block lower layers click
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }

                Text(
                    text = "AITOX LEICA PHOTOROLL",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )

                IconButton(
                    onClick = { if (capturedPhotos.isNotEmpty()) onClearAll() },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = LeicaRed
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.DeleteForever, contentDescription = "Clear collection")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (capturedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Empty",
                            tint = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NO SHOTS CAPTURED YET",
                            color = LightTextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Tap the white mechanical shutter to capture simulated or active frames.",
                            color = LightTextSecondary.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                // High fidelity scrolling grid containing EXIF detail cards
                Box(modifier = Modifier.weight(1f)) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        capturedPhotos.forEach { photo ->
                            ExifPhotoCard(photo = photo, onDelete = { onDelete(photo) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExifPhotoCard(
    photo: CapturedPhoto,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Simulated thumbnail canvas displaying colors depending on look
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black)
                    .drawBehind {
                        // Recreate some soft vector design in card thumbnail
                        drawRect(Color(0xFF131722))
                        
                        // Circle backdrop reflecting looks
                        val paintColor = when(photo.leicaLook) {
                            "LEICA M-MONO CLASSIC", "LEICA M-MONO DRAMATIC" -> Color.Gray
                            "LEICA SEPIA" -> Color(0xFF8D6E63)
                            "LEICA VIBRANT" -> Color(0xFFEF5350)
                            else -> Color(0xFF42A5F5)
                        }
                        
                        drawCircle(
                            color = paintColor.copy(alpha = 0.35f),
                            radius = 120f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                        // Tiny grid representational details
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (photo.isVideo) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = "Video marker",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = photo.sceneName.uppercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.0.sp
                    )
                    Text(
                        text = photo.leicaLook,
                        color = LeicaRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Exif metal tag markings plate
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sdf = SimpleDateFormat("yyyy.MM.dd | HH:mm", Locale.getDefault())
                    Text(
                        text = "EXIF telemetry ${sdf.format(Date(photo.timestamp))}",
                        color = LightTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Discard photo",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Five parameters physical dials list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ExifLabelItem(label = "LENS", value = photo.focalLength)
                    ExifLabelItem(label = "SHUTTER", value = photo.shutterSpeed)
                    ExifLabelItem(label = "ISO", value = "${photo.iso}")
                    ExifLabelItem(label = "BAL", value = "${photo.whiteBalance}K")
                    ExifLabelItem(label = "EV", value = "${if (photo.exposureValue >= 0) "+" else ""}${photo.exposureValue}")
                }

                if (!photo.aiFeaturesActive.contains("NONE")) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "COMPUTATIONAL ENVELOPE: ${photo.aiFeaturesActive.uppercase()}",
                        color = QualitySuccess,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExifLabelItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = LightTextSecondary, fontSize = 7.sp, letterSpacing = 0.5.sp)
        Text(text = value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------- Haptics / Animations tweaks ----------------------

fun twinPulse120(): DurationBasedAnimationSpec<Float> {
    return tween(durationMillis = 120, easing = LinearEasing)
}

fun twinPulse200OrLess(): DurationBasedAnimationSpec<Float> {
    return tween(durationMillis = 180, easing = LinearOutSlowInEasing)
}

// Colors helper references
fun ColorsTextActive(): Color = Color.White
