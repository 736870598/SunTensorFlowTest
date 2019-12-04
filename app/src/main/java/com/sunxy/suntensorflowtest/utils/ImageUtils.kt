package com.sunxy.suntensorflowtest.utils

import android.graphics.Bitmap
import android.media.Image
import android.util.Size

object ImageUtils{


    /**
     * 获取最符合要求大小的size
     */
    fun getOptimalSize(supportedSizes: Array<Size?>, width: Int, height: Int): Size?{
        var fixSize = supportedSizes[0]
        var minDiff = Integer.MAX_VALUE
        for (size in supportedSizes) {
            if (size == null){
                continue
            }
            if (size.width > width || size.height > height){
                continue
            }
            val diff = width - size.width + (height - size.height)
            if (diff < minDiff){
                minDiff = diff
                fixSize = size
            }
        }
        return fixSize
    }

    fun getBmpFromImage(planes: Array<Image.Plane>, previewWidth: Int, previewHeight: Int): Bitmap? {

        val yuvBytes =  arrayOfNulls<ByteArray>(3)
        fillBytes(planes, yuvBytes)

        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val rgbBytes = IntArray(previewWidth * previewHeight)
        convertYUV420ToARGB8888(
                yuvBytes[0]!!, yuvBytes[1]!!, yuvBytes[2]!!,
                previewWidth, previewHeight,
                yRowStride, uvRowStride, uvPixelStride, rgbBytes)

        val rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)


        return rgbFrameBitmap
    }


    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i]!!)
        }
    }

    private fun convertYUV420ToARGB8888(
            yData: ByteArray, uData: ByteArray, vData: ByteArray,
            width: Int, height: Int,
            yRowStride: Int, uvRowStride: Int, uvPixelStride: Int, out: IntArray) {
        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)

            for (i in 0 until width) {
                val uvOffset = pUV + (i shr 1) * uvPixelStride
                out[yp++] = YUV2RGB(0xff and yData[pY + i].toInt(),
                        0xff and uData[uvOffset].toInt(), 0xff and vData[uvOffset].toInt())
            }
        }
    }

    private val kMaxChannelValue = 262143

    private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
        var y = y
        var u = u
        var v = v
        y = if (y - 16 < 0) 0 else y - 16
        u -= 128
        v -= 128

        val y1192 = 1192 * y
        var r = y1192 + 1634 * v
        var g = y1192 - 833 * v - 400 * u
        var b = y1192 + 2066 * u

        r = if (r > kMaxChannelValue) kMaxChannelValue else if (r < 0) 0 else r
        g = if (g > kMaxChannelValue) kMaxChannelValue else if (g < 0) 0 else g
        b = if (b > kMaxChannelValue) kMaxChannelValue else if (b < 0) 0 else b

        return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
    }

    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2

        return ySize + uvSize
    }

    fun convertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {
        val frameSize = width * height
        var j = 0
        var yp = 0
        while (j < height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0

            var i = 0
            while (i < width) {
                val y = 0xff and input[yp].toInt()
                if (i and 1 == 0) {
                    v = 0xff and input[uvp++].toInt()
                    u = 0xff and input[uvp++].toInt()
                }

                output[yp] = YUV2RGB(y, u, v)
                i++
                yp++
            }
            j++
        }
    }
}