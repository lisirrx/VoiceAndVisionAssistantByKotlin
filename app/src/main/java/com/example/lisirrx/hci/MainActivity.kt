package com.example.lisirrx.hci

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.ContactsContract
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.backgroundColor
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.provider.SyncStateContract.Helpers.update
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.AndroidRuntimeException
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.VoiceRecognitionService
import com.example.lisirrx.hci.Md5.getMD5Str
import org.json.JSONException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.HashMap

class MainActivity : AppCompatActivity() {
    lateinit var speech : Speech
    var btnCnt : Int = 1
    private val TAG = "Main"
    private lateinit var vibrator : Vibrator
    private lateinit var mWpEventManager: EventManager

    var contactDic = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var me = MediaPlayer()
        me.release()
        var mer = MediaRecorder()
        mer.release()

        var button_vision = findViewById(R.id.button_vision) as Button
        var button_speech = findViewById(R.id.button_speech) as Button



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("THIS", "Permission Fault")
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf("" + Manifest.permission.RECORD_AUDIO), 1)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf("" + Manifest.permission.READ_CONTACTS), 1)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf("" + Manifest.permission.CALL_PHONE), 1)
        }

        speech = Speech()
        var filePath = cacheDir.absolutePath + "tempr.amr"


        button_vision.setOnClickListener {
           var intent = Intent(MainActivity@this, Camera2Activity::class.java)
            startActivity(intent)
        }

        button_speech.setOnClickListener {
            val intent = Intent("com.baidu.action.RECOGNIZE_SPEECH")
            intent.putExtra("grammar", "assets:///baidu_speech_grammar.bsg") // 设置离线的授权文件(离线模块需要授权), 该语法可以用自定义语义工具生成, 链接http://yuyin.baidu.com/asr#m5
            //intent.putExtra("slot-data", your slots); // 设置grammar中需要覆盖的词条,如联系人名
            startActivityForResult(intent, 1)

            intent.putExtra("license-file-path", Environment.getExternalStorageDirectory().path + "temp_license_2017-06-19")
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(100)
            vibrator.vibrate(pattern, -1)
            MainActivity.text2Voice(this@MainActivity, "开始")

        }


        text2Voice(this@MainActivity , "你好啊, 请点击上半屏幕让我为您看看面前有什么，点击下半屏幕请告诉我您需要我帮您干什么。")

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            val results = data.extras
            val results_recognition = results.getStringArrayList("results_recognition")

            for (re in results_recognition){
                if ("相机" in re){
                    var intent = Intent(MainActivity@this, Camera2Activity::class.java)
                    startActivity(intent)
                }
            }

        }
    }
    override fun onResume() {
        super.onResume()

        // 唤醒功能打开步骤
        // 1) 创建唤醒事件管理器
        mWpEventManager = EventManagerFactory.create(this@MainActivity, "wp")

        // 2) 注册唤醒事件监听器
        mWpEventManager.registerListener(EventListener { name, params, data, offset, length ->
            Log.d(TAG, String.format("event: name=%s, params=%s", name, params))
            try {
                val json = JSONObject(params)
                if ("wp.data" == name) { // 每次唤醒成功, 将会回调name=wp.data的时间, 被激活的唤醒词在params的word字段
                    val word = json.getString("word")

                    if("相机" in word || "镜头" in word){
                        var intent = Intent(MainActivity@this, Camera2Activity::class.java)
                        startActivity(intent)
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

    override fun onPause() {
        super.onPause()
        // 停止唤醒监听
        mWpEventManager.send("wp.stop", null, null, 0, 0)
    }

    private fun call(name : String){
        var cursor : Cursor? = null
        try {

            cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(""),"", arrayOf(""), "")
            if (cursor != null){
                while (cursor.moveToNext()){
                    var name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))

                    var number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    contactDic[name] = number
                }

            }
        } catch (e: Exception){
            e.printStackTrace()
        } finally {
           cursor?.close()

        }

        var intent = Intent(Intent.ACTION_CALL)
        var num : String? = contactDic.get(name)
        if (num != null) {
            intent.setData(Uri.parse("tel:" + contactDic.get(name)))
            try {
                startActivity(intent)
            } catch (e : SecurityException){
                e.printStackTrace()
            }
        }
    }
    companion object {
        public fun text2Voice(context: Context, string: String) {
            var client = OkHttpClient()
            var id = Build.SERIAL

            var request = Request.Builder()
                    .url("http://tsn.baidu.com/text2audio?spd=5&tex={$string}&lan=zh&cuid={$id}&ctp=1&tok=24.f4906ec0e19726fa5c258e0862ce111b.2592000.1500377332.282335-9779733")
                    .get()
                    .build()

            var result: ByteArray?

            var newThread = Thread {
                var response = client.newCall(request).execute()
                result = response.body()?.bytes()

                (context as Activity).runOnUiThread {
                    val tempWav = File(context.cacheDir.toString() + "temp.mp3")
                    if (!tempWav.exists())
                        tempWav.createNewFile()
                    val fos = FileOutputStream(tempWav)
                    fos.write(result)
                    fos.flush()
                    fos.close()
                    val mediaPlayer = MediaPlayer()
                    val fis = FileInputStream(tempWav)
                    mediaPlayer.setDataSource(fis.getFD())
                    mediaPlayer.prepare()
                    mediaPlayer.start()

                    mediaPlayer.setOnCompletionListener {
                        Log.d("THIS", "Relaese")
                        mediaPlayer.release()
                    }
                }

            }
            newThread.start()
        }
    }



}
