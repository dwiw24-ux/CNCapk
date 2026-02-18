package com.grbl.cnc.ui.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.grbl.cnc.grbl.GrblState
import com.grbl.cnc.grbl.GrblStatus

class MainViewModel : ViewModel() {

    // Status GRBL
    private val _grblStatus = MutableLiveData<GrblStatus>()
    val grblStatus: LiveData<GrblStatus> = _grblStatus

    // Mode mesin
    private val _grblRunMode = MutableLiveData<GrblState>()
    val grblRunMode: LiveData<GrblState> = _grblRunMode

    // Planner buffer (jumlah slot kosong)
    private val _plannerAvailable = MutableLiveData<Int>()
    val plannerAvailable: LiveData<Int> = _plannerAvailable

    fun updateStatus(status: GrblStatus) {
        _grblStatus.value = status
        _plannerAvailable.value = status.plannerAvailable
        _grblRunMode.value = mapState(status.state)
    }

    private fun mapState(state: String): GrblState {
        return when {
            state.startsWith("Idle") -> GrblState.IDLE
            state.startsWith("Run") -> GrblState.RUN
            state.startsWith("Hold") -> GrblState.HOLD
            state.startsWith("Alarm") -> GrblState.ALARM
            state.startsWith("Jog") -> GrblState.JOG
            state.startsWith("Home") -> GrblState.HOME
            else -> GrblState.UNKNOWN
        }
    }
}