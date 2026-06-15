package com.avex.ragraa.data

import android.util.Log
import com.avex.ragraa.sharedPreferences
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.UUID

/**
 * Handles anonymous upload and aggregation of course marks via Firebase Realtime Database.
 * No PII (roll number, name, etc.) is ever uploaded — only teacher name, course name,
 * and total marks for cross-section statistical analysis.
 */
object RealtimeDbRepository {
    private val db by lazy { Firebase.database }

    /** Stable anonymous ID per install, stored in SharedPreferences. */
    private val anonymousUserId: String
        get() {
            var id = sharedPreferences.getString("anonymousUserId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sharedPreferences.edit().putString("anonymousUserId", id).apply()
            }
            return id
        }

    /**
     * Uploads a single course's total marks for the current user.
     * Uses a deterministic key (userId + semId + courseHash) so that
     * refreshing marks overwrites the same node instead of creating duplicates.
     */
    fun uploadCourseMarks(
        teacherName: String,
        courseName: String,
        sectionName: String,
        totalObtained: Float,
        totalMarks: Float,
        semId: String
    ) {
        if (totalMarks <= 0f) return

        val sanitizedCourse = courseName.trim().lowercase().replace(".", "_").replace("#", "_")
            .replace("$", "_").replace("[", "_").replace("]", "_")
        val nodeId = "${anonymousUserId}_${semId}_${courseName.hashCode()}"
        val percentage = (totalObtained / totalMarks) * 100.0

        val data = mapOf(
            "totalObtained" to totalObtained.toDouble(),
            "totalMarks" to totalMarks.toDouble(),
            "percentage" to percentage,
            "courseName" to courseName,
            "sectionName" to sectionName,
            "semId" to semId,
            "timestamp" to ServerValue.TIMESTAMP
        )

        db.getReference("courses")
            .child(sanitizedCourse)
            .child("marks")
            .child(nodeId)
            .setValue(data)
            .addOnFailureListener { e ->
                Log.e("RealtimeDbRepo", "Failed to upload marks for $courseName", e)
            }
    }

    /**
     * Queries all marks entries for a given course.
     * Callback receives a map of Section Name -> List of Percentages.
     */
    fun getCourseStats(
        courseName: String,
        semId: String,
        callback: (Map<String, List<Float>>) -> Unit
    ) {
        val sanitizedCourse = courseName.trim().lowercase().replace(".", "_").replace("#", "_")
            .replace("$", "_").replace("[", "_").replace("]", "_")

        db.getReference("courses")
            .child(sanitizedCourse)
            .child("marks")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        callback(emptyMap())
                        return
                    }

                    val sectionMap = mutableMapOf<String, MutableList<Float>>()

                    snapshot.children.forEach { child ->
                        val itemSemId = child.child("semId").getValue(String::class.java)
                        if (itemSemId == semId) {
                            val percentage = child.child("percentage").getValue(Double::class.java)?.toFloat()
                            val sectionName = child.child("sectionName").getValue(String::class.java) ?: "Unknown"

                            if (percentage != null) {
                                if (!sectionMap.containsKey(sectionName)) {
                                    sectionMap[sectionName] = mutableListOf()
                                }
                                sectionMap[sectionName]!!.add(percentage)
                            }
                        }
                    }

                    callback(sectionMap)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("RealtimeDbRepo", "Failed to get stats for $courseName", error.toException())
                    callback(emptyMap())
                }
            })
    }
}
