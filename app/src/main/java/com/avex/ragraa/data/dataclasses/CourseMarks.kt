package com.avex.ragraa.data.dataclasses

data class CourseMarks(
    val courseName: String,
    val teacherName: String = "Unknown",
    val courseMarks: List<Section>,
    var new: Boolean = false,
    val grandTotalExists: Boolean,
)