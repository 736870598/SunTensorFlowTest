package com.sunxy.suntensorflowtest.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.WorkerThread

class Camera2Helper(private val context: Context,
                    private val textureView: TextureView,
                    private val initListener: OnInitCompleteListener? = null) {

    private val cameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private val imageFormat = ImageFormat.YUV_420_888

    private var isFrontCamera = false
    //前置摄像头
    private val frontCameraInfo by lazy { CameraInfo() }
    //后置摄像头
    private val backCameraInfo by lazy { CameraInfo() }

    private val cameraThreadHandler by lazy{ HandlerThread("cameraThreadHandler") }
    private val cameraHandler by lazy{ cameraThreadHandler.start(); Handler(cameraThreadHandler.looper) }
    private val previewThreadHandler by lazy{ HandlerThread("previewThreadHandler") }
    private val previewHandler by lazy{ previewThreadHandler.start(); Handler(previewThreadHandler.looper) }

    private var cameraDevice: CameraDevice? = null
    var previewDataImageReader: ImageReader? = null

    //用于接收预览数据的 Surface
    private var request: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null

    private val surfaceTextureListener by lazy{ PreviewSurfaceTextureListener() }
    private val cameraStateCallback by lazy { CameraStateCallback() }
    private val sessionStateCallback by lazy { SessionStateCallback() }
    private val repeatingCaptureStateCallback by lazy { RepeatingCaptureStateCallback() }

    private var previewListener: OnPreviewDataListener? = null
    private var captureListener: OnCaptureListener? = null


    data class CameraInfo(var cameraId : String = "", var cameraSize: Size? = null, var cameraYUV: Boolean = false, var cameraOri: Int = 0)

    init {
        getCameraId()
    }

    /**
     * 获取前后摄像头的CameraId
     */
    private fun getCameraId(){
        val cameraIdList = cameraManager.cameraIdList
        cameraIdList.forEach { cameraId ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = cameraCharacteristics[CameraCharacteristics.LENS_FACING]
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                frontCameraInfo.cameraId = cameraId
                frontCameraInfo.cameraOri = getJpegOrientation(cameraCharacteristics)
            } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                backCameraInfo.cameraId = cameraId
                backCameraInfo.cameraOri = getJpegOrientation(cameraCharacteristics)
            }
        }
    }

    fun changeCamera(){
        closeCamera()
        isFrontCamera = !isFrontCamera
        openCamera(isFrontCamera)
    }

    fun open() {
        if (textureView.isAvailable) {
            openCamera(isFrontCamera)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    fun close() {
        closeCamera()
    }

    /**
     * 拍照
     */
    fun capture(onCaptureDataListener: OnCaptureListener){
        this.captureListener = onCaptureDataListener
    }

    /**
     *  开启相机
     */
    private fun openCamera(isFront: Boolean) {
        val cameraInfo = if (isFront) frontCameraInfo else backCameraInfo
        initListener?.initComplete(cameraInfo)

        //打开摄像头。
        if (cameraInfo.cameraId != "") {
            //TODO 权限检查，最低运行版本检查。
            cameraManager.openCamera(cameraInfo.cameraId, cameraStateCallback, cameraHandler)
        } else {
            throw RuntimeException("Camera id must not be null.")
        }
    }

    /**
     * 关闭相机
     */
    private fun closeCamera() {
        cameraDevice?.close()
    }

    /**
     * 图片的方向。。。
     */
    private fun getJpegOrientation(cameraCharacteristics: CameraCharacteristics): Int {
        var myDeviceOrientation = getDeviceOrientation()
        if (myDeviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0
        }
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        // Round device orientation to a multiple of 90
        myDeviceOrientation = (myDeviceOrientation + 45) / 90 * 90

        // Reverse device orientation for front-facing cameras
        val facingFront = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        if (facingFront) {
            myDeviceOrientation = -myDeviceOrientation
        }
        return (sensorOrientation + myDeviceOrientation + 360) % 360
    }

    /**
     * 获取当前设备的方向、
     */
    private fun getDeviceOrientation(): Int{
        if(textureView.context is Activity){
            return when ((textureView.context as Activity).windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        }
        return 0
    }


    /**
     * TextureView 的listener
     */
    private inner class PreviewSurfaceTextureListener : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            //获取最佳预览尺寸
            var characteristics = cameraManager.getCameraCharacteristics(frontCameraInfo.cameraId)
            var map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            frontCameraInfo.cameraYUV = map?.isOutputSupportedFor(imageFormat) == true
            if (backCameraInfo.cameraOri / 90 % 2 == 0){
                frontCameraInfo.cameraSize = ImageUtils.getOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    width/2,
                    height/2
                )
            }else{
                frontCameraInfo.cameraSize = ImageUtils.getOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    height/2,
                    width/2
                )
            }

            characteristics = cameraManager.getCameraCharacteristics(backCameraInfo.cameraId)
            map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            backCameraInfo.cameraYUV = map?.isOutputSupportedFor(imageFormat) == true
            if (backCameraInfo.cameraOri / 90 % 2 == 0){
                backCameraInfo.cameraSize = ImageUtils.getOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    width/2,
                    height/2
                )
            }else{
                backCameraInfo.cameraSize = ImageUtils.getOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    height/2,
                    width/2
                )
            }

            //打开摄像头。
            openCamera(isFrontCamera)
        }
    }

    /**
     * 开启相机回调接口
     * 成功后拿到CameraDevice创建预览
     */
    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            openCamera(isFrontCamera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }

        @WorkerThread
        override fun onClosed(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
            captureSession?.close()
            captureSession = null
            previewDataImageReader?.close()
            previewDataImageReader = null
        }
    }

    private fun createCaptureSession(){
        val cameraInfo = if (isFrontCamera) frontCameraInfo else backCameraInfo

        val previewSurfaceTexture = textureView.surfaceTexture
        previewSurfaceTexture?.setDefaultBufferSize(cameraInfo.cameraSize!!.width, cameraInfo.cameraSize!!.height)
        val previewSurface = Surface(previewSurfaceTexture)

        //创建requestBuilder
        //TEMPLATE_PREVIEW：适用于配置预览的模板。
        //TEMPLATE_RECORD：适用于视频录制的模板。
        //TEMPLATE_STILL_CAPTURE：适用于拍照的模板。
        //TEMPLATE_VIDEO_SNAPSHOT：适用于在录制视频过程中支持拍照的模板。
        //TEMPLATE_MANUAL：适用于希望自己手动配置大部分参数的模板。
        val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        requestBuilder?.addTarget(previewSurface)
        val outputs = arrayListOf(previewSurface)

        if (cameraInfo.cameraYUV) {
            previewDataImageReader = ImageReader.newInstance(cameraInfo.cameraSize!!.width, cameraInfo.cameraSize!!.height, imageFormat, 2)
            previewDataImageReader?.setOnImageAvailableListener(OnPreviewDataAvailableListener(), previewHandler)
            val surface = previewDataImageReader!!.surface
            requestBuilder?.addTarget(surface)
            outputs.add(surface)
        }
        request = requestBuilder?.build()
        cameraDevice?.createCaptureSession(outputs, sessionStateCallback, cameraHandler)
    }


    /**
     * 会话状态回调接口
     *  CameraCaptureSession创建成功后，开启预览。
     */
    private inner class SessionStateCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
        }

        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            captureSession?.setRepeatingRequest(request!!, repeatingCaptureStateCallback, cameraHandler)
        }

        override fun onClosed(session: CameraCaptureSession) {
            session.close()
        }
    }

    /**
     * 预览界面回调
     */
    private inner class RepeatingCaptureStateCallback: CameraCaptureSession.CaptureCallback() {}


    private inner class OnPreviewDataAvailableListener: ImageReader.OnImageAvailableListener{
        override fun onImageAvailable(imageReader: ImageReader) {
            val image = imageReader.acquireNextImage()
            if (captureListener != null){
                val cameraInfo = if (isFrontCamera) frontCameraInfo else backCameraInfo
                val bmp = ImageUtils.getBmpFromImage(
                    image.planes,
                    cameraInfo.cameraSize!!.width,
                    cameraInfo.cameraSize!!.height
                )
                captureListener?.captureSuccess(bmp!!, cameraInfo.cameraOri)
                captureListener = null
            }else if (previewListener != null){
                previewListener?.previewData(image)
            }
            image?.close()
        }
    }

    interface OnInitCompleteListener{
        fun initComplete(cameraInfo: CameraInfo)
    }

    interface OnPreviewDataListener{
        fun previewData(image: Image)
    }

    interface OnCaptureListener{
        fun captureSuccess(bmp: Bitmap, orientation: Int)
    }

}