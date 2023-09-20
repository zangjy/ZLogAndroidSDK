package com.zjy.android.zlog.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * 文件名：SPUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
@SuppressLint("ApplySharedPref")
class SPUtil private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun putString(key: String, value: String, useCommit: Boolean = false) {
        val editor = sharedPreferences.edit().putString(key, value)
        if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun remove(key: String, useCommit: Boolean = false) {
        val editor = sharedPreferences.edit().remove(key)
        if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun clear(useCommit: Boolean = false) {
        val editor = sharedPreferences.edit().clear()
        if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    companion object {
        private const val PREF_NAME = "zlog"

        private var instance: SPUtil? = null

        fun getInstance(): SPUtil {
            if (instance == null) {
                synchronized(SPUtil::class.java) {
                    if (instance == null) {
                        instance = SPUtil(App.getApplication())
                    }
                }
            }
            return instance!!
        }
    }
}