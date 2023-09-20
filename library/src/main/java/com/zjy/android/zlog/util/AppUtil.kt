package com.zjy.android.zlog.util

/**
 * 文件名：AppUtils
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
object AppUtil {
    fun getAppVersionName(): String {
        return try {
            val pm = App.getApplication().packageManager
            val pi = pm.getPackageInfo(App.getApplication().packageName, 0)
            pi?.versionName ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}