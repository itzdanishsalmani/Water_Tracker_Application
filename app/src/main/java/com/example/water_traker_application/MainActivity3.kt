package com.example.water_traker_application

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color.BLUE
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity3 : AppCompatActivity() {

    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: WaterLogAdapter
    private val waterLogs = ArrayList<WaterLog>()

    private lateinit var requiredMLTextView: TextView
    private lateinit var currentMLTextView: TextView
    private lateinit var circularProgressBar: CircularProgressBar
    private var currentML: Float = 0f
    private var requiredML: Float = 0f

    private var previousDate: String = ""

    // SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        requiredMLTextView = findViewById(R.id.requiredML)
        currentMLTextView = findViewById(R.id.currentML)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        // Retrieve requiredML from SharedPreferences
        requiredML = sharedPreferences.getFloat("requiredML", 0f)
        requiredMLTextView.text = String.format("%.0f ML", requiredML)

        circularProgressBar = findViewById(R.id.circularProgressBar)
        circularProgressBar.apply {
            progressMax = 100f
            progressBarWidth = 5f
            backgroundProgressBarWidth = 7f
            progressBarColor = BLUE
        }

        logRecyclerView = findViewById(R.id.logRecyclerView)
        logAdapter = WaterLogAdapter(waterLogs)
        logRecyclerView.adapter = logAdapter
        logRecyclerView.layoutManager = LinearLayoutManager(this)

        val addWaterButton = findViewById<ImageButton>(R.id.addWaterButton)
        addWaterButton.setOnClickListener {
            addWater(150f)
            showToast("+150 ML added")
        }

        val handler = Handler(Looper.getMainLooper())
        val checkDateRunnable = object : Runnable {
            override fun run() {
                resetRecyclerViewIfNeeded()
                handler.postDelayed(this, 60000) // Check every minute (adjust as needed)
            }
        }
        handler.post(checkDateRunnable)

        val testButton = findViewById<Button>(R.id.historyButton)
        testButton.setOnClickListener {
            val intent44 = Intent(this, MainActivity5::class.java)
            startActivity(intent44)
        }
    }

    override fun onBackPressed() {
        // Finish MainActivity3, removing it from the back stack
        finish()
    }

    private fun addWater(mlToAdd: Float) {
        GlobalScope.launch(Dispatchers.Main) {
            // Update currentML
            currentML += mlToAdd
            currentMLTextView.text = String.format("%.0f ML", currentML)

            // Calculate progress percentage and update circularProgressBar
            val progressPercentage = (currentML / requiredML) * 100
            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)

            // Log water in the background
            logWaterInBackground(mlToAdd)

            // Inside the addWater() function, after updating the currentML
            if (currentML >= requiredML) {
                val remainingML = requiredML - currentML
                val intent = Intent(this@MainActivity3, MainActivity5::class.java)
                intent.putExtra("isGoalCompleted", true)
                intent.putExtra("remainingML", remainingML)
                startActivity(intent)
            }
        }
    }

    private suspend fun logWaterInBackground(mlAdded: Float) = withContext(Dispatchers.Default) {
        val currentTime = getCurrentTime()
        val log = WaterLog(currentTime, mlAdded)
        waterLogs.add(log)

        withContext(Dispatchers.Main) {
            logAdapter.notifyDataSetChanged()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun resetRecyclerViewIfNeeded() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (currentDate != previousDate) {
            // Date has changed, reset the RecyclerView
            logAdapter.clearData()
            previousDate = currentDate
        }
    }
}
