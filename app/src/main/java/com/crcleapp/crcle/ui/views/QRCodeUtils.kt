package com.crcleapp.crcle.ui.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors
import kotlin.math.*
import kotlin.random.Random

/**
 * Generates a standard square QR code styled to look circular.
 * Strictly clips all content within the circular boundary to prevent "jutting out".
 * Uses standard square finders and gapless module drawing.
 */
fun generateQRCode(
    content: String,
    size: Int = 512,
    logo: Bitmap? = null,
    dotColor: Int = Color.WHITE,
    backgroundColor: Int = Color.TRANSPARENT
): Bitmap? {
    return try {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 1
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        
        // Generate standard QR
        val matrix: BitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            hints
        )
        
        val width = matrix.width
        val height = matrix.height
        
        // Fit square QR in center (approx 70% of diameter)
        val qrSize = size * 0.70f
        val offset = (size - qrSize) / 2f
        val moduleSize = qrSize / width
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val paint = Paint().apply {
            isAntiAlias = true
            color = dotColor
            style = Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2f - 6f // Adjusted for thicker stroke

        // Create clipping path to ensure nothing juts out
        val clipPath = Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Draw QR Modules with slight overlap to eliminate hairline gaps
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (matrix[x, y]) {
                    val left = offset + x * moduleSize
                    val top = offset + y * moduleSize
                    // Adding 0.5f to eliminate gaps between adjacent modules
                    canvas.drawRect(left, top, left + moduleSize + 0.5f, top + moduleSize + 0.5f, paint)
                }
            }
        }

        // 2. Fill the rest of the circle with random noise blocks
        val noiseSize = moduleSize
        val random = Random(content.hashCode())
        val gridCells = (size / noiseSize).toInt()
        
        for (y in 0 until gridCells) {
            for (x in 0 until gridCells) {
                val left = x * noiseSize
                val top = y * noiseSize
                val px = left + noiseSize / 2f
                val py = top + noiseSize / 2f
                
                val dist = sqrt((px - centerX).pow(2) + (py - centerY).pow(2))
                
                // If inside circle but outside the QR square
                val isInsideQR = px > offset && px < offset + qrSize && py > offset && py < offset + qrSize
                if (dist < radius && !isInsideQR) {
                    if (random.nextFloat() > 0.6f) { // 40% density for noise
                        canvas.drawRect(left, top, left + noiseSize + 0.5f, top + noiseSize + 0.5f, paint)
                    }
                }
            }
        }

        canvas.restore() // Remove clipping

        // 3. Draw Thick Outer Ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f // Thicker outline
        canvas.drawCircle(centerX, centerY, radius, paint)

        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
fun QRScanner(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    processImageProxy(imageProxy, onQrCodeScanned)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
            previewView
        }
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    onQrCodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.rawValue != null) {
                        onQrCodeScanned(barcode.rawValue!!)
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
