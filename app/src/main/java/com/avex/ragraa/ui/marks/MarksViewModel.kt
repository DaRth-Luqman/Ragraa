package com.avex.ragraa.ui.marks

import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.avex.ragraa.data.Datasource
import com.avex.ragraa.data.RealtimeDbRepository
import com.avex.ragraa.data.dataclasses.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TeacherStats(
    val max: Float = 0f,
    val min: Float = 0f,
    val avg: Float = 0f,
    val count: Int = 0,
    val rawSectionData: Map<String, List<Float>> = emptyMap(),
    val selectedSections: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val hasData: Boolean = false
)

data class MarksUiState(
    val currentCourse: Course? = null,
    val viewingAttendance: Boolean = false,
    val teacherStats: TeacherStats = TeacherStats()
)

open class MarksViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MarksUiState())
    val uiState: StateFlow<MarksUiState> = _uiState.asStateFlow()

    private var currentCourse: Course? = null
    private var viewingAttendance = false
    private var teacherStats = TeacherStats()

    lateinit var navController: NavHostController

    private fun updateUI() {
        _uiState.update {
            MarksUiState(currentCourse, viewingAttendance, teacherStats)
        }
    }

    fun cascadeUpdate(course: Course? = currentCourse) {
        if(course != null) {
            course.newMarks = false
            for(item in course.marks)
                item.new = false
        }
    }

    fun showCourse(course: Course?) {
        if (course == null && currentCourse != null) {
            cascadeUpdate(currentCourse)
        }
        currentCourse = course

        // Fetch class stats when a course is selected
        if (course != null) {
            val courseCode = course.name.substringBefore("-").trim()
            fetchCourseStats(courseCode)
        } else {
            teacherStats = TeacherStats()
        }

        updateUI()
    }

    private fun fetchCourseStats(courseName: String) {
        teacherStats = TeacherStats(isLoading = true)
        updateUI()

        RealtimeDbRepository.getCourseStats(courseName, Datasource.semId) { sectionData ->
            val allSections = sectionData.keys.toSet()
            teacherStats = TeacherStats(rawSectionData = sectionData, isLoading = false)
            recalculateStats(sectionData, allSections)
        }
    }

    fun toggleSection(section: String) {
        val currentSelected = teacherStats.selectedSections.toMutableSet()
        if (currentSelected.contains(section)) {
            // Prevent deselecting the last section to avoid empty states if possible, or allow it? Let's allow it.
            currentSelected.remove(section)
        } else {
            currentSelected.add(section)
        }
        recalculateStats(teacherStats.rawSectionData, currentSelected)
    }

    private fun recalculateStats(sectionData: Map<String, List<Float>>, selected: Set<String>) {
        if (selected.isEmpty()) {
            teacherStats = teacherStats.copy(
                max = 0f, min = 0f, avg = 0f, count = 0, 
                selectedSections = selected, hasData = false
            )
            updateUI()
            return
        }

        val allMarks = mutableListOf<Float>()
        for (sec in selected) {
            sectionData[sec]?.let { allMarks.addAll(it) }
        }

        if (allMarks.isEmpty()) {
            teacherStats = teacherStats.copy(
                max = 0f, min = 0f, avg = 0f, count = 0, 
                selectedSections = selected, hasData = false
            )
        } else {
            teacherStats = teacherStats.copy(
                max = allMarks.maxOrNull() ?: 0f,
                min = allMarks.minOrNull() ?: 0f,
                avg = allMarks.average().toFloat(),
                count = allMarks.size,
                selectedSections = selected,
                hasData = true
            )
        }
        updateUI()
    }

    fun showAttendance() {
        viewingAttendance = !viewingAttendance
        updateUI()
    }
}
