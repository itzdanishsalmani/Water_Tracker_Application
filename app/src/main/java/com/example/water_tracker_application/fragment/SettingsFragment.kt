package com.example.water_tracker_application

import android.content.*
import android.view.*
import android.widget.*
import android.os.Bundle
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    private var currentWeight: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        sharedPreferences = requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val intakeGoalEditText = view.findViewById<EditText>(R.id.intakeGoalEditText)
        val goalConfirmButton = view.findViewById<Button>(R.id.goalConfirmButton)
        val currentWeightTextView = view.findViewById<TextView>(R.id.currentWeightTextView)
        val changeWeightEditText = view.findViewById<EditText>(R.id.changeWeightEditText)
        val weightConfirmButton = view.findViewById<Button>(R.id.weightConfirmButton)

        val changeCupEditText = view.findViewById<EditText>(R.id.changeCupEditText)
        val cupConfirmButton = view.findViewById<Button>(R.id.cupConfirmButton)

        // Retrieve and display the user's weight from SharedPreferences
        currentWeight = sharedPreferences.getFloat("userWeight", 0f)
        currentWeightTextView.text = currentWeight.toString()

        cupConfirmButton.setOnClickListener {
            val newCupSize = changeCupEditText.text.toString().toFloat()
            val homeFragment =
                parentFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            homeFragment?.updateCupSize(newCupSize)
            changeCupEditText.text.clear()
            Toast.makeText(context, "Cup size has been updated!", Toast.LENGTH_SHORT).show()
        }

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
            currentWeightTextView.text =
                currentWeight.toString() // Update the currentWeightTextView
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
