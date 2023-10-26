package com.example.water_tracker_application

import android.content.*
import java.util.*
import android.widget.*
import android.view.*
import kotlinx.coroutines.*
import androidx.recyclerview.widget.*
import com.google.firebase.firestore.*
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.google.gson.Gson
import com.google.common.reflect.TypeToken
import java.text.SimpleDateFormat
import kotlin.random.Random

class HomeFragment : Fragment(), BackPressListener {

    // Views and variables
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: WaterLogAdapter
    private val waterLogs = ArrayList<WaterLog>()

    private lateinit var requiredMLTextView: TextView
    private lateinit var currentMLTextView: TextView
    private lateinit var circularProgressBar: CircularProgressBar
    private var currentML: Float = 0f
    private var requiredML: Float = 0f

    private var lastSavedDate: String = ""
    private var cupSize: Float = 150f  // Default cup size

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var lastSavedDatePrefs: SharedPreferences

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null

    private val quotesArray: Array<String> by lazy {
        resources.getStringArray(R.array.quotes)
    }

    override fun onBackPressExitApp(): Boolean {
        return true // Add your logic here as needed
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views and load data from SharedPreferences
        requiredMLTextView = view.findViewById(R.id.requiredML)
        currentMLTextView = view.findViewById(R.id.currentML)

        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        lastSavedDatePrefs = requireContext().getSharedPreferences("LastSavedDatePrefs", Context.MODE_PRIVATE)

        // Check if requiredML exists in SharedPreferences, otherwise use the default value
        requiredML = sharedPreferences.getFloat("requiredML", 0f)

        // Check if cupSize exists in SharedPreferences, otherwise use the default value
        cupSize = sharedPreferences.getFloat("cupSize", 150f)

        // Load requiredML from intent (if passed)
        val passedRequiredML = requireActivity().intent.getFloatExtra("requiredML", -1f)
        if (passedRequiredML != -1f) {
            requiredML = passedRequiredML
        } else {
            requiredML = sharedPreferences.getFloat("requiredML", 0f)
        }

        // Set requiredML and cupSize in TextViews
        currentMLTextView.text = String.format("%.0f ", currentML)
        requiredMLTextView.text = String.format("%.0f ml", requiredML)

        // Initialize circular progress bar
        val progressBarColor1 = ContextCompat.getColor(requireContext(), R.color.purple_500)
        circularProgressBar = view.findViewById(R.id.circularProgressBar)
        circularProgressBar.apply {
            progressMax = 100f
            progressBarWidth = 8f
            backgroundProgressBarWidth = 0f
            progressBarColor = progressBarColor1
        }

        // Initialize RecyclerView for water logs
        logRecyclerView = view.findViewById(R.id.logRecyclerView)
        logAdapter = WaterLogAdapter(waterLogs)
        logRecyclerView.adapter = logAdapter
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set the belowButton text to the cupSize
        val belowButton = view.findViewById<TextView>(R.id.belowButton)
        belowButton.text = String.format("%.0f ml", cupSize)

        // Set a click listener for the "addWaterButton"
        val addWaterButton = view.findViewById<ImageButton>(R.id.addWaterButton)
        addWaterButton.setOnClickListener {
            addWater(cupSize)  // Pass the cupSize to the addWater function
            showRandomQuote()
        }

        // Initialize Firebase Authentication and Firestore
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configure Firestore offline persistence
        configureFirestoreOfflinePersistence()

        // Get the user's email if logged in
        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

        // Check the current date and handle data accordingly
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSavedDate = lastSavedDatePrefs.getString("lastSavedDate", "")

        if (currentDate != lastSavedDate) {
            lastSavedDatePrefs.edit().putString("lastSavedDate", currentDate).apply()
            currentML = 0f
            logAdapter.clearData()
            updateUserFirestoreDataForNewDate(currentDate)
        } else {
            loadWaterLogsFromSharedPreferences()
        }

        fetchCurrentMLFromFirestore()

        return view
    }

    // Function to update the intake goal and save it to SharedPreferences
    fun updateIntakeGoal(newIntakeGoal: Float) {
        requiredML = newIntakeGoal
        saveRequiredMLToSharedPreferences(requiredML)
        requiredMLTextView.text = String.format("%.0f ml", requiredML)
    }

    private fun saveRequiredMLToSharedPreferences(requiredML: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("requiredML", requiredML)
        editor.apply()
    }

    // Function to add water to the current ML and update the UI
    private fun addWater(cupSize: Float) {
        GlobalScope.launch(Dispatchers.Main) {
            val mlToAdd = cupSize
            var log = WaterLog(getCurrentTime(), mlToAdd)
            logAdapter.notifyItemInserted(0)
            currentML += mlToAdd
            currentMLTextView.text = String.format("%.0f /", currentML)
            val progressPercentage = (currentML / requiredML) * 100
            circularProgressBar.setProgressWithAnimation(progressPercentage, 500)
            logWaterInBackground(mlToAdd)
            updateUserFirestoreData()
            Toast.makeText(requireContext(), "$mlToAdd ml added", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to display a random quote
    private fun showRandomQuote() {
        val random = Random
        val randomIndex = random.nextInt(quotesArray.size)
        val randomQuote = quotesArray[randomIndex]
        val quoteTextView = view?.findViewById<TextView>(R.id.quoteTextView)
        quoteTextView?.text = randomQuote
    }

    // Function to log water consumption in the background
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

    // Function to get the current time
    private fun getCurrentTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    // Function to update Firestore data for a new date
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

    // Function to update Firestore data
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

    // Function to fetch the current ML from Firestore
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

    // Function to configure Firestore offline persistence
    private fun configureFirestoreOfflinePersistence() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }

    // Function to save water logs to SharedPreferences
    private fun saveWaterLogsToSharedPreferences() {
        val editor = sharedPreferences.edit()
        val waterLogsJson = Gson().toJson(waterLogs)
        editor.putString("waterLogs", waterLogsJson)
        editor.apply()
    }

    // Function to load water logs from SharedPreferences
    private fun loadWaterLogsFromSharedPreferences() {
        val waterLogsJson = sharedPreferences.getString("waterLogs", null)
        if (waterLogsJson != null) {
            val type = object : TypeToken<ArrayList<WaterLog>>() {}.type
            waterLogs.clear()
            waterLogs.addAll(Gson().fromJson(waterLogsJson, type))
        }
    }

    // Function to update the cup size and save it to SharedPreferences
    fun updateCupSize(newCupSize: Float) {
        saveCupSizeToSharedPreferences(newCupSize)
        cupSize = newCupSize
        val belowButton = view?.findViewById<TextView>(R.id.belowButton)
        belowButton?.text = String.format("%.0f ml", cupSize)
    }

    // Function to save cup size to SharedPreferences
    private fun saveCupSizeToSharedPreferences(newCupSize: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("cupSize", newCupSize)
        editor.apply()
    }

    // Function to load cup size from SharedPreferences
    private fun loadCupSizeFromSharedPreferences(): Float {
        return sharedPreferences.getFloat("cupSize", 150f) // Use the default value if not set
    }
}
