package com.stardust.autojs.core.image.capture

import android.app.Activity
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Path
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.result.contract.ActivityResultContract
import com.github.aiselp.autox.activity.TransparentActivity
import com.stardust.view.accessibility.AccessibilityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class ScreenCaptureManager : ScreenCaptureRequester {
    @Volatile
    override var screenCapture: ScreenCapturer? = null
    private var mediaProjection: MediaProjection? = null
    companion object {
        private const val LOG_TAG = "ScreenCaptureManager"
    }

    override suspend fun requestScreenCapture(context: Context, orientation: Int) {
        if (screenCapture?.available == true) {
            screenCapture?.setOrientation(orientation, context)
            return
        }

        val result = run {
            val result = CompletableDeferred<Intent>()
            TransparentActivity.requestNewActivity(context) { activity ->
                activity.registerForActivityResult(ScreenCaptureRequester()) { data ->
                    activity.finish()
                    if (data != null) {
                        result.complete(data)
                    } else result.completeExceptionally(CancellationException("data is null"))
                }.launch(activity)
            }
            val autoConfirmJob = startAutoConfirmJob()
            try {
                result.await()
            } finally {
                autoConfirmJob?.cancel()
            }
        }

        // 使用服务绑定确保服务就绪
        val serviceConnected = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    // 服务已连接，安全获取mediaProjection
                    mediaProjection =
                        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                            .getMediaProjection(Activity.RESULT_OK, result)
                    CaptureForegroundService.setMediaProjection(context, mediaProjection!!)
                    screenCapture = ScreenCapturer(mediaProjection!!, orientation)
                } finally {
                    serviceConnected.complete(Unit)
                    context.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 服务意外断开时处理
                serviceConnected.completeExceptionally(IllegalStateException("Service disconnected unexpectedly"))
            }
        }

        // 绑定服务并等待连接
        context.startService(Intent(context, CaptureForegroundService::class.java))
        context.bindService(
            Intent(context, CaptureForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        serviceConnected.await()
    }

    private fun startAutoConfirmJob(): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            repeat(90) {
                if (tryConfirmMediaProjectionPermission()) {
                    return@launch
                }
                delay(350)
            }
        }
    }

    private fun tryConfirmMediaProjectionPermission(): Boolean {
        val service = AccessibilityService.instance ?: return false
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            service.windows?.forEach { window ->
                window?.root?.let { roots.add(it) }
            }
        } catch (_: Exception) {
        }
        if (roots.isEmpty()) {
            (service.rootInActiveWindow ?: service.fastRootInActiveWindow())?.let { roots.add(it) }
        }
        for (root in roots) {
            try {
                val packageName = root.packageName?.toString() ?: ""
                Log.d(LOG_TAG, "tryConfirmMediaProjectionPermission rootPackage=$packageName")
                if (packageName != "com.android.systemui" && packageName != "android") {
                    continue
                }
                findFirstNodeByViewId(root, "com.android.systemui:id/remember")
                    ?.takeIf { it.isCheckable && !it.isChecked }
                    ?.let {
                        Log.d(LOG_TAG, "tryConfirmMediaProjectionPermission click remember")
                        clickNode(service, it)
                    }

                val positiveButton = findFirstNodeByViewId(root, "android:id/button1")
                    ?: findFirstNodeByText(root, listOf("INICIAR AGORA", "START NOW", "COMEÇAR AGORA", "开始", "允许"))
                if (positiveButton != null) {
                    Log.d(LOG_TAG, "tryConfirmMediaProjectionPermission click positiveButton")
                }
                if (positiveButton != null && clickNode(service, positiveButton)) {
                    return true
                }
            } finally {
                root.recycle()
            }
        }
        return false
    }

    private fun clickNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(LOG_TAG, "clickNode performAction success")
            return true
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            Log.d(LOG_TAG, "clickNode bounds empty")
            return false
        }
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        Log.d(LOG_TAG, "clickNode dispatchGesture=$dispatched x=${bounds.exactCenterX()} y=${bounds.exactCenterY()}")
        return dispatched
    }

    private fun findFirstNodeByViewId(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.firstOrNull()
    }

    private fun findFirstNodeByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            val match = root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
            if (match != null) {
                return match
            }
        }
        return null
    }

    class ScreenCaptureRequester : ActivityResultContract<Context, Intent?>() {
        override fun createIntent(context: Context, input: Context): Intent {
            return (input.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return if (resultCode != Activity.RESULT_OK) {
                null
            } else intent
        }
    }

    override fun recycle() {
        screenCapture?.release()
        screenCapture = null
        mediaProjection?.stop()
        mediaProjection = null
    }
}
