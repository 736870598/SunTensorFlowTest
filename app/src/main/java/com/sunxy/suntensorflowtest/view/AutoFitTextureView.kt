package com.sunxy.suntensorflowtest.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import com.sunxy.suntensorflowtest.utils.Camera2Helper

class AutoFitTextureView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, style:Int = 0):
        TextureView(context, attributeSet, style), Camera2Helper.OnInitCompleteListener{


    private var ratioWidth = 0
    private var ratioHeight = 0

    private val camera2Helper by lazy { Camera2Helper(getContext(), this, this) }

    override fun initComplete(cameraInfo: Camera2Helper.CameraInfo) {
        Log.v("sunxy-----", "cameraInfo: $cameraInfo")
        if (cameraInfo.cameraOri / 90 % 2 == 0){
            setAspectRatio(cameraInfo.cameraSize!!.width, cameraInfo.cameraSize!!.height)
        }else{
            setAspectRatio(cameraInfo.cameraSize!!.height, cameraInfo.cameraSize!!.width)
        }
    }

    fun onResume(){
        camera2Helper.open()
    }

    fun onPause(){
        camera2Helper.close()
    }

    fun capture(listener: Camera2Helper.OnCaptureListener){
        camera2Helper.capture(listener)
    }




    //--------------------------------------------------------------------------------------------------
    fun setAspectRatio(width: Int, height: Int) {
        if (width != ratioWidth || height != ratioHeight){
            ratioWidth = width
            ratioHeight = height
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(width, height)
        }else{
            setMeasuredDimension(ratioWidth, ratioHeight)
//            setMeasuredDimension(width, width * ratioHeight / ratioWidth)
        }
    }
}

