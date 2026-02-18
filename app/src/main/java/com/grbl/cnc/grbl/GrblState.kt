package com.grbl.cnc.grbl

enum class GrblState {
    IDLE,
    RUN,
    HOLD,
    JOG,
    ALARM,
    HOME,
    UNKNOWN
}