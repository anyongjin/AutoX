package com.aiselp.autox.api

import com.baidu.paddle.lite.demo.ocr.OcrResult
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.runtime.api.Paddle

class JsPaddle : NativeApi {
    override val moduleId: String = "paddle"
    override val globalModule: Boolean = true

    private val api = Paddle()

    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {}

    @V8Function
    fun ocr(image: ImageWrapper): List<OcrResult> {
        return api.ocr(image)
    }

    @V8Function
    fun ocr(image: ImageWrapper, useSlim: Boolean): List<OcrResult> {
        return api.ocr(image, useSlim)
    }

    @V8Function
    fun ocr(image: ImageWrapper, cpuThreadNum: Int, useSlim: Boolean): List<OcrResult> {
        return api.ocr(image, cpuThreadNum, useSlim)
    }
}
