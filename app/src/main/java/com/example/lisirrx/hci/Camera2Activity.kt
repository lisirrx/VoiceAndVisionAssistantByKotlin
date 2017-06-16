package com.example.lisirrx.hci

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Point
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import org.jetbrains.anko.cameraManager

import java.nio.ByteBuffer
import java.util.*

/**
 * Created by lisirrx on 17-6-16.
 */
class Camera2Activity : AppCompatActivity(), View.OnClickListener {

    private var picture : Bitmap? = null

    private var mSurfaceView: SurfaceView? = null
    private var mSurfaceHolder: SurfaceHolder? = null
    private var iv_show: ImageView? = null
    private var mCameraManager: CameraManager? = null//摄像头管理器
    private var childHandler: Handler? = null
    private var mainHandler: Handler? = null
    private var mCameraID: String? = null//摄像头Id 0 为后  1 为前
    private var mImageReader: ImageReader? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private val windowHeight : Int by lazy {
        var wm = this.getWindowManager()
        var size = Point()
        wm.defaultDisplay.getSize(size)
        size.y
    }

    private val windowWidth : Int by lazy {
        var wm = this.getWindowManager()
        var size = Point()
        wm.defaultDisplay.getSize(size)
        size.x
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vision)

        initVIew()
    }


    private fun initVIew() {
        iv_show = findViewById(R.id.image_view) as ImageView
        //mSurfaceView
        mSurfaceView = findViewById(R.id.surface_view) as SurfaceView
        mSurfaceView!!.setOnClickListener(this)
        mSurfaceHolder = mSurfaceView!!.holder
        mSurfaceHolder!!.setKeepScreenOn(true)
        // mSurfaceView添加回调
        mSurfaceHolder!!.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { //SurfaceView创建
                // 初始化Camera

                initCamera2()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) { //SurfaceView销毁
                // 释放Camera资源
                if (null != mCameraDevice) {
                    mCameraDevice!!.close()
                    this@Camera2Activity.mCameraDevice = null
                }
            }
        })
    }


    private fun initCamera2() {
        val handlerThread = HandlerThread("Camera2")
        handlerThread.start()
        childHandler = Handler(handlerThread.looper)
        mainHandler = Handler(mainLooper)
        mCameraID =  "" + CameraCharacteristics.LENS_FACING_FRONT
        mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 100)
        mImageReader!!.setOnImageAvailableListener({ reader ->
            //可以在这里处理拍照得到的临时照片 例如，写入本地
//            mCameraDevice!!.close()
//            mSurfaceView!!.visibility = View.GONE
//            iv_show!!.visibility = View.VISIBLE
            // 拿到拍照照片数据

            val image = reader.acquireLatestImage()

            val buffer = image.planes[0].buffer



            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)//由缓冲区存入字节数组
            picture= BitmapFactory.decodeByteArray(bytes, 0, bytes.size)



            if (picture!= null) {
                iv_show!!.setImageBitmap(picture)

            }
        }, mainHandler)
        //获取摄像头管理
        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.d("THIS", "Permission Fault")
                ActivityCompat.requestPermissions(this@Camera2Activity, arrayOf("" + Manifest.permission.CAMERA), 1)


            }
            //打开摄像头
            Log.d("THIS", "OPEN Camera")
            mCameraManager!!.openCamera(mCameraID, stateCallback, mainHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }



    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {//打开摄像头
            mCameraDevice = camera
            //开启预览

            takePreview()



        }

        override fun onDisconnected(camera: CameraDevice) {//关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                this@Camera2Activity.mCameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {//发生错误
            Toast.makeText(this@Camera2Activity, "摄像头开启失败", Toast.LENGTH_SHORT).show()
        }
    }


    private fun takePreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            val previewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(mSurfaceHolder!!.surface)
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice!!.createCaptureSession(Arrays.asList(mSurfaceHolder!!.surface, mImageReader!!.surface), object : CameraCaptureSession.StateCallback() // ③
            {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    if (null == mCameraDevice) return
                    // 当摄像头已经准备好时，开始显示预览
                    mCameraCaptureSession = cameraCaptureSession
                    try {
                        // 自动对焦
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        // 打开闪光灯
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                        // 显示预览
                        val previewRequest = previewRequestBuilder.build()
                        mCameraCaptureSession!!.setRepeatingRequest(previewRequest, null, childHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }

                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@Camera2Activity, "配置失败", Toast.LENGTH_SHORT).show()
                }
            }, childHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
//


    }

    override fun onClick(v: View) {
        takePicture()

//        takePreview()
//        var timer2 = Handler()
//        timer2.postDelayed({
//            takePicture()
//
//        }, 3000)
    }

    private fun takePicture() {
        if (mCameraDevice == null) return
        // 创建拍照需要的CaptureRequest.Builder
        val captureRequestBuilder: CaptureRequest.Builder
        try {
            captureRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader!!.surface)
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            // 获取手机方向
            val rotation = windowManager.defaultDisplay.rotation
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
            //拍照
            val mCaptureRequest = captureRequestBuilder.build()
            mCameraCaptureSession!!.capture(mCaptureRequest, null, childHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    companion object {
        private val ORIENTATIONS = SparseIntArray()

        ///为了使照片竖直显示
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }
}