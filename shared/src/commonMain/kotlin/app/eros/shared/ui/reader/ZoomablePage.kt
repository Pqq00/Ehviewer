// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 单页（或单个双页槽位）的缩放状态。
 *
 * 缩放属于「页」而不是「会话」：每页各持有一份，只有当前页会收到手势，
 * 因此放大一页不会连带放大左右预组合的相邻页。
 */
internal class PageZoomState(
    private val maxZoom: () -> Float,
    private val scope: CoroutineScope,
) {
    /** 当前倍数，1f 表示未缩放。 */
    var scale by mutableFloatStateOf(1f)
        private set

    /** 相对布局中心的平移量（px）。 */
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** 页面可视区域（px），由 [ZoomableSlot] 的布局回报。 */
    var frame by mutableStateOf(IntSize.Zero)

    /** 图片在 1 倍时的实际绘制尺寸（px），用于计算可平移的边界。 */
    var content by mutableStateOf(Size.Zero)

    val isZoomed: Boolean get() = scale > 1.001f

    /** 未加阻尼的平移量；超出边界时 [offset] 只跟手一部分，抬手后弹回。 */
    private var rawOffset: Offset = Offset.Zero

    /** 双击缩放/还原的动画任务；新手势开始时取消，避免和手指抢状态。 */
    private var zoomJob: Job? = null

    fun cancelAnimation() {
        zoomJob?.cancel()
        zoomJob = null
    }

    fun reset() {
        cancelAnimation()
        if (scale != 1f || offset != Offset.Zero) {
            scale = 1f
            offset = Offset.Zero
        }
        rawOffset = Offset.Zero
    }

    /** 以 [centroid]（页面内坐标，px）为不动点缩放 [factor] 倍。 */
    fun zoomBy(factor: Float, centroid: Offset) {
        if (frame == IntSize.Zero || factor <= 0f || !factor.isFinite()) return
        val old = scale
        val target = (old * factor).coerceIn(MIN_SCALE, maxScale())
        if (target == old) return
        val pivot = centroid - Offset(frame.width / 2f, frame.height / 2f)
        val k = target / old
        scale = target
        val clamped = clampAt(pivot - (pivot - offset) * k, target)
        offset = clamped
        rawOffset = clamped
    }

    /** 平滑缩放到 [target] 倍（双击放大/双击还原），保持 [centroid] 下的内容大致不动。 */
    fun zoomTo(target: Float, centroid: Offset) {
        if (frame == IntSize.Zero) return
        cancelAnimation()
        zoomJob = scope.launch { animateZoomTo(target, centroid) }
    }

    private suspend fun animateZoomTo(target: Float, centroid: Offset) {
        val startScale = scale
        val endScale = target.coerceIn(MIN_SCALE, maxScale())
        if (startScale == endScale) return
        val startOffset = offset
        val pivot = centroid - Offset(frame.width / 2f, frame.height / 2f)
        val k = endScale / startScale
        val endOffset = clampAt(pivot - (pivot - startOffset) * k, endScale)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        ) { progress, _ ->
            scale = startScale + (endScale - startScale) * progress
            offset = Offset(
                startOffset.x + (endOffset.x - startOffset.x) * progress,
                startOffset.y + (endOffset.y - startOffset.y) * progress,
            )
        }
        scale = endScale
        offset = endOffset
        rawOffset = endOffset
    }

    private fun maxScale(): Float = maxZoom().coerceIn(MIN_SCALE + 0.5f, 6f)

    /**
     * 单指平移：边界内严格跟手；超出边界按 [RUBBER_FACTOR] 阻尼继续跟手一点。
     * 抬手后由 [settle] 弹回边界。
     */
    fun panBy(delta: Offset) {
        if (!isZoomed || delta == Offset.Zero) return
        rawOffset += delta
        offset = rubber(rawOffset)
    }

    /**
     * 抬手后收尾：缩到 1 倍以下（捏合概览）的自动恢复原大小；
     * 越界的平移弹回边界内。没有需要恢复的内容时直接返回。
     */
    suspend fun settle() {
        val fromScale = scale
        val toScale = if (fromScale < 1f) 1f else fromScale
        val from = rawOffset
        val to = clampAt(from, toScale)
        if (fromScale == toScale && from == to) return
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) { progress, _ ->
            scale = fromScale + (toScale - fromScale) * progress
            offset = Offset(
                from.x + (to.x - from.x) * progress,
                from.y + (to.y - from.y) * progress,
            )
        }
        scale = toScale
        rawOffset = to
        offset = to
    }

    /** 水平方向被边界顶住的越界量（负值=手指继续往左推，正值=往右推）。 */
    fun horizontalOverscroll(): Float =
        rawOffset.x - clampAt(rawOffset, scale).x

    /** 垂直方向被边界顶住的越界量（负值=手指继续往上推，正值=往下推）。 */
    fun verticalOverscroll(): Float =
        rawOffset.y - clampAt(rawOffset, scale).y

    private fun rubber(value: Offset): Offset {
        if (frame == IntSize.Zero) return Offset.Zero
        val contentSize = if (content == Size.Zero) frame.toSize() else content
        val maxX = ((contentSize.width * scale - frame.width) / 2f).coerceAtLeast(0f)
        val maxY = ((contentSize.height * scale - frame.height) / 2f).coerceAtLeast(0f)
        return Offset(rubberAxis(value.x, maxX), rubberAxis(value.y, maxY))
    }

    private fun rubberAxis(value: Float, max: Float): Float {
        val limit = max.coerceAtLeast(1f)
        return when {
            value > limit -> limit + (value - limit) * RUBBER_FACTOR
            value < -limit -> -limit + (value + limit) * RUBBER_FACTOR
            else -> value
        }
    }

    private fun clamp(value: Offset): Offset = clampAt(value, scale)

    private fun clampAt(value: Offset, atScale: Float): Offset {
        if (frame == IntSize.Zero) return Offset.Zero
        val contentSize = if (content == Size.Zero) frame.toSize() else content
        val maxX = ((contentSize.width * atScale - frame.width) / 2f).coerceAtLeast(0f)
        val maxY = ((contentSize.height * atScale - frame.height) / 2f).coerceAtLeast(0f)
        return Offset(
            value.x.coerceIn(-maxX, maxX),
            value.y.coerceIn(-maxY, maxY),
        )
    }

    private companion object {
        /** 越界拖动时仍然跟手的比例，越小越"拉不动"。 */
        const val RUBBER_FACTOR = 0.35f

        /**
         * 捏合缩小的下限。参数上"不限制大小"（可以缩到很小看整页），
         * 抬手后 [settle] 会自动恢复到 1 倍。
         */
        const val MIN_SCALE = 0.05f
    }
}

/**
 * 页内点击类手势的回调集合。
 *
 * 点按必须挂在**页**上而不是屏幕层：连续模式下每页各是一份 [PageZoomState]，
 * 屏幕层只认得到单页的缩放状态，双击就放大不了当前页。
 */
internal class ZoomableTaps(
    val onTap: (zoom: PageZoomState?, fractionX: Float, position: Offset) -> Unit,
    val onDoubleTap: (zoom: PageZoomState?, position: Offset) -> Unit,
    val onLongPress: () -> Unit,
    /** 缩放态下水平方向顶到边界后继续滑动：+1 下一页，-1 上一页。 */
    val onSwipePast: (direction: Int) -> Unit = {},
)

/**
 * 页内点击类手势（单击翻页/切栏、双击缩放、长按唤出栏体）。
 *
 * 抽成独立修饰符是因为「加载中 / 取地址 / 加载失败」的页没有 [ZoomableSlot]，
 * 但同样需要能点屏幕切栏——否则一进阅读器或某页在转圈时，顶底栏就切不动了。
 *
 * [zoom] 为 null 表示这一页当前不可缩放（占位状态）：双击不放大，单击照常生效。
 */
internal fun Modifier.readerTaps(zoom: PageZoomState?, taps: ZoomableTaps): Modifier =
    pointerInput(zoom, taps) {
        detectTapGestures(
            onTap = { position ->
                val width = size.width.toFloat()
                val fraction = if (width > 0f) position.x / width else 0.5f
                taps.onTap(zoom, fraction.coerceIn(0f, 1f), position)
            },
            onDoubleTap = if (zoom == null) {
                null
            } else {
                { position: Offset -> taps.onDoubleTap(zoom, position) }
            },
            onLongPress = { taps.onLongPress() },
        )
    }

/**
 * 把 [content] 放进一个可双指缩放、缩放态可单指平移的槽位。
 *
 * - 未缩放时**不消费**任何移动事件，手指水平拖动照常交给外层 Pager 翻页；
 * - 缩放态或出现第二根手指时接管手势，并把缩放/平移套到 `graphicsLayer`；
 * - 平移超出边界时有阻尼、抬手后弹回边界（橡皮筋）；
 * - [clipToBounds] 保证放大后的内容不会画到相邻页上；
 * - [taps] 非空时本槽位自己处理单击/双击/长按，位置按槽位宽度换算成比例。
 *
 * @param contentSize 计算 1 倍时内容绘制尺寸（用框尺寸推导），用于夹紧平移边界。
 * @param onZoomChanged 缩放态变化回调，供外层决定是否允许翻页/滚动。
 */
@Composable
internal fun ZoomableSlot(
    zoom: PageZoomState,
    modifier: Modifier = Modifier,
    contentSize: (IntSize) -> Size = { it.toSize() },
    onZoomChanged: (Boolean) -> Unit = {},
    taps: ZoomableTaps? = null,
    /** 缩放态下纵向顶到图片边界后继续拖：把手指位移交给列表滚动（连续模式）。 */
    onVerticalScroll: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    SideEffect {
        zoom.frame = frame
        zoom.content = contentSize(frame)
    }
    LaunchedEffect(zoom.isZoomed) { onZoomChanged(zoom.isZoomed) }

    val tapModifier = if (taps == null) Modifier else Modifier.readerTaps(zoom, taps)

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { frame = it }
            .then(tapModifier)
            .pointerInput(zoom, frame) {
                detectZoomGestures(
                    zoom = zoom,
                    onSwipePast = taps?.onSwipePast,
                    onVerticalScroll = onVerticalScroll,
                ) { scope.launch { zoom.settle() } }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        ) {
            content()
        }
    }
}

/**
 * 页内缩放手势：双指捏合缩放（以双指中点为不动点），缩放态下单指平移。
 *
 * 按下时不消费，所以单击仍能到达屏幕层的点击处理器（缩放态单击退出缩放）；
 * 一旦开始缩放或平移就消费移动事件，外层 Pager 的翻页也因此不会启动。
 */
private suspend fun PointerInputScope.detectZoomGestures(
    zoom: PageZoomState,
    onSwipePast: ((Int) -> Unit)?,
    onVerticalScroll: ((Float) -> Unit)?,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        // requireUnconsumed = false：即使上层（Pager / 点按）先处理了 down 也要接管，
        // 否则第二根手指落下那一帧可能被跳过，捏合缩小会失灵。
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        zoom.cancelAnimation()
        // 顶到水平边界后继续拖的累计量，超过页面宽度的 18% 就翻页。
        var edgePull = 0f
        val edgeThreshold = size.width * 0.18f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            // 官方换算：正确处理手指增删、指针顺序、两指重合（距离为 0）等边界情况。
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            if (pressed.size >= 2) {
                if (zoomChange.isFinite() && zoomChange != 1f) {
                    zoom.zoomBy(zoomChange, event.calculateCentroid())
                } else if (panChange != Offset.Zero && zoom.isZoomed) {
                    zoom.panBy(panChange)
                }
                event.changes.forEach { if (it.pressed) it.consume() }
            } else if (zoom.isZoomed) {
                if (panChange != Offset.Zero) {
                    val dx = panChange.x
                    var dy = panChange.y
                    // 纵向已经顶到图片上/下边，还继续往同方向推：把这一帧的竖向位移交给列表，
                    // 这样连续模式放大后仍能接着上下滚动（否则会卡在被放大的那一页）。
                    val overY = zoom.verticalOverscroll()
                    val pinned = (overY < 0f && dy < 0f) || (overY > 0f && dy > 0f)
                    if (pinned && onVerticalScroll != null) {
                        onVerticalScroll(-dy)
                        dy = 0f
                    }
                    if (dx != 0f || dy != 0f) {
                        val beforeOver = zoom.horizontalOverscroll()
                        zoom.panBy(Offset(dx, dy))
                        val over = zoom.horizontalOverscroll()
                        val pushingFurther = over != 0f &&
                            (beforeOver == 0f || (over > 0f) == (beforeOver > 0f)) &&
                            kotlin.math.abs(over) >= kotlin.math.abs(beforeOver)
                        edgePull = if (pushingFurther) edgePull + dx else 0f
                        if (edgeThreshold > 0f && kotlin.math.abs(edgePull) > edgeThreshold) {
                            // 往左拖（负）到右边界 → 下一页；往右拖到左边界 → 上一页。
                            onSwipePast?.invoke(if (edgePull < 0f) 1 else -1)
                            edgePull = 0f
                        }
                    }
                }
                event.changes.forEach { if (it.pressed) it.consume() }
            }
        }
        // 抬手后：缩放小于 1 倍恢复原大小、越界平移弹回边界（橡皮筋）。
        // waitEachGesture 处于受限的协程作用域，不能直接 await 动画，交回外层 scope 启动。
        onGestureEnd()
    }
}

/** 图片在 [frame] 内按 [contentScale] 铺开后的实际绘制尺寸（px）。 */
internal fun drawnImageSize(
    bitmap: ImageBitmap,
    contentScale: ContentScale,
    frame: IntSize,
): Size {
    if (frame.width <= 0 || frame.height <= 0) return frame.toSize()
    val src = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    val factor = contentScale.computeScaleFactor(src, frame.toSize())
    return Size(src.width * factor.scaleX, src.height * factor.scaleY)
}
