package com.example.circleapp.ui.viewmodels

import android.os.Build
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BugReportViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    fun submitBugReport(description: String, onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid
        val bugReport = hashMapOf(
            "description" to description,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "timestamp" to System.currentTimeMillis(),
            "userId" to userId
        )

        db.collection("bugs")
            .add(bugReport)
            .addOnSuccessListener {
                onComplete()
            }
            .addOnFailureListener { 
                // Handle failure
            }
    }
}
