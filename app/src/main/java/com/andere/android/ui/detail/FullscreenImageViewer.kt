package com.andere.android.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.andere.android.domain.model.Post
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun Post.fullImageUrl(): String? = jpegUrl ?: fileUrl ?: sampleUrl ?: previewUrl

private fun isSideways(degrees: Float): Boolean {
    val n = ((degrees % 360f) + 360f) % 360f
    return abs(n - 90f) < 1f || abs(n - 270f) < 1f
}

private fun rotatePan(pan: Offset, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return Offset(x = pan.x * c - pan.y * s, y = pan.x * s + pan.y * c)
}

private fun projectedSwipeDistance(pan: Offset, rotationDeg: Float): Float {
    return -pan.x
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun FullscreenStatusBarEffect(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled, context, view) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled && controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun FullscreenImageViewer(
    posts: List<Post>,
    initialIndex: Int,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        FullscreenStatusBarEffect(enabled = true)
        val postCount = posts.size
        val pagerState = rememberPagerState(initialPage = initialIndex) { postCount }
        val coroutineScope = rememberCoroutineScope()
        var sharedRotation by remember { mutableFloatStateOf(0f) }
        var isZoomed by remember { mutableStateOf(false) }

        val context = LocalContext.current
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                onPageChanged(page)
                val loader = context.imageLoader
                for (ahead in 1..6) {
                    val url = posts.getOrNull(page + ahead)?.fullImageUrl() ?: break
                    loader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Rotate the entire pager so that page-switch animation direction follows rotation.
            // Swap layout dimensions when sideways so content fits the screen after rotation.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        if (isSideways(sharedRotation)) {
                            val swapped = Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                            val placeable = measurable.measure(swapped)
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(
                                    (constraints.maxWidth - placeable.width) / 2,
                                    (constraints.maxHeight - placeable.height) / 2,
                                )
                            }
                        } else {
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                    }
                    .graphicsLayer { rotationZ = sharedRotation },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = sharedRotation == 0f && !isZoomed,
                    beyondViewportPageCount = 0,
                    key = { posts.getOrNull(it)?.id ?: it },
                ) { page ->
                    val post = posts.getOrNull(page) ?: return@HorizontalPager
                    ZoomableImage(
                        imageUrl = post.fullImageUrl(),
                        imageWidth = post.width,
                        imageHeight = post.height,
                        isCurrentPage = page == pagerState.currentPage,
                        currentRotation = sharedRotation,
                        onTap = onDismiss,
                        onRotationChanged = { newRotation ->
                            sharedRotation = newRotation
                            isZoomed = false
                        },
                        onZoomChanged = { zoomed ->
                            if (page == pagerState.currentPage) isZoomed = zoomed
                        },
                        onSwipeNext = {
                            coroutineScope.launch {
                                val next = pagerState.currentPage + 1
                                if (next < posts.size) pagerState.animateScrollToPage(next)
                            }
                        },
                        onSwipePrev = {
                            coroutineScope.launch {
                                val prev = pagerState.currentPage - 1
                                if (prev >= 0) pagerState.animateScrollToPage(prev)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String?,
    imageWidth: Int,
    imageHeight: Int,
    isCurrentPage: Boolean,
    currentRotation: Float,
    onTap: () -> Unit,
    onRotationChanged: (Float) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var gestureRotation by remember { mutableFloatStateOf(0f) }

    val updatedRotation by rememberUpdatedState(currentRotation)
    val updatedSwipeNext by rememberUpdatedState(onSwipeNext)
    val updatedSwipePrev by rememberUpdatedState(onSwipePrev)

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            gestureRotation = 0f
        }
    }

    LaunchedEffect(currentRotation) {
        scale = 1f
        offset = Offset.Zero
        gestureRotation = 0f
    }

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val swipeThreshold = min(size.width, size.height).toFloat() * 0.22f

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val startRotation = updatedRotation
                    var rawRotation = startRotation
                    var gestureRotAccum = 0f
                    var accumulatedPan = Offset.Zero
                    var swipeTriggered = false

                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val rotChange = event.calculateRotation()
                        val panChange = event.calculatePan()
                        val isMultiTouch = zoomChange != 1f || rotChange != 0f

                        if (isMultiTouch) {
                            scale = (scale * zoomChange).coerceIn(0.5f, 8f)
                            rawRotation += rotChange
                            gestureRotAccum += abs(rotChange)
                            gestureRotation = rawRotation - startRotation
                            offset = Offset(
                                x = offset.x + panChange.x,
                                y = offset.y + panChange.y,
                            )
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.15f) {
                            offset = Offset(
                                x = offset.x + panChange.x,
                                y = offset.y + panChange.y,
                            )
                            event.changes.forEach { it.consume() }
                        } else if (updatedRotation != 0f && !swipeTriggered) {
                            accumulatedPan += panChange
                            val swipeDistance = projectedSwipeDistance(accumulatedPan, updatedRotation)
                            if (swipeDistance > swipeThreshold) {
                                swipeTriggered = true
                                updatedSwipeNext()
                            } else if (swipeDistance < -swipeThreshold) {
                                swipeTriggered = true
                                updatedSwipePrev()
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    gestureRotation = 0f
                    if (gestureRotAccum > 25f) {
                        val snapped = (rawRotation / 90f).roundToInt() * 90f
                        val startSnapped = (startRotation / 90f).roundToInt() * 90f
                        if (snapped != startSnapped) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        onRotationChanged(snapped)
                    } else if (gestureRotAccum > 0f) {
                        onRotationChanged((startRotation / 90f).roundToInt() * 90f)
                    }
                    onZoomChanged(scale > 1.15f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f || offset != Offset.Zero) {
                            scale = 1f
                            offset = Offset.Zero
                            gestureRotation = 0f
                            onZoomChanged(false)
                        } else {
                            scale = 3f
                            onZoomChanged(true)
                        }
                    },
                    onTap = { onTap() },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = gestureRotation
                translationX = offset.x
                translationY = offset.y
            },
    )
}
