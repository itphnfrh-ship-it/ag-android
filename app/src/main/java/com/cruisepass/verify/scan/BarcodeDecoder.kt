package com.cruisepass.verify.scan

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.ITF,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC,
                    BarcodeFormat.CODABAR,
                ),
            ),
        )
    }

    fun read(image: ImageProxy): String? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.first()
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixels = ByteArray(width * height)
        var offset = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(pixels, offset, width)
            offset += width
        }
        val upright = rotate(pixels, width, height, image.imageInfo.rotationDegrees)
        val source = PlanarYUVLuminanceSource(
            upright.pixels,
            upright.width,
            upright.height,
            0,
            0,
            upright.width,
            upright.height,
            false,
        )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun rotate(pixels: ByteArray, width: Int, height: Int, degrees: Int): Frame {
        return when (degrees) {
            90 -> Frame(rotate90(pixels, width, height), height, width)
            180 -> Frame(rotate90(rotate90(pixels, width, height), height, width), width, height)
            270 -> Frame(rotate90(rotate90(rotate90(pixels, width, height), height, width), width, height), height, width)
            else -> Frame(pixels, width, height)
        }
    }

    private fun rotate90(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(pixels.size)
        var index = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[index++] = pixels[y * width + x]
            }
        }
        return rotated
    }

    private data class Frame(val pixels: ByteArray, val width: Int, val height: Int)
}
