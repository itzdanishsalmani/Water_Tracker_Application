package com.example.water_traker_application

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class MainActivity5 : AppCompatActivity() {
    private lateinit var goalCompletionTextView: TextView

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main5)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Initialize Firebase Realtime Database
        database = FirebaseDatabase.getInstance()
        userRef = database.reference.child("users")

        // Initialize the TextView
        goalCompletionTextView = findViewById(R.id.goalCompletionTextView)

        // Get the value of isGoalCompleted from the intent
        val isGoalCompleted = intent.getBooleanExtra("isGoalCompleted", false)

        // Get the user's email from Firebase Authentication
        val userEmail = auth.currentUser?.email

        // Check if the user is authenticated and has an email
        if (userEmail != null) {
            // Replace dots in email to make a valid key
            val userKey = userEmail.replace(".", "_")

            // Store the goal completion status in Firebase under the user's key
            userRef.child(userKey).child("goalCompleted").setValue(isGoalCompleted)
        }

        // Set the text of goalCompletionTextView based on isGoalCompleted
        if (isGoalCompleted) {
            goalCompletionTextView.text = "Today's goal completed!"
        } else {
            val remainingML = intent.getFloatExtra("remainingML", 0f)
            goalCompletionTextView.text = "Remaining: ${remainingML.toInt()} ML"
        }
    }
}
