package com.aiselp.autox.api

import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.core.image.TemplateMatching
import com.stardust.autojs.core.opencv.Mat
import com.stardust.autojs.runtime.ScriptRuntimeV2
import com.stardust.autojs.runtime.api.Images
import org.opencv.core.Point
import org.opencv.core.Rect

class JsImages(runtime: ScriptRuntimeV2) : NativeApi {
    override val moduleId: String = "images"
    override val globalModule: Boolean = true

    private val api: Images = runtime.images as Images

    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {}

    @V8Function
    fun requestScreenCapture(orientation: Int): Boolean = api.requestScreenCapture(orientation)

    @V8Function
    fun stopScreenCapturer() = api.stopScreenCapturer()

    @V8Function
    fun captureScreen(): ImageWrapper = api.captureScreen()

    @V8Function
    fun captureScreen(path: String): Boolean = api.captureScreen(path)

    @V8Function
    fun copy(image: ImageWrapper): ImageWrapper = api.copy(image)

    @V8Function
    fun save(image: ImageWrapper, path: String?, format: String, quality: Int): Boolean =
        api.save(image, path, format, quality)

    @V8Function
    fun read(path: String): ImageWrapper? = api.read(path)

    @V8Function
    fun load(src: String): ImageWrapper? = api.load(src)

    @V8Function
    fun clip(img: ImageWrapper, x: Int, y: Int, w: Int, h: Int): ImageWrapper? = api.clip(img, x, y, w, h)

    @V8Function
    fun pixel(image: ImageWrapper, x: Int, y: Int): Int = api.pixel(image, x, y)

    @V8Function
    fun rotate(img: ImageWrapper, x: Float, y: Float, degree: Float): ImageWrapper =
        api.rotate(img, x, y, degree)

    @V8Function
    fun concat(img1: ImageWrapper, img2: ImageWrapper, direction: Int): ImageWrapper =
        api.concat(img1, img2, direction)

    @V8Function
    fun fromBase64(data: String): ImageWrapper? = api.fromBase64(data)

    @V8Function
    fun toBase64(wrapper: ImageWrapper, format: String, quality: Int): String =
        api.toBase64(wrapper, format, quality)

    @V8Function
    fun fromBytes(bytes: ByteArray): ImageWrapper = api.fromBytes(bytes)

    @V8Function
    fun toBytes(wrapper: ImageWrapper, format: String, quality: Int): ByteArray =
        api.toBytes(wrapper, format, quality)

    @V8Function
    fun initOpenCvIfNeeded() = api.initOpenCvIfNeeded()

    @V8Function
    fun findImage(
        image: ImageWrapper?,
        template: ImageWrapper?,
        weakThreshold: Float,
        threshold: Float,
        rect: Rect?,
        maxLevel: Int,
        transparentMask: Boolean,
    ): Point? = api.findImage(image, template, weakThreshold, threshold, rect, maxLevel, transparentMask)

    @V8Function
    fun matchTemplate(
        image: ImageWrapper?,
        template: ImageWrapper?,
        weakThreshold: Float,
        threshold: Float,
        rect: Rect?,
        maxLevel: Int,
        limit: Int,
        transparentMask: Boolean,
    ): List<TemplateMatching.Match> =
        api.matchTemplate(image, template, weakThreshold, threshold, rect, maxLevel, limit, transparentMask)

    @V8Function
    fun newMat(): Mat = api.newMat()
}
