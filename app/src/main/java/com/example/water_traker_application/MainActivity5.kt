package com.example.water_traker_application

import android.os.Bundle
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity5 : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var percentageTextView: TextView
    private lateinit var weeklyAverageData: TextView
    private lateinit var monthlyAverageData: TextView

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null
    private val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main5)

        calendarView = findViewById(R.id.calendarView)
        percentageTextView = findViewById(R.id.percentageTextView)
        weeklyAverageData = findViewById(R.id.weeklyAverageData)
        monthlyAverageData = findViewById(R.id.monthlyAverageData)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        configureFirestoreOfflinePersistence()

        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

        calendarView.maxDate = Date().time

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(year - 1900, month, dayOfMonth))

            if (selectedDate <= currentDate) {
                fetchFirestoreData(selectedDate)
            } else {
                Toast.makeText(this, "Future date cannot be selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchFirestoreData(selectedDate: String) {
        userEmail?.let { email ->
            val documentPath = "users/$email/dailyData/$selectedDate"

            firestore.document(documentPath)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val currentML = documentSnapshot.getDouble("currentML") ?: 0.0
                        val requiredML = documentSnapshot.getDouble("requiredML") ?: 0.0

                        val percentage = if (requiredML > 0) {
                            val calculatedPercentage = (currentML / requiredML * 100).toInt()
                            if (calculatedPercentage > 100) {
                                100
                            } else {
                                calculatedPercentage
                            }
                        } else {
                            0
                        }

                        percentageTextView.text = "$percentage% completed"

                        calculateAndDisplayWeeklyAverage(selectedDate)
                        calculateAndDisplayMonthlyAverage()
                    } else {
                        percentageTextView.text = "0%"
                    }
                }
                .addOnFailureListener { e ->
                    percentageTextView.text = "Refresh"
                }
        }
    }

    private fun calculateAndDisplayMonthlyAverage() {
        userEmail?.let { email ->
            val calendar = Calendar.getInstance()
            calendar.time = Date()
            calendar.set(Calendar.DAY_OF_MONTH, 1) // Set to the 1st day of the current month
            val startDate =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            firestore.collection("users/$email/dailyData")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), startDate)
                .whereLessThanOrEqualTo(FieldPath.documentId(), currentDate)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val dailyPercentages = mutableListOf<Double>()
                    querySnapshot.documents.forEach { documentSnapshot ->
                        val currentML = documentSnapshot.getDouble("currentML") ?: 0.0
                        val requiredML = documentSnapshot.getDouble("requiredML") ?: 0.0

                        val dailyPercentage = if (requiredML > 0) {
                            val calculatedPercentage = (currentML / requiredML * 100).toInt()
                            if (calculatedPercentage > 100) {
                                100.0
                            } else {
                                calculatedPercentage.toDouble()
                            }
                        } else {
                            0.0
                        }

                        dailyPercentages.add(dailyPercentage)
                    }

                    if (dailyPercentages.isNotEmpty()) {
                        val monthlyAverage = dailyPercentages.average().toInt()
                        monthlyAverageData.text = "Monthly Average: $monthlyAverage%"
                    } else {
                        monthlyAverageData.text = "Monthly Average: 0%"
                    }
                }
                .addOnFailureListener { e ->
                    monthlyAverageData.text = "Monthly Average: Error"
                }
        }
    }

    private fun configureFirestoreOfflinePersistence() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }

    private fun calculateAndDisplayWeeklyAverage(selectedDate: String) {
        userEmail?.let { email ->
            val startDate = getStartOfWeek(selectedDate)
            val endDate = getEndOfWeek(selectedDate)

            firestore.collection("users/$email/dailyData")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), startDate)
                .whereLessThanOrEqualTo(FieldPath.documentId(), endDate)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val dailyPercentages = mutableListOf<Int>() // Use Int instead of Double
                    querySnapshot.documents.forEach { documentSnapshot ->
                        val currentML = documentSnapshot.getDouble("currentML") ?: 0.0
                        val requiredML = documentSnapshot.getDouble("requiredML") ?: 0.0

                        val dailyPercentage = if (requiredML > 0) {
                            val calculatedPercentage = (currentML / requiredML * 100).toInt()
                            if (calculatedPercentage > 100) {
                                100 // Cap the daily percentage at 100
                            } else {
                                calculatedPercentage
                            }
                        } else {
                            0
                        }

                        dailyPercentages.add(dailyPercentage)
                    }

                    if (dailyPercentages.isNotEmpty()) {
                        val weeklySum = dailyPercentages.sum()
                        val weeklyAverage = weeklySum / 7 // Divide by 7 to get a consistent weekly average
                        weeklyAverageData.text = "Weekly Average: ${weeklyAverage}%"
                    } else {
                        weeklyAverageData.text = "Weekly Average: 0%"
                    }
                }
                .addOnFailureListener { e ->
                    weeklyAverageData.text = "Weekly Average: Error"
                }
        }
    }

    private fun getStartOfWeek(date: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsedDate = dateFormat.parse(date)
        val calendar = Calendar.getInstance()
        calendar.time = parsedDate

        // Set the start day of the week to Monday
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        return dateFormat.format(calendar.time)
    }

    private fun getEndOfWeek(date: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsedDate = dateFormat.parse(date)
        val calendar = Calendar.getInstance()
        calendar.time = parsedDate

        // Set the start day of the week to Monday
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        return dateFormat.format(calendar.time)
    }

}