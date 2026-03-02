package com.crcleapp.crcle.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: () -> Unit = {}
) {
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale.snapTo(1f)
            offset.snapTo(Offset.Zero)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var isPinching = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        
                        if (activePointers.size >= 2) {
                            isPinching = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            
                            val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                            
                            scope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(offset.value + panChange)
                            }
                            
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        } else if (isPinching) {
                            isPinching = false
                            // Animate both scale and offset simultaneously in parallel coroutines
                            // with the same spring spec for a unified "one step" animation.
                            val springSpec = spring<Float>()
                            val offsetSpringSpec = spring<Offset>()
                            
                            scope.launch {
                                launch {
                                    scale.animateTo(1f, springSpec)
                                }
                                launch {
                                    offset.animateTo(Offset.Zero, offsetSpringSpec)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offset.value.x,
                    translationY = offset.value.y
                ),
            contentScale = contentScale
        )
    }
}
