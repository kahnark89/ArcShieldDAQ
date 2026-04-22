package com.arcshield.app.capture.source

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.arcshield.app.data.schema.PovSource
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX-backed [CaptureSource]. Requires [bindToLifecycle] to be called from
 * a Composable or Activity with a live [LifecycleOwner] before [captureFrame]
 * will succeed. The CameraPreview Composable handles this lifecycle binding
 * transparently when embedded in the capture UI.
 */
@Singleton
class PhoneCameraSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptureSource {

    override val povSource: PovSource = PovSource.PHONE_MOUNTED

    private var imageCapture: ImageCapture? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView:    PreviewView,
    ) {
        val future: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                )
                imageCapture = capture
            } catch (t: Throwable) {
                Log.e(TAG, "Camera bind failed", t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override suspend fun captureFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            cont.resume(bytes)
                        } finally {
                            image.close()
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    companion object {
        private const val TAG = "PhoneCameraSource"
    }
}
