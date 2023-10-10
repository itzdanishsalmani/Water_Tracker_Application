package com.example.water_tracker_application

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

class NotificationOpen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_open)

        // Open the HomeFragment when the notification is tapped
        openHomeFragment()
    }

    private fun openHomeFragment() {
        // Create a new instance of the HomeFragment
        val homeFragment: Fragment = HomeFragment()

        // Replace the fragment_container with the HomeFragment
        val fragmentManager: FragmentManager = supportFragmentManager
        val transaction: FragmentTransaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_Container, homeFragment)
        transaction.commit()
    }
}