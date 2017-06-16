package com.example.lisirrx.hci

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var button_vision : Button = findViewById(R.id.button_vision) as Button
        var button_sound = findViewById(R.id.button_sound) as TextView



        button_vision.setOnClickListener {
           var intent = Intent(MainActivity@this, Camera2Activity::class.java)
            startActivity(intent)
        }
    }
}
