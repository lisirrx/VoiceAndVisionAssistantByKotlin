package com.example.lisirrx.hci

import android.Manifest
import android.content.Context
import android.content.Intent
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
import android.opengl.Visibility
import android.os.*
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.AndroidRuntimeException
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import org.jetbrains.anko.cameraManager
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*
import com.google.gson.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Type

/**
 * Created by lisirrx on 17-6-16.
 */
class Camera2Activity : AppCompatActivity(), View.OnClickListener {
    private lateinit var mWpEventManager: EventManager
    private lateinit var picture : Bitmap

    private var requestFlag = false



    private var mSurfaceView: SurfaceView? = null
    private var mSurfaceHolder: SurfaceHolder? = null
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


    override fun onResume() {
        super.onResume()
        mWpEventManager = EventManagerFactory.create(this@Camera2Activity, "wp")

        // 2) 注册唤醒事件监听器
        mWpEventManager.registerListener(EventListener { name, params, data, offset, length ->
            Log.d("Camera", String.format("event: name=%s, params=%s", name, params))
            try {
                val json = JSONObject(params)
                if ("wp.data" == name) {
                    val word = json.getString("word")
                    if("拍照" in word || "照相" in word){
                        takePicture()
                    }

                } else if ("wp.exit" == name) {
                }
            } catch (e: JSONException) {
                throw AndroidRuntimeException(e)
            }
        })

        // 3) 通知唤醒管理器, 启动唤醒功能
        val params = HashMap<String, String>()
        params.put("kws-file", "assets:///WakeUp.bin")
        mWpEventManager.send("wp.start", JSONObject(params).toString(), null, 0, 0)

    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vision)

        initVIew()
    }



    private fun initVIew() {
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
            val image = reader.acquireLatestImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)//由缓冲区存入字节数组
            picture= BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            makeRequest(picture)


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
        if (!requestFlag){
            takePicture()
        }

    }

    private fun takePicture() {

        requestFlag = true

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

    private fun makeRequest(bitmap: Bitmap){


        var file = File(cacheDir, "cache.jpg")
        val url = "https://westcentralus.api.cognitive.microsoft.com/vision/v1.0/describe"

        try {
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
        } catch (e:IOException){
            e.printStackTrace()
        }

        try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e:FileNotFoundException) {
            e.printStackTrace()
        }
        val MEDIA_TYPE = MediaType.parse("application/octet-stream");
        val client = OkHttpClient()
        var body = RequestBody.create(MEDIA_TYPE, file)
        var request = Request.Builder()
                .header("Ocp-Apim-Subscription-Key", "3b7708792daf4aeaaff74b9b3d474a5e")
                .header("Content-Type", "multipart/form-data")
                .url(url)
                .post(body)
                .build();

        MainActivity.text2Voice(this@Camera2Activity, "正在识别中，稍后")

        var newThread = Thread{
            var response = client.newCall(request).execute()
            runOnUiThread{
                var result = JSONObject(response.body()?.string().toString())
                var t = ((result.getJSONObject("description").get("captions") as JSONArray)[0] as  JSONObject).get("text") as String

                MainActivity.text2Voice(this@Camera2Activity, t)
                requestFlag = false
            }
        }
        newThread.start()


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

    override fun onPause() {
        super.onPause()
        // 停止唤醒监听
        mWpEventManager.send("wp.stop", null, null, 0, 0)
    }
}