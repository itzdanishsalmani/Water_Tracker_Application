package com.example.water_tracker_application

import androidx.fragment.app.DialogFragment
import android.os.Bundle
import android.app.AlertDialog
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

class ExitConfirmationDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_exit_confirmation_dialog, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireActivity())
            .setTitle("Exit Confirmation")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("OK") { _, _ ->
                // Handle the "OK" button click (exit the app)
                activity?.finish()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // Handle the "Cancel" button click (dismiss the dialog)
                dialog.dismiss()
            }
            .create()
    }
}