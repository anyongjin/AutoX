/**
 * @packageDocumentation
 * v7 图片模块。提供截图、图片裁剪、OpenCV 图像处理，以及模板匹配能力。
 */
import { android, loadClass } from '@/java'

type Image = Autox.Image
type ImageFormat = 'png' | 'jpeg' | 'webp' | 'jpg'

export interface FindImageOptions {
    threshold?: number
    weakThreshold?: number
    region?: [number, number, number?, number?]
    level?: number
    transparentMask?: boolean
}

export interface MatchTemplateOptions extends FindImageOptions {
    max?: number
}

export type TemplateMatch = {
    point: { x: number, y: number }
    similarity: number
}

const nativeImages: any = (globalThis as any).images || (Autox as any).images
const ScreenCapturer = loadClass('com.stardust.autojs.core.image.capture.ScreenCapturer') as any
const PointClass = loadClass('org.opencv.core.Point') as any
const RectClass = loadClass('org.opencv.core.Rect') as any
const ScalarClass = loadClass('org.opencv.core.Scalar') as any
const SizeClass = loadClass('org.opencv.core.Size') as any
const Core = loadClass('org.opencv.core.Core') as any
const Imgproc = loadClass('org.opencv.imgproc.Imgproc') as any
const MatClass = loadClass('com.stardust.autojs.core.opencv.Mat') as any
const ImageWrapper = loadClass('com.stardust.autojs.core.image.ImageWrapper') as any

function ensureImages() {
    if (!nativeImages) {
        throw new Error('images native module unavailable')
    }
    nativeImages.initOpenCvIfNeeded?.()
    return nativeImages
}

function normalizeRegion(region: [number, number, number?, number?] | undefined, image: Image) {
    if (!region) {
        return null
    }
    const x = Math.max(0, Math.round(region[0] || 0))
    const y = Math.max(0, Math.round(region[1] || 0))
    const width = region[2] == null ? Math.max(0, image.width - x) : Math.max(0, Math.round(region[2] || 0))
    const height = region[3] == null ? Math.max(0, image.height - y) : Math.max(0, Math.round(region[3] || 0))
    return new RectClass(x, y, width, height)
}

function newSize(size: [number, number?]) {
    return new SizeClass(
        Number(size[0] || 0),
        Number(size[1] == null ? size[0] || 0 : size[1]),
    )
}

function matToImage(mat: any): Image {
    ensureImages()
    return ImageWrapper.ofMat(mat)
}

export class MatchingResult {
    matches: TemplateMatch[]

    constructor(list: any) {
        this.matches = Array.isArray(list) ? list : Array.from(list || [])
    }

    first() {
        return this.matches.length > 0 ? this.matches[0] : null
    }

    last() {
        return this.matches.length > 0 ? this.matches[this.matches.length - 1] : null
    }

    best() {
        return this.matches.reduce<TemplateMatch | null>((best, current) => {
            if (!best || Number(current.similarity || 0) > Number(best.similarity || 0)) {
                return current
            }
            return best
        }, null)
    }
}

export function requestScreenCapture(landscape?: boolean) {
    const images = ensureImages()
    let orientation = ScreenCapturer.ORIENTATION_AUTO
    if (landscape === true) {
        orientation = ScreenCapturer.ORIENTATION_LANDSCAPE
    }
    if (landscape === false) {
        orientation = ScreenCapturer.ORIENTATION_PORTRAIT
    }
    return images.requestScreenCapture(orientation)
}

export function stopScreenCapturer() {
    return ensureImages().stopScreenCapturer()
}

export function captureScreen() {
    return ensureImages().captureScreen()
}

export function read(path: string) {
    return ensureImages().read(path)
}

export function copy(image: Image) {
    return ensureImages().copy(image)
}

export function load(src: string) {
    return ensureImages().load(src)
}

export function clip(img: Image, x: number, y: number, w: number, h: number) {
    return ensureImages().clip(img, x, y, w, h)
}

export function pixel(img: Image, x: number, y: number) {
    return ensureImages().pixel(img, x, y)
}

export function save(img: Image, path: string, format: ImageFormat = 'png', quality: number = 100) {
    return ensureImages().save(img, path, format, quality)
}

export const saveImage = save

export function grayscale(img: Image, dstCn?: number): Image {
    return cvtColor(img, 'BGR2GRAY', dstCn)
}

export function threshold(img: Image, thresholdValue: number, maxVal: number, type: string = 'BINARY') {
    ensureImages()
    const mat = new MatClass()
    Imgproc.threshold(img.mat, mat, thresholdValue, maxVal, Imgproc[`THRESH_${type}`])
    return matToImage(mat)
}

export function adaptiveThreshold(
    img: Image,
    maxValue: number,
    adaptiveMethod: 'MEAN_C' | 'GAUSSIAN_C',
    thresholdType: 'BINARY' | 'BINARY_INV',
    blockSize: number,
    C: number,
) {
    ensureImages()
    const mat = new MatClass()
    Imgproc.adaptiveThreshold(
        img.mat,
        mat,
        maxValue,
        Imgproc[`ADAPTIVE_THRESH_${adaptiveMethod}`],
        Imgproc[`THRESH_${thresholdType}`],
        blockSize,
        C,
    )
    return matToImage(mat)
}

export function blur(img: Image, size: [number, number?], point?: [number, number], type: string = 'DEFAULT') {
    ensureImages()
    const mat = new MatClass()
    const borderType = Core[`BORDER_${type}`]
    if (point) {
        Imgproc.blur(img.mat, mat, newSize(size), new PointClass(point[0], point[1]), borderType)
    } else {
        Imgproc.blur(img.mat, mat, newSize(size))
    }
    return matToImage(mat)
}

export function medianBlur(img: Image, size: number) {
    ensureImages()
    const mat = new MatClass()
    Imgproc.medianBlur(img.mat, mat, size)
    return matToImage(mat)
}

export function gaussianBlur(img: Image, size: [number, number?], sigmaX: number = 0, sigmaY: number = 0, type: string = 'DEFAULT') {
    ensureImages()
    const mat = new MatClass()
    Core.getVersionString?.()
    Imgproc.GaussianBlur(img.mat, mat, newSize(size), sigmaX, sigmaY, Core[`BORDER_${type}`])
    return matToImage(mat)
}

export function cvtColor(img: Image, code: string, dstCn?: number) {
    ensureImages()
    const mat = new MatClass()
    const cvCode = Imgproc[`COLOR_${code}`]
    if (dstCn == null) {
        Imgproc.cvtColor(img.mat, mat, cvCode)
    } else {
        Imgproc.cvtColor(img.mat, mat, cvCode, dstCn)
    }
    return matToImage(mat)
}

export function resize(img: Image, size: [number, number?], interpolation: string = 'LINEAR') {
    ensureImages()
    const mat = new MatClass()
    Imgproc.resize(img.mat, mat, newSize(size), 0, 0, Imgproc[`INTER_${interpolation}`])
    return matToImage(mat)
}

export function scale(img: Image, fx: number, fy: number, interpolation: string = 'LINEAR') {
    ensureImages()
    const mat = new MatClass()
    Imgproc.resize(img.mat, mat, newSize([0, 0]), fx, fy, Imgproc[`INTER_${interpolation}`])
    return matToImage(mat)
}

export function rotate(img: Image, degree: number, x?: number, y?: number) {
    const images = ensureImages()
    return images.rotate(
        img,
        Number(x == null ? img.width / 2 : x),
        Number(y == null ? img.height / 2 : y),
        degree,
    )
}

export function concat(img1: Image, img2: Image, direction: 'left' | 'right' | 'top' | 'bottom' = 'right') {
    const gravity = android.view.Gravity[direction.toUpperCase()]
    return ensureImages().concat(img1, img2, gravity)
}

export function findImage(img: Image, template: Image, options: FindImageOptions = {}) {
    const images = ensureImages()
    return images.findImage(
        img,
        template,
        Number(options.weakThreshold ?? 0.7),
        Number(options.threshold ?? 0.9),
        normalizeRegion(options.region, img),
        Number(options.level ?? -1),
        Boolean(options.transparentMask ?? false),
    )
}

export function matchTemplate(img: Image, template: Image, options: MatchTemplateOptions = {}) {
    const images = ensureImages()
    const result = images.matchTemplate(
        img,
        template,
        Number(options.weakThreshold ?? 0.7),
        Number(options.threshold ?? 0.9),
        normalizeRegion(options.region, img),
        Number(options.level ?? -1),
        Number(options.max ?? 5),
        Boolean(options.transparentMask ?? false),
    )
    return new MatchingResult(result)
}

export function fromBase64(data: string) {
    return ensureImages().fromBase64(data)
}

export function toBase64(img: Image, format: ImageFormat = 'png', quality: number = 100) {
    return ensureImages().toBase64(img, format, quality)
}

export function fromBytes(bytes: any) {
    return ensureImages().fromBytes(bytes)
}

export function toBytes(img: Image, format: ImageFormat = 'png', quality: number = 100) {
    return ensureImages().toBytes(img, format, quality)
}

export const opencvImporter = {
    Point: PointClass,
    Rect: RectClass,
    Scalar: ScalarClass,
    Size: SizeClass,
    Core,
    Imgproc,
    Mat: MatClass,
}

export default {
    requestScreenCapture,
    stopScreenCapturer,
    captureScreen,
    read,
    copy,
    load,
    clip,
    pixel,
    save,
    saveImage,
    grayscale,
    threshold,
    adaptiveThreshold,
    blur,
    medianBlur,
    gaussianBlur,
    cvtColor,
    resize,
    scale,
    rotate,
    concat,
    findImage,
    matchTemplate,
    fromBase64,
    toBase64,
    fromBytes,
    toBytes,
    MatchingResult,
    opencvImporter,
}
