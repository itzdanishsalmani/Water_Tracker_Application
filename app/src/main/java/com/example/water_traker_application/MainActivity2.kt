package com.example.water_traker_application

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity2 : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Check if requiredML has been previously saved
        val savedRequiredML = sharedPreferences.getFloat("requiredML", -1f)

        if (savedRequiredML != -1f) {
            // requiredML has been previously saved, proceed to MainActivity3
            val userWeight = sharedPreferences.getFloat("userWeight", -1f)
            val intent = Intent(this, MainActivity3::class.java)
            intent.putExtra("calculatedValue", userWeight * 35)
            startActivity(intent)
            finish() // This finishes (closes) MainActivity2
        }

        val nextButton2 = findViewById<Button>(R.id.nextButton2)
        nextButton2.setOnClickListener {
            val enterWeight = findViewById<EditText>(R.id.enterWeight)
            val weightString = enterWeight.text.toString()

            if (weightString.isNotEmpty()) {
                val weight = weightString.toDouble()
                val calculatedValue = weight * 35

                // Save the weight in SharedPreferences
                val editor = sharedPreferences.edit()
                editor.putFloat("userWeight", weight.toFloat())
                editor.apply()

                // Calculate and save requiredML in SharedPreferences
                val requiredML = calculatedValue.toFloat()
                editor.putFloat("requiredML", requiredML)
                editor.apply()

                val intent = Intent(this,MainActivity3::class.java)
                intent.putExtra("calculatedValue", calculatedValue)
                startActivity(intent)

                // Finish MainActivity2, removing it from the back stack
                finish()
            } else {
                Toast.makeText(this, "Please enter the weight.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
