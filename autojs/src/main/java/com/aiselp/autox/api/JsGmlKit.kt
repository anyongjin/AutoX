package com.aiselp.autox.api

import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import com.stardust.autojs.core.image.ImageWrapper
import com.stardust.autojs.core.mlkit.GoogleMLKitOcrResult
import com.stardust.autojs.runtime.api.GoogleMLKit

class JsGmlKit : NativeApi {
    override val moduleId: String = "gmlkit"
    override val globalModule: Boolean = true

    private val api = GoogleMLKit()

    override fun install(v8Runtime: V8Runtime, global: V8ValueObject): NativeApi.BindingMode {
        return NativeApi.BindingMode.ObjectBind
    }

    override fun recycle(v8Runtime: V8Runtime, global: V8ValueObject) {}

    @V8Function
    fun ocr(image: ImageWrapper, language: String): GoogleMLKitOcrResult? {
        return api.ocr(image, language)
    }

    @V8Function
    fun ocrText(image: ImageWrapper, language: String): String {
        return api.ocrText(image, language)
    }
}
