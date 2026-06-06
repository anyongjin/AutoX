/**
 * @packageDocumentation
 * v7 Paddle OCR 模块。
 */
const nativePaddle: any = (globalThis as any).paddle || (Autox as any).paddle

function ensurePaddle() {
    if (!nativePaddle) {
        throw new Error('paddle native module unavailable')
    }
    return nativePaddle
}

export function ocr(image: Autox.Image, arg1?: boolean | number, arg2?: boolean) {
    const paddle = ensurePaddle()
    if (typeof arg1 === 'number') {
        return paddle.ocr(image, arg1, Boolean(arg2))
    }
    if (typeof arg1 === 'boolean') {
        return paddle.ocr(image, arg1)
    }
    return paddle.ocr(image)
}

export default {
    ocr,
}
