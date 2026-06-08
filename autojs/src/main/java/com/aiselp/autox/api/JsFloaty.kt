package com.aiselp.autox.api

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import com.stardust.autojs.runtime.ScriptRuntimeV2
import com.stardust.autojs.runtime.api.Floaty
import com.stardust.autojs.util.FloatingPermission
import java.util.concurrent.ConcurrentHashMap

class JsFloaty(private val runtime: ScriptRuntimeV2) : NativeApi {
    override val moduleId: String = ID
    override val globalModule: Boolean = true

    private val api: Floaty = runtime.floaty
    private val windows = ConcurrentHashMap<String, Floaty.JsRawWindow>()

    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {
        closeAll()
    }

    @V8Function
    fun checkPermission(): Boolean = api.checkPermission()

    @V8Function
    fun requestPermission() {
        api.requestPermission()
    }

    @V8Function
    fun requestFloatViewPermission(timeoutMs: Long): Boolean {
        if (api.checkPermission()) {
            return true
        }
        api.requestPermission()
        val deadline = System.currentTimeMillis() + maxOf(0L, timeoutMs)
        while (System.currentTimeMillis() < deadline) {
            if (FloatingPermission.canDrawOverlays(context)) {
                return true
            }
            Thread.sleep(200)
        }
        return FloatingPermission.canDrawOverlays(context)
    }

    @V8Function
    fun showFloatXml(tag: String, xml: String, x: Int, y: Int): View? {
        close(tag)
        val window = api.rawWindow { currentContext, parent ->
            runtime.ui.layoutInflater.setContext(currentContext)
            runtime.ui.layoutInflater.inflate(xml, parent, true)
        }
        window.setPosition(x, y)
        windows[tag] = window
        return window.getContentView()
    }

    @V8Function
    fun updateSize(tag: String, width: Int, height: Int): Boolean {
        val window = windows[tag] ?: return false
        window.setSize(width, height)
        return true
    }

    @V8Function
    fun touchable(tag: String, touchable: Boolean): Boolean {
        val window = windows[tag] ?: return false
        window.setTouchable(touchable)
        return true
    }

    @V8Function
    fun close(tag: String): Boolean {
        val window = windows.remove(tag) ?: return false
        window.close()
        return true
    }

    @V8Function
    fun closeAll() {
        windows.keys.toList().forEach { close(it) }
        api.closeAll()
        windows.clear()
    }
    private val context: Context
        get() = runtime.uiHandler.context

    companion object {
        const val ID = "floaty"
    }
}
