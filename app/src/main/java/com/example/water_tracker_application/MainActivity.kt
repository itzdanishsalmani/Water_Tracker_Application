package com.example.water_tracker_application

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        // Check if the app has been launched before
        val isFirstLaunch = sharedPreferences.getBoolean("isFirstLaunch", true)

        if (isFirstLaunch) {
            // First launch, open MainActivity2
            val startButton = findViewById<Button>(R.id.startButton)
            startButton.setOnClickListener {
                val intent = Intent(this, MainActivity2::class.java)
                startActivity(intent)
                finish() // This finishes (closes) MainActivity
            }

            // Update SharedPreferences to indicate that the app has been launched
            val editor = sharedPreferences.edit()
            editor.putBoolean("isFirstLaunch", false)
            editor.apply()
        } else {
            // Not the first launch, go directly to MainActivity2
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
            finish() // This finishes (closes) MainActivity
        }
    }
}