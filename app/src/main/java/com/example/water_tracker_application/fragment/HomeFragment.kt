package com.example.water_tracker_application

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.google.gson.Gson
import com.google.common.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class HomeFragment : Fragment(), BackPressListener {

    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: WaterLogAdapter
    private val waterLogs = ArrayList<WaterLog>()

    private lateinit var requiredMLTextView: TextView
    private lateinit var currentMLTextView: TextView
    private lateinit var circularProgressBar: CircularProgressBar
    private var currentML: Float = 0f
    private var requiredML: Float = 0f

    private lateinit var previousDate: String

    private lateinit var currentDate: String

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null

    private val quotesArray: Array<String> by lazy {
        resources.getStringArray(R.array.quotes)
    }
    private var isButtonEnabled = true

    override fun onBackPressExitApp(): Boolean {
        // Implement the behavior for handling the back button press in this fragment
        // Return true to request the activity to exit the app
        // Return false to allow the default behavior
        return true // Add your logic here as needed
    }

    @SuppressLint("ScheduleExactAlarm")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        scheduleNotificationAlarm()

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        requiredMLTextView = view.findViewById(R.id.requiredML)
        currentMLTextView = view.findViewById(R.id.currentML)

        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val passedRequiredML = requireActivity().intent.getFloatExtra("requiredML", -1f)
        if (passedRequiredML != -1f) {
            requiredML = passedRequiredML
        } else {
            requiredML = sharedPreferences.getFloat("requiredML", 0f)
        }

        currentMLTextView.text = String.format("%.0f ", currentML)
        requiredMLTextView.text = String.format("%.0f ml", requiredML)

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
                isButtonEnabled = false
                addWater(150f)
                showRandomQuote()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val checkDateRunnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(checkDateRunnable)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configureFirestoreOfflinePersistence()

        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

        loadWaterLogsFromSharedPreferences()

        logRecyclerView = view.findViewById(R.id.logRecyclerView)
        logAdapter = WaterLogAdapter(waterLogs)
        logRecyclerView.adapter = logAdapter
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        fetchRequiredMLFromFirestore()
        fetchCurrentMLFromFirestore()

        return view
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleNotificationAlarm() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(requireContext(), MemoBroadcast::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val endTime = Calendar.getInstance()
        endTime.set(Calendar.HOUR_OF_DAY, 22)
        endTime.set(Calendar.MINUTE, 0)
        endTime.set(Calendar.SECOND, 0)

        while (calendar.before(endTime)) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_HOUR,
                pendingIntent
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }
    }

    private fun addWater(mlToAdd: Float) {
        val cupSize = sharedPreferences.getFloat("cupSize", 150f)

        GlobalScope.launch(Dispatchers.Main) {
            val log = WaterLog(getCurrentTime(), mlToAdd)
            logAdapter.notifyItemInserted(0)
            currentML += mlToAdd
            currentMLTextView.text = String.format("%.0f /", currentML)
            val progressPercentage = (currentML / requiredML) * 100
            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)
            logWaterInBackground(mlToAdd)
            updateUserFirestoreData()
            Handler().postDelayed({
                isButtonEnabled = true
            }, 1000)
        }
    }

    private fun showRandomQuote() {
        val random = Random
        val randomIndex = random.nextInt(quotesArray.size)
        val randomQuote = quotesArray[randomIndex]
        val quoteTextView = view?.findViewById<TextView>(R.id.quoteTextView)
        quoteTextView?.text = randomQuote
    }

    private suspend fun logWaterInBackground(mlAdded: Float) = withContext(Dispatchers.Default) {
        val currentTime = getCurrentTime()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val log = WaterLog(currentTime, mlAdded)
        waterLogs.add(log)
        withContext(Dispatchers.Main) {
            logAdapter.notifyDataSetChanged()
        }
        saveWaterLogsToSharedPreferences()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    private fun updateUserFirestoreDataForNewDate(newDate: String) {
        userEmail?.let { email ->
            currentML = 0f
            val data = hashMapOf(
                "currentML" to currentML,
                "requiredML" to requiredML,
                "currentDate" to newDate
            )
            firestore.collection("users")
                .document(email)
                .collection("dailyData")
                .add(data)
                .addOnSuccessListener {
                }
                .addOnFailureListener { e ->
                }
        }
    }

    private fun updateUserFirestoreData() {
        userEmail?.let { email ->
            val data = hashMapOf(
                "currentML" to currentML,
                "requiredML" to requiredML,
                "currentDate" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )
            firestore.collection("users")
                .document(email)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                }
                .addOnFailureListener { e ->
                }
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val documentPath = "users/$email/dailyData/$currentDate"
            firestore.document(documentPath)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                }
                .addOnFailureListener { e ->
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
                            val progressPercentage = (currentML / requiredML) * 100
                            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)
                        }
                    }
                }
                .addOnFailureListener { e ->
                }
        }
    }

    private fun configureFirestoreOfflinePersistence() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
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
        }
    }

    private fun checkForDateChangeAndResetData() {
        val newDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (newDate != currentDate) {
            waterLogs.clear()
            logAdapter.notifyDataSetChanged()
            updateUserFirestoreDataForNewDate(newDate)
            currentDate = newDate
        }
    }
}
