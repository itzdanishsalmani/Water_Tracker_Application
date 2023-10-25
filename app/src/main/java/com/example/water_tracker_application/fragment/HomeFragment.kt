package com.example.water_tracker_application

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.google.gson.Gson
import com.google.common.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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

        requiredMLTextView = view.findViewById(R.id.requiredML)
        currentMLTextView = view.findViewById(R.id.currentML)

        sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        lastSavedDatePrefs = requireContext().getSharedPreferences("LastSavedDatePrefs", Context.MODE_PRIVATE)

        // Check if requiredML exists in SharedPreferences, otherwise use the default value
        requiredML = sharedPreferences.getFloat("requiredML", 0f)

        // Check if cupSize exists in SharedPreferences, otherwise use the default value
        cupSize = sharedPreferences.getFloat("cupSize", 150f)

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
            addWater(cupSize)  // Pass the cupSize to the addWater function
            showRandomQuote()
        }

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        configureFirestoreOfflinePersistence()

        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

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

        // Inside HomeFragment when navigating to SettingFragment
        val settingFragment = SettingFragment()
        val bundle = Bundle()
        bundle.putFloat("intakeGoal", requiredML)
        settingFragment.arguments = bundle


        return view
    }
    fun updateIntakeGoal(newIntakeGoal: Float) {
        requiredML = newIntakeGoal
        // Update the requiredML in shared preferences
        saveRequiredMLToSharedPreferences(requiredML)

        // Update the requiredMLTextView immediately
        requiredMLTextView.text = String.format("%.0f ml", requiredML)
    }

    private fun saveRequiredMLToSharedPreferences(requiredML: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("requiredML", requiredML)
        editor.apply()
    }


    private fun addWater(cupSize: Float) {
        GlobalScope.launch(Dispatchers.Main) {
            val mlToAdd = cupSize  // Use the provided cupSize
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
    fun updateCupSize(newCupSize: Float) {
        // Save the new cup size to SharedPreferences
        saveCupSizeToSharedPreferences(newCupSize)

        // Update the cupSize variable
        cupSize = newCupSize

        // Update the belowButton text to show the new cupSize
        val belowButton = view?.findViewById<TextView>(R.id.belowButton)
        belowButton?.text = String.format("%.0f ml", cupSize)
    }

    private fun saveCupSizeToSharedPreferences(newCupSize: Float) {
        val editor = sharedPreferences.edit()
        editor.putFloat("cupSize", newCupSize)
        editor.apply()
    }
}