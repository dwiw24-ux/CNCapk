package com.grbl.cnc.grbl

data class GrblStatus(
    val state: String,
    val mposX: Double,
    val mposY: Double,
    val mposZ: Double,

    val wposX: Double,
    val wposY: Double,
    val wposZ: Double,

    val feed: Int,
    val spindle: Int,
    val pin: String?,

    val plannerAvailable: Int,
    val rxAvailable: Int,

    val flood: Boolean,
    val mist: Boolean,
    val spindleDirection: SpindleDirection,

    val ovFeed: Int,
    val ovRapid: Int,
    val ovSpindle: Int
)
