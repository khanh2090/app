package com.fitness.companion.presentation.counter

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitness.companion.domain.counter.AngleRepCounter
import com.fitness.companion.domain.model.ExerciseType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors

/**
 * Camera preview that runs ML Kit pose detection on every frame and reports the joint angle used
 * to count reps of [exercise] (null when no body is visible).
 *
 * The camera is bound to the screen's lifecycle, so it closes whenever the app is minimised.
 */
@Composable
fun CameraPoseCounter(
    exercise: ExerciseType,
    onAngle: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnAngle by rememberUpdatedState(onAngle)
    val currentExercise by rememberUpdatedState(exercise)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { frame ->
                detectPose(detector, frame) { pose ->
                    currentOnAngle(pose?.let { repAngle(it, currentExercise) })
                }
            }

            // The front camera lets the user watch the count while exercising.
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                // No usable camera after all: the screen keeps showing "no body detected".
                currentOnAngle(null)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            detector.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun detectPose(detector: PoseDetector, frame: ImageProxy, onResult: (Pose?) -> Unit) {
    val mediaImage = frame.image
    if (mediaImage == null) {
        frame.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, frame.imageInfo.rotationDegrees)
    detector.process(input)
        .addOnSuccessListener { pose -> onResult(pose) }
        .addOnFailureListener { onResult(null) }
        // The frame must be closed before CameraX delivers the next one.
        .addOnCompleteListener { frame.close() }
}

/**
 * Elbow angle for push-ups, hip angle for sit-ups, measured on whichever body side the camera
 * sees better. Returns null when the three joints are not reliably in frame.
 */
private fun repAngle(pose: Pose, exercise: ExerciseType): Double? {
    val sides = if (exercise == ExerciseType.SIT_UP) {
        listOf(
            Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        )
    } else {
        listOf(
            Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        )
    }
    return sides
        .mapNotNull { (first, vertex, last) ->
            val a = pose.getPoseLandmark(first) ?: return@mapNotNull null
            val b = pose.getPoseLandmark(vertex) ?: return@mapNotNull null
            val c = pose.getPoseLandmark(last) ?: return@mapNotNull null
            val confidence = minOf(a.inFrameLikelihood, b.inFrameLikelihood, c.inFrameLikelihood)
            if (confidence < MIN_LANDMARK_CONFIDENCE) return@mapNotNull null
            confidence to AngleRepCounter.angleDegrees(
                a.position.x, a.position.y,
                b.position.x, b.position.y,
                c.position.x, c.position.y,
            )
        }
        .maxByOrNull { it.first }
        ?.second
}

private const val MIN_LANDMARK_CONFIDENCE = 0.5f
