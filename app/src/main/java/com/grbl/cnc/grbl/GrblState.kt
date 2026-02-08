package com.grbl.cnc.grbl

enum class GrblState {
    Idle,
    Run,
    Hold,
    Jog,
    Alarm,
    Homing,
    Unknown
}