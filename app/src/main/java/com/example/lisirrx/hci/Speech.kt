package com.example.lisirrx.hci

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.TextView
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset



/**
 * Created by user on 2017/6/18.
 */

class Speech {

    lateinit var outputFile : String

    var httpClient : OkHttpClient
    var mediaRecorder : MediaRecorder?
    var mediaPlayer : MediaPlayer?



    init {

        mediaRecorder = MediaRecorder()
        mediaPlayer = MediaPlayer()
        httpClient = OkHttpClient()



    }

    public fun startRecord(file: String){

        try {


        if(mediaRecorder == null){
            mediaRecorder = MediaRecorder()
        }

        outputFile = file
        mediaRecorder?.setAudioSamplingRate(8000)
        mediaRecorder?.setAudioChannels(1)
        mediaRecorder?.setOutputFile(outputFile)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)


        try {
            mediaRecorder?.prepare()
        } catch (e: IOException) {
            Log.e("THIS", "prepare() failed")
        }


        mediaRecorder?.start()

    } catch (e: IllegalStateException){
        mediaRecorder?.release()
        }
    }

    public fun stopRecord(){
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null

        var file = File(outputFile)
        var id = android.os.Build.SERIAL


        val params = JSONObject()
        params.put("format", "amr")
        params.put("rate", 8000)
        params.put("channel", "1")
        params.put("token", "24.f4906ec0e19726fa5c258e0862ce111b.2592000.1500377332.282335-9779733")
        params.put("cuid", id)
        params.put("len", file.length())

        var arr =  Base64.encode(loadFile(file), Base64.DEFAULT)
        var str : String = ""
        val cs = Charset.forName("UTF-8")
        val bb = ByteBuffer.allocate(arr.size)
        bb.put(arr)
        bb.flip()
        val cb = cs.decode(bb)


        params.put("speech", cb)
        var body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), params.toString())
        var t = params.toString()

        var request = Request.Builder()
                .url("http://vop.baidu.com/server_api")
                .header("Content-Type", "application/json")
                .header("Content-length", file.length().toString())
                .post(body)
                .build()

        var re = request.body()

        var newThread = Thread{
            var response = httpClient.newCall(request).execute()
            var result = JSONObject(response.body()?.string())


            Log.d("SPEECH", result.toString())

        }
        newThread.start()
    }

    fun loadFile(file: File): ByteArray{
        val ins = FileInputStream(file)
        val len = file.length()
        var bytes = ByteArray(len.toInt())
        var offset = 0
        var numRead = 0
        while ((offset < bytes.size) && numRead >=0){
            numRead = ins.read(bytes, offset, bytes.size - offset)
            offset += numRead
        }

        if (offset < bytes.size){
            ins.close()
            throw IOException()
        }

        ins.close()
        return bytes



    }


}