package com.example.water_tracker_application

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FieldPath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class HistoryFragment : Fragment(), BackPressListener {

    private lateinit var calendarView: CalendarView
    private lateinit var percentageTextView: TextView
    private lateinit var weeklyAverageData: TextView
    private lateinit var monthlyAverageData: TextView

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null
    private val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onBackPressExitApp(): Boolean {
        // Implement the behavior for handling the back button press in this fragment
        // Return true to request the activity to exit the app
        // Return false to allow the default behavior
        return true // Add your logic here as needed
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        percentageTextView = view.findViewById(R.id.percentageTextView)
        weeklyAverageData = view.findViewById(R.id.weeklyAverageData)
        monthlyAverageData = view.findViewById(R.id.monthlyAverageData)

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
                calculateAndDisplayMonthlyAverage(selectedDate) // Call it when the user selects a date
            } else {
                Toast.makeText(requireContext(), "Future date cannot be selected", Toast.LENGTH_SHORT).show()
            }
        }

        // Set an initial value for the monthly average
        calculateAndDisplayMonthlyAverage(currentDate)

        return view
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
                        calculateAndDisplayMonthlyAverage(selectedDate) // Also update monthly average here
                    } else {
                        percentageTextView.text = "0%"
                    }
                }
                .addOnFailureListener { e ->
                    percentageTextView.text = "Refresh"
                }
        }
    }

    private fun calculateAndDisplayMonthlyAverage(selectedDate: String) {
        userEmail?.let { email ->
            // Extract the year and month from the selected date
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = sdf.parse(selectedDate)
            val calendar = Calendar.getInstance()
            calendar.time = parsedDate
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)

            // Calculate the start and end dates for the selected month
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }.time
            )

            val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                }.time
            )

            firestore.collection("users/$email/dailyData")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), startDate)
                .whereLessThanOrEqualTo(FieldPath.documentId(), endDate)
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