package com.example.water_traker_application

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity4 : AppCompatActivity() {
    private lateinit var weeklyCompletionTextView: TextView
    private lateinit var todayCompletionTextView: TextView
    private lateinit var todayTextView: TextView
    private lateinit var yesterdayTextView: TextView

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main4)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Initialize Firebase Realtime Database
        database = FirebaseDatabase.getInstance()
        userRef = database.reference.child("users")

        // Initialize TextViews
        weeklyCompletionTextView = findViewById(R.id.weeklyCompletionTextView)
        todayCompletionTextView = findViewById(R.id.sunday) // Assuming Sunday is today
        todayTextView = findViewById(R.id.sundayBelow)
        yesterdayTextView = findViewById(R.id.saturday) // Assuming Saturday is yesterday

        // Detect today's day of the week (e.g., Sunday is 1, Monday is 2, etc.)
        val currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)

        // Convert to Firebase day format (Sunday is 0, Monday is 1, etc.)
        val firebaseDayOfWeek = (currentDayOfWeek - 1).toString()

        // Fetch today's water intake percentage from Firebase
        userRef.child(auth.currentUser?.uid ?: "").child("weeklyGoals").child(firebaseDayOfWeek)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val todayGoal = snapshot.getValue(String::class.java)
                        todayCompletionTextView.text = todayGoal
                    } else {
                        todayCompletionTextView.text = "0%" // Default to 0% if data is not available
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle the error
                }
            })

        // Fetch yesterday's water intake percentage from Firebase
        userRef.child(auth.currentUser?.uid ?: "").child("weeklyGoals")
            .child((firebaseDayOfWeek.toInt() - 1).toString())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val yesterdayGoal = snapshot.getValue(String::class.java)
                        yesterdayTextView.text = yesterdayGoal
                    } else {
                        yesterdayTextView.text = "0%" // Default to 0% if data is not available
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle the error
                    Toast.makeText(this@MainActivity4, "Sorry! Server Error", Toast.LENGTH_SHORT).show()
                }
            })

        // Retrieve data from MainActivity3
        val isGoalCompleted = intent.getBooleanExtra("isGoalCompleted", false)
        val remainingML = intent.getFloatExtra("remainingML", 0f)

        // Use isGoalCompleted and remainingML as needed in your code
    }
}
