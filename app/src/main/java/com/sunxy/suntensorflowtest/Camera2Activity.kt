package com.sunxy.suntensorflowtest

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sunxy.suntensorflowtest.tflite.Classifier
import com.sunxy.suntensorflowtest.utils.Camera2Helper
import com.sunxy.suntensorflowtest.view.AutoFitTextureView
import java.lang.StringBuilder

class Camera2Activity: AppCompatActivity(), Camera2Helper.OnCaptureListener, Handler.Callback{

    private val classifier : Classifier by lazy {
        Classifier.create(
            this,
            Classifier.Model.QUANTIZED,
            Classifier.Device.CPU,
            1
        )
    }

    private val textView by lazy { findViewById<TextView>(R.id.text_view) }
    private val textureView by lazy { findViewById<AutoFitTextureView>(R.id.camera_view) }
    private val handlerThread by lazy { HandlerThread("dealInfo") }
    private val dealHandler by lazy { handlerThread.start(); Handler(handlerThread.looper, this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_layout)

        findViewById<Button>(R.id.take_photo)
            .setOnClickListener { textureView.capture(this) }
    }

    override fun onResume() {
        super.onResume()
        textureView.onResume()
    }

    override fun onPause() {
        super.onPause()
        textureView.onPause()
    }

    override fun captureSuccess(bmp: Bitmap, orientation: Int) {
        Log.v("sunxy-----", "bmp: $bmp, orientation: $orientation")
        dealHandler.obtainMessage(orientation, bmp).sendToTarget()
//        val resultList = classifier.recognizeImage(bmp, orientation)
//        runOnUiThread {
//            textView.text = ""
//            for (recognition in resultList) {
//                textView.append(recognition.toString())
//                textView.append("\n")
//                textureView.capture(this)
//            }
//        }
    }

    override fun handleMessage(msg: Message): Boolean {
        val bmp = msg.obj as Bitmap
        val resultList = classifier.recognizeImage(bmp, msg.what)
        val info = StringBuilder()
        for (recognition in resultList) {
            info.append(recognition.toString())
            info.append("\n")
        }
        runOnUiThread {
            textView.text = info
            textureView.capture(this)
        }
        return true
    }

}