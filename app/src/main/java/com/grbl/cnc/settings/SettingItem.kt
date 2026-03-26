package com.grbl.cnc.settings

data class SettingItem(
    val icon: Int,
    val title: String,
    var description: String,
    val key: String
)