package com.grbl.cnc.ui.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.grbl.cnc.grbl.GrblState
import com.grbl.cnc.grbl.GrblStatus
import com.grbl.cnc.grbl.SpindleDirection

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

    // ✅ Spindle RPM
    private val _spindleRpm = MutableLiveData<Int>()
    val spindleRpm: LiveData<Int> = _spindleRpm

    // ✅ Coolant state
    private val _floodOn = MutableLiveData<Boolean>()
    val floodOn: LiveData<Boolean> = _floodOn

    private val _mistOn = MutableLiveData<Boolean>()
    val mistOn: LiveData<Boolean> = _mistOn

    private val _spindleDirection = MutableLiveData<SpindleDirection>()
    val spindleDirection: LiveData<SpindleDirection> = _spindleDirection

    fun updateStatus(status: GrblStatus) {
        _grblStatus.value = status
        _plannerAvailable.value = status.plannerAvailable
        _grblRunMode.value = mapState(status.state)

        // ✅ Ambil spindle dari status
        _spindleRpm.value = status.spindle

        // ✅ Coolant
        _floodOn.value = status.flood
        _mistOn.value = status.mist
        _spindleDirection.value = status.spindleDirection
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