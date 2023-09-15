package com.example.water_traker_application

import android.os.Bundle
import android.widget.CalendarView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity5 : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var percentageTextView: TextView

    // Firebase
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String? = null // User's Gmail address
    private val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main5)

        calendarView = findViewById(R.id.calendarView)
        percentageTextView = findViewById(R.id.percentageTextView)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configure Firestore settings for offline persistence
        configureFirestoreOfflinePersistence()

        // Check if the user is authenticated
        if (firebaseAuth.currentUser != null) {
            userEmail = firebaseAuth.currentUser?.email
        }

        // Set the maximum date for the CalendarView to the current date
        calendarView.maxDate = Date().time

        // Set up the OnDateChangeListener for the CalendarView
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(year - 1900, month, dayOfMonth))

            // Check if the selected date is not in the future
            if (selectedDate <= currentDate) {
                // Fetch data from Firestore for the selected date
                fetchFirestoreData(selectedDate)
            } else {
                // Selected date is in the future, display a message or take appropriate action
                // For example, you can show a Toast message indicating that future dates cannot be selected
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

                        // Calculate and display the percentage
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

                        percentageTextView.text = "$percentage%"
                    } else {
                        // Data not found for the selected date, display 0%
                        percentageTextView.text = "0%"
                    }
                }
                .addOnFailureListener { e ->
                    // Handle errors here
                    percentageTextView.text = "Refresh"
                }
        }
    }

    private fun configureFirestoreOfflinePersistence() {
        // Enable Firestore offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings
    }
}
