/**
 * @packageDocumentation
 * v7 Google ML Kit OCR 模块。
 */
const nativeGmlkit: any = (globalThis as any).gmlkit || (Autox as any).gmlkit

function ensureGmlkit() {
    if (!nativeGmlkit) {
        throw new Error('gmlkit native module unavailable')
    }
    return nativeGmlkit
}

export function ocr(image: Autox.Image, language: string) {
    return ensureGmlkit().ocr(image, language)
}

export function ocrText(image: Autox.Image, language: string) {
    return ensureGmlkit().ocrText(image, language)
}

export default {
    ocr,
    ocrText,
}
