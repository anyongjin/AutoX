package com.stardust.automator

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

internal object GestureTraceOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var enabled = false

    @Volatile
    private var serviceProvider: (() -> AccessibilityService)? = null

    private var overlayView: TraceOverlayView? = null
    private var windowManager: WindowManager? = null

    fun configure(provider: () -> AccessibilityService) {
        serviceProvider = provider
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        mainHandler.post {
            if (value) {
                ensureAttached()
            } else {
                detach()
            }
        }
    }

    fun isEnabled(): Boolean = enabled

    fun traceGesture(points: List<PointF>, durationMs: Long) {
        if (!enabled || points.isEmpty()) {
            return
        }
        mainHandler.post {
            ensureAttached()
            overlayView?.addGesture(points, durationMs)
        }
    }

    fun traceNodeClick(bounds: Rect, isLongPress: Boolean) {
        if (!enabled || bounds.isEmpty) {
            return
        }
        mainHandler.post {
            ensureAttached()
            overlayView?.addNodeAction(bounds, isLongPress)
        }
    }

    private fun ensureAttached() {
        if (!enabled || overlayView != null) {
            return
        }
        val service = serviceProvider?.invoke() ?: return
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = TraceOverlayView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            manager.addView(view, params)
            overlayView = view
            windowManager = manager
        } catch (_: Throwable) {
            overlayView = null
            windowManager = null
        }
    }

    private fun detach() {
        val view = overlayView ?: return
        overlayView = null
        val manager = windowManager
        windowManager = null
        try {
            manager?.removeViewImmediate(view)
        } catch (_: Throwable) {
        }
    }

    private class TraceOverlayView(context: Context) : View(context) {
        private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#3DDC84")
            strokeWidth = dp(6f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#3DDC84")
        }
        private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#00C2FF")
            strokeWidth = dp(4f)
        }
        private val rectFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#3300C2FF")
        }
        private val longPressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#FFC107")
            strokeWidth = dp(5f)
        }
        private val longPressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#33FFC107")
        }
        private val entries = mutableListOf<TraceEntry>()

        fun addGesture(points: List<PointF>, durationMs: Long) {
            val lifetime = max(durationMs, 2200L)
            if (points.size == 1) {
                entries += TapEntry(
                    center = PointF(points[0].x, points[0].y),
                    startedAt = SystemClock.uptimeMillis(),
                    lifetimeMs = lifetime + 1000L,
                )
            } else {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (index in 1 until points.size) {
                        lineTo(points[index].x, points[index].y)
                    }
                }
                entries += PathEntry(
                    path = path,
                    endPoint = PointF(points.last().x, points.last().y),
                    startedAt = SystemClock.uptimeMillis(),
                    lifetimeMs = lifetime + 1200L,
                )
            }
            postInvalidateOnAnimation()
        }

        fun addNodeAction(bounds: Rect, isLongPress: Boolean) {
            entries += BoundsEntry(
                rect = RectF(bounds),
                startedAt = SystemClock.uptimeMillis(),
                lifetimeMs = 2600L,
                longPress = isLongPress,
            )
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            val iterator = entries.iterator()
            var needsNextFrame = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val progress = ((now - entry.startedAt).toFloat() / entry.lifetimeMs.toFloat()).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    iterator.remove()
                    continue
                }
                entry.draw(canvas, 1f - progress, this)
                needsNextFrame = true
            }
            if (needsNextFrame) {
                postInvalidateOnAnimation()
            }
        }

        private fun dp(value: Float): Float = value * resources.displayMetrics.density

        private sealed class TraceEntry(
            val startedAt: Long,
            val lifetimeMs: Long,
        ) {
            abstract fun draw(canvas: Canvas, alphaFactor: Float, view: TraceOverlayView)
        }

        private class PathEntry(
            private val path: Path,
            private val endPoint: PointF,
            startedAt: Long,
            lifetimeMs: Long,
        ) : TraceEntry(startedAt, lifetimeMs) {
            override fun draw(canvas: Canvas, alphaFactor: Float, view: TraceOverlayView) {
                val alpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
                view.pathPaint.alpha = alpha
                view.pointPaint.alpha = alpha
                canvas.drawPath(path, view.pathPaint)
                canvas.drawCircle(endPoint.x, endPoint.y, view.dp(10f), view.pointPaint)
            }
        }

        private class TapEntry(
            private val center: PointF,
            startedAt: Long,
            lifetimeMs: Long,
        ) : TraceEntry(startedAt, lifetimeMs) {
            override fun draw(canvas: Canvas, alphaFactor: Float, view: TraceOverlayView) {
                val alpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
                val radius = view.dp(18f) + (1f - alphaFactor) * view.dp(26f)
                view.pointPaint.alpha = alpha
                view.pathPaint.alpha = alpha
                canvas.drawCircle(center.x, center.y, radius, view.pointPaint)
                canvas.drawCircle(center.x, center.y, radius + view.dp(10f), view.pathPaint)
            }
        }

        private class BoundsEntry(
            private val rect: RectF,
            startedAt: Long,
            lifetimeMs: Long,
            private val longPress: Boolean,
        ) : TraceEntry(startedAt, lifetimeMs) {
            override fun draw(canvas: Canvas, alphaFactor: Float, view: TraceOverlayView) {
                val alpha = (255 * alphaFactor).toInt().coerceIn(0, 255)
                val strokePaint = if (longPress) view.longPressPaint else view.rectPaint
                val fillPaint = if (longPress) view.longPressFillPaint else view.rectFillPaint
                strokePaint.alpha = alpha
                fillPaint.alpha = min(alpha, 96)
                canvas.drawRoundRect(rect, view.dp(14f), view.dp(14f), fillPaint)
                canvas.drawRoundRect(rect, view.dp(14f), view.dp(14f), strokePaint)
            }
        }
    }
}
