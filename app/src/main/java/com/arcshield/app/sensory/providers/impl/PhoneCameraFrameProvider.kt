package com.arcshield.app.sensory.providers.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.arcshield.app.sensory.ChannelAvailability
import com.arcshield.app.sensory.SensoryDelta
import com.arcshield.app.sensory.VisualSnapshot
import com.arcshield.app.sensory.providers.VisualFrameProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gen 1 visual frame provider — captures still frames via CameraX rear camera.
 *
 * Bound to [ProcessLifecycleOwner] so no Activity reference is required from
 * the DI-provided singleton. Frame metadata (luminance, motion score) is left
 * at zero for Gen 1; gauge OCR happens downstream via LLM vision API on the
 * saved JPEG path.
 *
 * Gen 2 replacement: MetaGlassesFrameProvider (POV camera via Wearables Device
 * Access Toolkit) — zero changes to VisualChannel required.
 */
class PhoneCameraFrameProvider(
    private val context: Context,
) : VisualFrameProvider {

    private val executor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override suspend fun initialize(): ChannelAvailability {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return ChannelAvailability.PERMISSION_DENIED

        return withContext(Dispatchers.Main) {
            runCatching {
                val provider = ProcessCameraProvider.getInstance(context).get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    ProcessLifecycleOwner.get(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    capture,
                )
                cameraProvider = provider
                imageCapture = capture
                ChannelAvailability.AVAILABLE
            }.getOrElse { ChannelAvailability.UNAVAILABLE }
        }
    }

    override suspend fun captureFrame(label: String): VisualSnapshot? {
        val capture = imageCapture ?: return null
        val dir = context.getExternalFilesDir("frames") ?: context.cacheDir
        val file = File(dir, "${label}_${System.currentTimeMillis()}.jpg")

        return suspendCancellableCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(
                        VisualSnapshot(
                            timestampMs  = System.currentTimeMillis(),
                            framePath    = file.absolutePath,
                            meanLuminance = 0f,
                            motionScore  = 0f,
                            widthPx      = 0,
                            heightPx     = 0,
                            sourceLabel  = "phone_rear",
                        )
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
            cont.invokeOnCancellation { file.delete() }
        }
    }

    override fun significantMotionStream(): Flow<SensoryDelta>? = null

    override suspend fun release() {
        withContext(Dispatchers.Main) {
            cameraProvider?.unbindAll()
        }
        imageCapture = null
        cameraProvider = null
    }
}
