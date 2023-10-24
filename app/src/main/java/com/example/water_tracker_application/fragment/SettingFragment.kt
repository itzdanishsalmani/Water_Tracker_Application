package com.example.water_tracker_application

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.content.SharedPreferences
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private var currentWeight: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        sharedPreferences = requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val intakeGoalEditText = view.findViewById<EditText>(R.id.intakeGoalEditText)
        val goalConfirmButton = view.findViewById<Button>(R.id.goalConfirmButton)
        val currentWeightTextView = view.findViewById<TextView>(R.id.currentWeightTextView)
        val changeWeightEditText = view.findViewById<EditText>(R.id.changeWeightEditText)
        val weightConfirmButton = view.findViewById<Button>(R.id.weightConfirmButton)

        // Retrieve and display the user's weight from SharedPreferences
        currentWeight = sharedPreferences.getFloat("userWeight", 0f)
        currentWeightTextView.text = currentWeight.toString()

        // Handle goal confirmation
        goalConfirmButton.setOnClickListener {
            val newIntakeGoal = intakeGoalEditText.text.toString().toFloat()
            val homeFragment =
                parentFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            homeFragment?.updateIntakeGoal(newIntakeGoal)
            intakeGoalEditText.text.clear()
            Toast.makeText(context, "New goal has been set!", Toast.LENGTH_SHORT).show()
        }

        weightConfirmButton.setOnClickListener {
            val newWeight = changeWeightEditText.text.toString().toFloat()
            currentWeight = newWeight // Update the currentWeight variable
            val calculatedValue = newWeight * 35
            currentWeightTextView.text = currentWeight.toString() // Update the currentWeightTextView
            Toast.makeText(context, "New weight has been set!", Toast.LENGTH_SHORT).show()

            // Save the new weight in SharedPreferences
            val editor = sharedPreferences.edit()
            editor.putFloat("userWeight", newWeight)
            editor.apply()

            val homeFragment =
                parentFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            homeFragment?.updateIntakeGoal(calculatedValue)
            changeWeightEditText.text.clear()
        }

        return view
    }
}
