package com.sunxy.suntensorflowtest.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Size
import android.view.TextureView
import com.sunxy.suntensorflowtest.utils.ImageUtils
import java.io.IOException

class CameraTextureView @JvmOverloads
constructor(context: Context, attrSet: AttributeSet? = null, attrStyle: Int = 0)
    : TextureView(context, attrSet, attrStyle){

    private var camera: Camera? = null
    private val desiredSize by lazy {Size(640, 480)}
    var perListener: OnPreViewListener? = null
    private val handlerThread by lazy { HandlerThread("deal") }
    private val dealHandler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper, Handler.Callback {
            val previewSize = camera!!.parameters?.previewSize
            val previewHeight = previewSize!!.height
            val previewWidth = previewSize!!.width
            val rgbBytes = IntArray(previewWidth * previewHeight)
            val rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
            ImageUtils.convertYUV420SPToARGB8888(it.obj as ByteArray, previewWidth, previewHeight, rgbBytes)
            rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
            perListener?.onPreBmp(rgbFrameBitmap)
            true
        })
    }

    private val textureListener = object: SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera(surface)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) { }
    }

    private val imageListener = Camera.PreviewCallback { data, camera ->
        val message = dealHandler.obtainMessage();
        message.obj = data
        dealHandler.sendMessage(message)
        camera.addCallbackBuffer(data)
    }

    init {
        surfaceTextureListener = textureListener
    }

    private fun openCamera(surface: SurfaceTexture?){
        val index = getCameraId()
        camera = Camera.open(index)
        try {
            val parameters = camera?.parameters
            val focusModes = parameters?.supportedFocusModes
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            val cameraSizes = parameters?.supportedPreviewSizes
            val sizes = arrayOfNulls<Size>(cameraSizes!!.size)
            var i = 0
            for (size in cameraSizes!!) {
                sizes[i++] = Size(size.width, size.height)
            }
            val previewSize = ImageUtils.getOptimalSize(
                sizes, desiredSize.width, desiredSize.height
            )
            parameters.setPreviewSize(previewSize!!.width, previewSize.height)
            camera?.setDisplayOrientation(90)
            camera?.parameters = parameters
            camera?.setPreviewTexture(surface)
        } catch (exception: IOException) {
            camera?.release()
        }


        camera?.setPreviewCallbackWithBuffer(imageListener)
        val s = camera?.parameters?.previewSize
        camera?.addCallbackBuffer(ByteArray(ImageUtils.getYUVByteSize(s!!.height, s!!.width)))

        setAspectRatio(s?.height, s?.width)

        camera?.startPreview()
    }

    private fun getCameraId(): Int {
        val ci = Camera.CameraInfo()
        for (i in 0..Camera.getNumberOfCameras()){
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
        }
        return -1
    }

    fun onResume() {
        if (isAvailable) {
            camera?.startPreview()
        } else {
            surfaceTextureListener = textureListener
        }
    }

    fun onPause() {
        camera?.stopPreview()
    }


    fun onDestroy(){
        camera?.stopPreview()
        camera?.setPreviewCallback(null)
        camera?.release()
        camera = null
        handlerThread.quit()

    }


    interface OnPreViewListener{
        fun onPreBmp(bmp: Bitmap)
    }


    private var mRatioWidth :Int? = 0
    private var mRatioHeight :Int? = 0

    fun setAspectRatio(width: Int?, height: Int?) {
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height)
        }else{
            if (width < height * mRatioWidth!! / mRatioHeight!!) {
                setMeasuredDimension(width, width * mRatioHeight!! / mRatioWidth!!)
            } else {
                setMeasuredDimension(height * mRatioWidth!! / mRatioHeight!!, height)
            }
        }
    }

}