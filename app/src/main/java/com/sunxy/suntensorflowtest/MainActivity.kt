package com.sunxy.suntensorflowtest

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunxy.suntensorflowtest.tflite.Classifier
import com.sunxy.suntensorflowtest.tflite.Classifier.create
import com.sunxy.suntensorflowtest.view.CameraTextureView

class MainActivity : AppCompatActivity() {

    private val classifier : Classifier by lazy { create(this,  Classifier.Model.QUANTIZED, Classifier.Device.CPU, 1) }

    private val cameraView by lazy { findViewById<CameraTextureView>(R.id.camera_view) }
    private val textView by lazy { findViewById<TextView>(R.id.text_view) }
    private var bmp : Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView.perListener = object : CameraTextureView.OnPreViewListener{
            override fun onPreBmp(bmp: Bitmap) {
                val resultList = classifier.recognizeImage(bmp, 0)
                runOnUiThread {
                    textView.text = ""
                    for (recognition in resultList) {
                        textView.append(recognition.toString())
                        textView.append("\n")
                    }
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        cameraView.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
        bmp?.recycle()
        cameraView.onDestroy()
    }
}
