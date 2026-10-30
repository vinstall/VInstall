package com.vinstall.alwiz.model

data class CrashEntry(
    val time: String,
    val thread: String,
    val device: String,
    val exceptionType: String,
    val exceptionMessage: String,
    val report: String
)
