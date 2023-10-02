package com.example.water_tracker_application

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class HomeFragment : Fragment() {

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

    private val quotesArray: Array<String> by lazy {
        resources.getStringArray(R.array.quotes)
    }
    private var isButtonEnabled = true // for block clicked to addWaterButton immediately

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        //notification
        // Schedule the alarm to send a notification every minute
// Define the interval in hours
        val i = 2 // Change this to your desired interval in hours

// Calculate the interval in milliseconds
        val intervalMillis = i * 60 * 60 * 1000L // Convert hours to milliseconds

// Schedule the alarm to send a notification every i hours
        val intent = Intent(requireContext(), MemoBroadcast::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis(),
            intervalMillis,
            pendingIntent
        )

// Create a notification channel (if it doesn't exist)
        createNotificationChannel()

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        requiredMLTextView = view.findViewById(R.id.requiredML)
        currentMLTextView = view.findViewById(R.id.currentML)

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Check if requiredML was passed from MainActivity2
        val passedRequiredML = requireActivity().intent.getFloatExtra("requiredML", -1f)
        if (passedRequiredML != -1f) {
            requiredML = passedRequiredML
        } else {
            // Retrieve requiredML from SharedPreferences
            requiredML = sharedPreferences.getFloat("requiredML", 0f)
        }

        currentMLTextView.text = String.format("%.0f ", currentML)
        requiredMLTextView.text = String.format("%.0f ml", requiredML)

        //using defined color in color.xml
        val progressBarColor1 = ContextCompat.getColor(requireContext(), R.color.purple_500)

        circularProgressBar = view.findViewById(R.id.circularProgressBar)
        circularProgressBar.apply {
            progressMax = 100f
            progressBarWidth = 10f
            backgroundProgressBarWidth = 0f
            progressBarColor = progressBarColor1
        }

        logRecyclerView = view.findViewById(R.id.logRecyclerView)
        logAdapter = WaterLogAdapter(waterLogs)
        logRecyclerView.adapter = logAdapter
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        val addWaterButton = view.findViewById<ImageButton>(R.id.addWaterButton)
        addWaterButton.setOnClickListener {
            if (isButtonEnabled) {
                // Disable the button
                isButtonEnabled = false

                // Add water and handle completion
                addWater(150f)

                // Show a random quote
                showRandomQuote()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val checkDateRunnable = object : Runnable {
            override fun run() {
                resetRecyclerViewIfNeeded()
                handler.postDelayed(this, 60000) // Check every minute (adjust as needed)
            }
        }
        handler.post(checkDateRunnable)

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

        // Fetch currentML for the current date from Firestore
        fetchCurrentMLFromFirestore()

        // Restore currentML and water logs from SharedPreferences
        restoreCurrentMLFromSharedPreferences()
        loadWaterLogsFromSharedPreferences()

        return view
    }

    private fun addWater(mlToAdd: Float) {
        val cupSize = sharedPreferences.getFloat("cupSize", 150f) // Default to 150 ml if cupSize is not set

        GlobalScope.launch(Dispatchers.Main) {
            // Create a new WaterLog entry
            val log = WaterLog(getCurrentTime(), mlToAdd)

            // Update the RecyclerView
            logAdapter.notifyItemInserted(0)

            // Update currentML, progress bar, and save data as before
            currentML += mlToAdd
            currentMLTextView.text = String.format("%.0f /", currentML)
            val progressPercentage = (currentML / requiredML) * 100
            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)

            logWaterInBackground(mlToAdd)

            saveCurrentMLToSharedPreferences(currentML)
            saveWaterLogsToSharedPreferences()
            updateUserFirestoreData()

            // Use a Handler to re-enable the button after a delay (e.g., 1000 milliseconds)
            Handler().postDelayed({
                // Re-enable the button
                isButtonEnabled = true
            }, 1000) // 1000 milliseconds (1 second) delay
        }
    }

    private fun showRandomQuote() {
        // Get a random quote from the quotesArray
        val random = Random
        val randomIndex = random.nextInt(quotesArray.size)
        val randomQuote = quotesArray[randomIndex]

        // Display the quote (you can show it in a TextView)
        val quoteTextView = view?.findViewById<TextView>(R.id.quoteTextView)
        quoteTextView?.text = randomQuote
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
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun resetRecyclerViewIfNeeded() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (currentDate != previousDate) {
            // Date has changed, clear the waterLogs list
            waterLogs.clear()

            // Notify the RecyclerView adapter
            logAdapter.notifyDataSetChanged()

            // Save the new data to Firestore for the current date
            updateUserFirestoreDataForNewDate(currentDate)
            previousDate = currentDate
        }
    }

    private fun updateUserFirestoreDataForNewDate(newDate: String) {
        userEmail?.let { email ->
            // Update currentML to zero
            currentML = 0f

            // Define the data for the new date in Firestore
            val data = hashMapOf(
                "currentML" to currentML,
                "requiredML" to requiredML,
                "currentDate" to newDate
            )

            // Create a new Firestore document with a unique ID (Firestore will generate the ID)
            firestore.collection("users")
                .document(email)
                .collection("dailyData") // Create a subcollection for daily data
                .add(data) // Use add to create a new document with a unique ID
                .addOnSuccessListener {
                    // Data added successfully in Firestore
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                }
        }
    }

    private fun updateUserFirestoreData() {
        userEmail?.let { email ->
            // Define the data to update in Firestore for both locations
            val data = hashMapOf(
                "currentML" to currentML,
                "requiredML" to requiredML,
                "currentDate" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )

            // Update Firestore data in the "users" document
            firestore.collection("users")
                .document(email)
                .set(data, SetOptions.merge()) // Use set() with merge option to update specific fields
                .addOnSuccessListener {
                    // Data updated successfully in Firestore
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                }

            // Update Firestore data in the "dailyData" collection for the current date
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val documentPath = "users/$email/dailyData/$currentDate"
            firestore.document(documentPath)
                .set(data, SetOptions.merge()) // Use set() with merge option to update specific fields
                .addOnSuccessListener {
                    // Data updated successfully in Firestore for the current date
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
                            requiredMLTextView.text = String.format("%.0f ml", requiredML)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                }
        }
    }

    private fun fetchCurrentMLFromFirestore() {
        userEmail?.let { email ->
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val documentPath = "users/$email/dailyData/$currentDate"

            firestore.document(documentPath)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val currentMLFromFirestore = documentSnapshot.getDouble("currentML")
                        if (currentMLFromFirestore != null) {
                            currentML = currentMLFromFirestore.toFloat()
                            currentMLTextView.text = String.format("%.0f /", currentML)

                            // Update the circularProgressBar as well
                            val progressPercentage = (currentML / requiredML) * 100
                            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)
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
        currentMLTextView.text = String.format("%.0f /", currentML)

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

    private fun clearWaterLogsInSharedPreferences() {
        val editor = sharedPreferences.edit()
        editor.remove("waterLogs")
        editor.apply()
    }

    private fun saveWaterLogsToSharedPreferences() {
        val editor = sharedPreferences.edit()
        val waterLogsJson = Gson().toJson(waterLogs)
        editor.putString("waterLogs", waterLogsJson)
        editor.apply()
    }

    private fun loadWaterLogsFromSharedPreferences() {
        val waterLogsJson = sharedPreferences.getString("waterLogs", null)
        if (waterLogsJson != null) {
            val type = object : TypeToken<ArrayList<WaterLog>>() {}.type
            waterLogs.clear()
            waterLogs.addAll(Gson().fromJson(waterLogsJson, type))
            logAdapter.notifyDataSetChanged()
        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PASTICCINO"
            val description = "PASTICCINO'S CHANNEL"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("Notification", name, importance)
            channel.description = description

            val notificationManager =
                requireContext().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

}