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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
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

    // Firebase
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null // User's Gmail address

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

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configure Firestore settings for offline persistence
        configureFirestoreOfflinePersistence()

        // Check if the user is authenticated
        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

        // Fetch requiredML from Firestore
        fetchRequiredMLFromFirestore()

        // Restore currentML from SharedPreferences
        restoreCurrentMLFromSharedPreferences()
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

            // Save currentML to SharedPreferences
            saveCurrentMLToSharedPreferences(currentML)

            // Update Firestore data
            updateUserFirestoreData()
        }
    }

    private suspend fun logWaterInBackground(mlAdded: Float) = withContext(Dispatchers.Default) {
        val currentTime = getCurrentTime()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Create a WaterLog object
        val log = WaterLog(currentTime, mlAdded)

        // Add the log to the RecyclerView
        waterLogs.add(log)

        // Notify the RecyclerView adapter
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

    private fun updateUserFirestoreData() {
        userEmail?.let { email ->
            // Define the data to update in Firestore
            val data = hashMapOf<String, Any>(
                "currentML" to currentML,
                "requiredML" to requiredML
            )

            // Update Firestore with the user's Gmail address as the document ID
            firestore.collection("users")
                .document(email)
                .update(data) // Use update() instead of set() to update specific fields
                .addOnSuccessListener {
                    // Data updated successfully in Firestore
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                }
        }
    }

    private fun fetchRequiredMLFromFirestore() {
        userEmail?.let { email ->
            firestore.collection("users")
                .document(email)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val requiredMLFromFirestore = documentSnapshot.getDouble("requiredML")
                        if (requiredMLFromFirestore != null) {
                            requiredML = requiredMLFromFirestore.toFloat()
                            requiredMLTextView.text = String.format("%.0f ML", requiredML)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                }
        }
    }

    private fun saveCurrentMLToSharedPreferences(currentML: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("currentML", currentML)
        editor.apply()
    }

    private fun restoreCurrentMLFromSharedPreferences() {
        currentML = sharedPreferences.getFloat("currentML", 0f)
        currentMLTextView.text = String.format("%.0f ML", currentML)

        // Update the circularProgressBar as well
        val progressPercentage = (currentML / requiredML) * 100
        circularProgressBar.setProgressWithAnimation(progressPercentage, 500)
    }

    private fun configureFirestoreOfflinePersistence() {
        // Enable Firestore offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }
}
