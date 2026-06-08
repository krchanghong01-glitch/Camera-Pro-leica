package com.example.ui.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CapturedPhoto
import com.example.data.PhotoDatabase
import com.example.data.PhotoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

// Camera modes available
enum class CameraMode {
    PRO, PHOTO, PORTRAIT, NIGHT, VIDEO, PANORAMA, DOCUMENT, MACRO
}

// Leica Looks
enum class LeicaLook(val displayName: String, val desc: String) {
    AUTHENTIC("LEICA AUTHENTIC", "Natural colors, deep blacks, high shadow contrast"),
    VIBRANT("LEICA VIBRANT", "Richer colors, slightly warmer highlights, punchy look"),
    NATURAL("LEICA NATURAL", "Balanced contrast, neutral rendering, accurate colors"),
    BW_CLASSIC("LEICA M-MONO CLASSIC", "Film inspired classic silver-halide monochrome"),
    BW_HIGH_CONTRAST("LEICA M-MONO DRAMATIC", "Extreme deep blacks, bright highlights, heavy grit"),
    SEPIA("LEICA SEPIA", "Vintage documentary warm bronze-toned styling")
}

// Scene Simulators
data class SimulatedScene(
    val name: String,
    val description: String,
    val baseWarmth: Int, // temperature offset
    val baseContrast: Float,
    val initialAiScene: String,
    val imageUrl: String = "" // Placeholder fallback
)

val SCENE_PRESETS = listOf(
    SimulatedScene("Leica headquarters", "Fleshed in minimalist German industrial curves with satin stones", 5400, 1.15f, "STREET / ARCHITECTURE"),
    SimulatedScene("Munich rain-streaked alley", "Golden cobblestones reflecting wet luxury vehicle headlamps", 5200, 1.25f, "REFLECTIONS / NIGHT"),
    SimulatedScene("Paris boulevard café", "Soft golden hour amber, light café steam, and warm details", 5800, 1.10f, "CANDID / HUMANITY"),
    SimulatedScene("Studio portrait session", "Chiaroscuro modeling, sharp eyelashes, creamy buttery bokeh", 5500, 1.05f, "PORTRAIT"),
    SimulatedScene("Swiss Alps dawn", "Cool mist in cracks, soft rose-toned glacier sky overhead", 4800, 1.30f, "LANDSCAPE / RAW")
)

data class LiveHistogramData(
    val r: FloatArray,
    val g: FloatArray,
    val b: FloatArray,
    val y: FloatArray
) {
    override fun equals(other: Any?): Boolean = true
    override fun hashCode(): Int = 0
}

data class CameraUiState(
    // Mode parameters
    val currentMode: CameraMode = CameraMode.PHOTO,
    val currentLook: LeicaLook = LeicaLook.AUTHENTIC,
    val currentFocalLength: String = "35mm", // "28mm", "35mm", "50mm", "90mm"
    
    // Pro parameters
    val iso: Int = 100, // 50 to 6400
    val shutterSpeed: String = "1/250", // 1/8000s up to 30s
    val whiteBalance: Int = 5600, // Kelvin (2000K to 10000K)
    val exposureValue: Float = 0.0f, // -3.0f to +3.0f
    val manualFocus: Float = 100.0f, // 0.0 to 100.0 (100.0 is Infinity Autofocus)
    val isAutofocus: Boolean = true,
    
    // Viewfinder Overlays
    val showGrid: Boolean = false,
    val showLevelMeter: Boolean = true,
    val showZebraPattern: Boolean = false,
    val showHistogram: Boolean = true,
    val liveSceneIndex: Int = 0,
    val levelRollAngle: Float = 0.0f, // simulated live gyro
    val levelPitchAngle: Float = 0.0f,
    
    // AI Parameters
    val isAiSceneDetectionEnabled: Boolean = true,
    val isAiPortraitLightingEnabled: Boolean = false,
    val isAiCompositionGuideEnabled: Boolean = false,
    val isAiReflectionRemovalEnabled: Boolean = false,
    val aiSceneLabel: String? = "STREET",
    
    // Media Capture/Video
    val isRecordingVideo: Boolean = false,
    val videoDurationSec: Int = 0,
    val audioLevelL: Float = 0.15f,
    val audioLevelR: Float = 0.12f,
    val selectedVideoRes: String = "4K60", // "4K60", "4K30", "1080p60", "1080p30"
    val selectedAspect: String = "4:3", // "16:9", "21:9", "1:1", "4:3"
    
    // Simulation / Real Engine Toggle
    val useRealCameraIfPossible: Boolean = false,
    val isShutterSnapping: Boolean = false,
    val hasCameraPermission: Boolean = false,
    
    // UI Helpers
    val isLooksDrawerOpen: Boolean = false,
    val isProDialActive: String? = null, // "ISO", "SHUTTER", "WB", "EV", "FOCUS", or null
    val isSettingsOpen: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: PhotoRepository
    
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // Captured database list
    val capturedPhotos: StateFlow<List<CapturedPhoto>>

    private var videoTimerJob: Job? = null
    private var levelSimulationJob: Job? = null

    init {
        val database = PhotoDatabase.getDatabase(application)
        repository = PhotoRepository(database.dao)
        capturedPhotos = repository.allPhotos.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Simulate gyro movement for level meter and audio levels in background
        startLiveSimulators()
    }

    private fun startLiveSimulators() {
        levelSimulationJob = viewModelScope.launch {
            var tick = 0.0f
            while (true) {
                delay(120)
                tick += 0.05f
                
                // Slow organic wandering of camera level
                val baseRoll = (Math.sin(tick.toDouble() * 0.4) * 1.8).toFloat()
                val basePitch = (Math.cos(tick.toDouble() * 0.3) * 0.9).toFloat()
                
                // Snap to zero sometimes if they hold steady
                val steadyRoll = if (Math.abs(baseRoll) < 0.4f) 0.0f else baseRoll
                val steadyPitch = if (Math.abs(basePitch) < 0.3f) 0.0f else basePitch

                _uiState.update { state ->
                    var audioL = state.audioLevelL
                    var audioR = state.audioLevelR
                    if (state.isRecordingVideo) {
                        audioL = (0.2f + Random.nextFloat() * 0.5f).coerceIn(0.0f, 1.0f)
                        audioR = (audioL + (Random.nextFloat() * 0.2f - 0.1f)).coerceIn(0.0f, 1.0f)
                    }
                    state.copy(
                        levelRollAngle = steadyRoll,
                        levelPitchAngle = steadyPitch,
                        audioLevelL = audioL,
                        audioLevelR = audioR
                    )
                }
            }
        }
    }

    fun setCameraPermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted, useRealCameraIfPossible = granted) }
    }

    fun toggleCameraSource() {
        _uiState.update { state ->
            val target = !state.useRealCameraIfPossible
            if (target && !state.hasCameraPermission) {
                // Keep simulation
                state
            } else {
                state.copy(useRealCameraIfPossible = target)
            }
        }
    }

    fun selectMode(mode: CameraMode) {
        _uiState.update { state ->
            // Stop recording logic if we leave video mode
            if (state.isRecordingVideo) {
                stopVideoRecordingInternal()
            }
            
            // Adjust exposure/parameters automatically based on scene
            val defaultIso = when(mode) {
                CameraMode.NIGHT -> 1600
                CameraMode.PORTRAIT -> 200
                else -> 100
            }
            val defaultShutter = when(mode) {
                CameraMode.NIGHT -> "1/8"
                CameraMode.PORTRAIT -> "1/125"
                CameraMode.VIDEO -> "1/60"
                else -> "1/250"
            }
            
            state.copy(
                currentMode = mode,
                iso = defaultIso,
                shutterSpeed = defaultShutter,
                isProDialActive = null
            )
        }
    }

    fun selectLook(look: LeicaLook) {
        _uiState.update { it.copy(currentLook = look) }
    }

    fun selectFocalLength(focal: String) {
        _uiState.update { it.copy(currentFocalLength = focal) }
    }

    fun setIso(iso: Int) {
        _uiState.update { it.copy(iso = iso) }
    }

    fun setShutterSpeed(shutter: String) {
        _uiState.update { it.copy(shutterSpeed = shutter) }
    }

    fun setWhiteBalance(wb: Int) {
        _uiState.update { it.copy(whiteBalance = wb) }
    }

    fun setExposureValue(ev: Float) {
        _uiState.update { it.copy(exposureValue = ev) }
    }

    fun setManualFocus(value: Float) {
        _uiState.update { 
            it.copy(
                manualFocus = value,
                isAutofocus = value >= 98.0f // Inf is AF
            )
        }
    }

    fun setAutofocus() {
        _uiState.update { it.copy(isAutofocus = true, manualFocus = 100.0f) }
    }

    fun toggleProDial(dialName: String) {
        _uiState.update { state ->
            val nextDial = if (state.isProDialActive == dialName) null else dialName
            state.copy(isProDialActive = nextDial)
        }
    }

    fun closeProDial() {
        _uiState.update { it.copy(isProDialActive = null) }
    }

    fun toggleZebraPattern() {
        _uiState.update { it.copy(showZebraPattern = !it.showZebraPattern) }
    }

    fun toggleLevelMeter() {
        _uiState.update { it.copy(showLevelMeter = !it.showLevelMeter) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleHistogram() {
        _uiState.update { it.copy(showHistogram = !it.showHistogram) }
    }

    fun selectScene(index: Int) {
        val nextScene = SCENE_PRESETS[index]
        _uiState.update { 
            it.copy(
                liveSceneIndex = index,
                aiSceneLabel = if (it.isAiSceneDetectionEnabled) nextScene.initialAiScene else null
            )
        }
    }

    fun toggleAiSceneDetection() {
        _uiState.update { state ->
            val nextEnabled = !state.isAiSceneDetectionEnabled
            state.copy(
                isAiSceneDetectionEnabled = nextEnabled,
                aiSceneLabel = if (nextEnabled) SCENE_PRESETS[state.liveSceneIndex].initialAiScene else null
            )
        }
    }

    fun toggleAiPortraitLighting() {
        _uiState.update { it.copy(isAiPortraitLightingEnabled = !it.isAiPortraitLightingEnabled) }
    }

    fun toggleAiCompositionGuide() {
        _uiState.update { it.copy(isAiCompositionGuideEnabled = !it.isAiCompositionGuideEnabled) }
    }

    fun toggleAiReflectionRemoval() {
        _uiState.update { it.copy(isAiReflectionRemovalEnabled = !it.isAiReflectionRemovalEnabled) }
    }

    fun setVideoResolution(res: String) {
        _uiState.update { it.copy(selectedVideoRes = res) }
    }

    fun setAspectRatio(aspect: String) {
        _uiState.update { it.copy(selectedAspect = aspect) }
    }

    fun toggleLooksDrawer() {
        _uiState.update { it.copy(isLooksDrawerOpen = !it.isLooksDrawerOpen) }
    }

    fun setLooksDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isLooksDrawerOpen = open) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun setSettingsOpen(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    // Capture Trigger
    fun triggerShutter() {
        val state = _uiState.value
        if (state.currentMode == CameraMode.VIDEO) {
            toggleVideoRecording()
            return
        }

        if (state.isShutterSnapping) return

        viewModelScope.launch {
            // Trigger physical visual curtains effect
            _uiState.update { it.copy(isShutterSnapping = true) }
            
            // Build photo capture metadata details
            val activeScene = SCENE_PRESETS[state.liveSceneIndex]
            val actualSceneName = if (state.useRealCameraIfPossible && state.hasCameraPermission) "Live Viewfinder Capture" else activeScene.name
            
            val activeAiList = mutableListOf<String>()
            if (state.isAiSceneDetectionEnabled) activeAiList.add("AI Scene: ${activeScene.initialAiScene}")
            if (state.isAiPortraitLightingEnabled) activeAiList.add("AI Portrait Light")
            if (state.isAiCompositionGuideEnabled) activeAiList.add("AI Rule Guide")
            if (state.isAiReflectionRemovalEnabled) activeAiList.add("AI Glass Erase")
            
            val photo = CapturedPhoto(
                sceneName = actualSceneName,
                cameraMode = state.currentMode.name,
                leicaLook = state.currentLook.displayName,
                iso = state.iso,
                shutterSpeed = state.shutterSpeed,
                whiteBalance = state.whiteBalance,
                exposureValue = state.exposureValue,
                focalLength = state.currentFocalLength,
                manualFocusValue = state.manualFocus.toInt(),
                aiSceneDetected = if (state.isAiSceneDetectionEnabled) activeScene.initialAiScene else null,
                aiFeaturesActive = activeAiList.joinToString(", ").ifEmpty { "NONE" }
            )

            // Save in database
            repository.insertPhoto(photo)
            
            // Shutter speed physical shutter duration feedback
            val delayMs = when {
                state.shutterSpeed.startsWith("1/") -> 120L // Fast tactile response
                state.shutterSpeed.endsWith("s") -> {
                    // Long exposure simulation
                    val sec = state.shutterSpeed.removeSuffix("s").toLongOrNull() ?: 1L
                    (sec * 1000L).coerceAtMost(3000L) // limit UI locking up to 3 sec max
                }
                else -> 120L
            }
            delay(delayMs)
            
            _uiState.update { it.copy(isShutterSnapping = false) }
        }
    }

    private fun toggleVideoRecording() {
        val isRecording = _uiState.value.isRecordingVideo
        if (isRecording) {
            stopVideoRecordingInternal()
        } else {
            startVideoRecordingInternal()
        }
    }

    private fun startVideoRecordingInternal() {
        _uiState.update { it.copy(isRecordingVideo = true, videoDurationSec = 0) }
        videoTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(videoDurationSec = it.videoDurationSec + 1) }
            }
        }
    }

    private fun stopVideoRecordingInternal() {
        val recordDurationSec = _uiState.value.videoDurationSec
        val state = _uiState.value
        videoTimerJob?.cancel()
        videoTimerJob = null

        viewModelScope.launch {
            // Save recording as record in our database
            val activeScene = SCENE_PRESETS[_uiState.value.liveSceneIndex]
            val actualSceneName = if (state.useRealCameraIfPossible && state.hasCameraPermission) "Live Viewfinder Capture" else activeScene.name
            val activeAiList = mutableListOf<String>()
            if (state.isAiSceneDetectionEnabled) activeAiList.add("AI Scene Det.")
            
            val videoRecord = CapturedPhoto(
                sceneName = actualSceneName,
                cameraMode = CameraMode.VIDEO.name,
                leicaLook = state.currentLook.displayName,
                iso = state.iso,
                shutterSpeed = state.shutterSpeed,
                whiteBalance = state.whiteBalance,
                exposureValue = state.exposureValue,
                focalLength = state.currentFocalLength,
                manualFocusValue = state.manualFocus.toInt(),
                aiSceneDetected = if (state.isAiSceneDetectionEnabled) activeScene.initialAiScene else null,
                aiFeaturesActive = activeAiList.joinToString(", ").ifEmpty { "NONE" },
                isVideo = true,
                videoDurationMs = recordDurationSec * 1000L
            )
            repository.insertPhoto(videoRecord)
            
            _uiState.update { it.copy(isRecordingVideo = false, videoDurationSec = 0) }
        }
    }

    fun deletePhoto(photo: CapturedPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
        }
    }

    fun clearAllPhotos() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        videoTimerJob?.cancel()
        levelSimulationJob?.cancel()
        super.onCleared()
    }
}
