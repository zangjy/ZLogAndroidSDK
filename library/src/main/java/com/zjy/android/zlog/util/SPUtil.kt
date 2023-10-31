package com.zjy.android.zlog.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.zjy.android.zlog.constant.SPConstant

/**
 * 文件名：SPUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
@SuppressLint("ApplySharedPref")
class SPUtil private constructor(context: Context, fileName: String) {

    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
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

    fun putLong(key: String, value: Long, useCommit: Boolean = false) {
        val editor = sharedPreferences.edit().putLong(key, value)
        if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPreferences.getLong(key, defaultValue)
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
        private val instances = mutableMapOf<String, SPUtil>()

        fun getInstance(fileName: String = SPConstant.DEFAULT_SP_NAME): SPUtil {
            synchronized(SPUtil::class.java) {
                if (!instances.containsKey(fileName)) {
                    instances[fileName] = SPUtil(App.getApplication(), fileName)
                }
            }
            return instances[fileName]!!
        }
    }
}