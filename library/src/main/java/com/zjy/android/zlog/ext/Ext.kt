package com.zjy.android.zlog.ext

import android.util.Log
import com.zjy.android.zlog.proto.LogOuterClass
import java.text.SimpleDateFormat
import java.util.Locale

private val zLogTimestampFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
}

private val zLogTAG by lazy {
    "ZLogAndroidSDK"
}

fun LogOuterClass.Log.printToConsole() {
    val level = when (logLevel) {
        LogOuterClass.Log.Level.DEBUG -> "DEBUG"
        LogOuterClass.Log.Level.VERBOSE -> "VERBOSE"
        LogOuterClass.Log.Level.WARN -> "WARN"
        LogOuterClass.Log.Level.ERROR -> "ERROR"
        else -> "INFO"
    }

    val logMessage = StringBuilder()
    logMessage.append("╔═══════════════════════════════════════════════════════════════════\n")
    logMessage.append("║ Sequence       : $sequence\n")
    logMessage.append("║ SystemVersion  : $systemVersion\n")
    logMessage.append("║ AppVersion     : $appVersion\n")
    logMessage.append("║ Timestamp      : ${zLogTimestampFormat.format(timestamp * 1000L)}\n")
    logMessage.append("║ LogLevel       : $level\n")
    logMessage.append("║ Identify       : $identify\n")
    logMessage.append("║ Tag            : $tag\n")
    logMessage.append("║ Message        : $msg\n")
    logMessage.append("╚═══════════════════════════════════════════════════════════════════\n")

    when (logLevel) {
        LogOuterClass.Log.Level.DEBUG -> Log.d(zLogTAG, logMessage.toString())
        LogOuterClass.Log.Level.VERBOSE -> Log.v(zLogTAG, logMessage.toString())
        LogOuterClass.Log.Level.WARN -> Log.w(zLogTAG, logMessage.toString())
        LogOuterClass.Log.Level.ERROR -> Log.e(zLogTAG, logMessage.toString())
        else -> Log.i(zLogTAG, logMessage.toString())
    }
}