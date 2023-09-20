package com.zjy.android.zlog.util

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import java.util.UUID


/**
 * 文件名：DeviceUtils
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
object DeviceUtil {
    fun getManufacturer(): String {
        return Build.MANUFACTURER ?: ""
    }

    fun getModel(): String {
        return Build.MODEL ?: ""
    }

    fun getSDKVersionName(): String {
        return Build.VERSION.RELEASE ?: ""
    }

    fun getSDKVersionCode(): Int {
        return Build.VERSION.SDK_INT
    }

    @SuppressLint("HardwareIds")
    fun getAndroidID(): String {
        val id: String? = Settings.Secure.getString(
            App.getApplication().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return if ("9774d56d682e549c" == id) "" else id ?: ""
    }

    fun getUniqueDeviceId(): String {
        try {
            val androidId: String = getAndroidID()
            if (!TextUtils.isEmpty(androidId)) {
                return getUDid("" + 2, androidId)
            }
        } catch (ignore: Exception) {

        }
        return getUDid("" + 9, "")
    }

    private fun getUDid(prefix: String, id: String): String {
        return if (id == "") {
            prefix + UUID.randomUUID().toString().replace("-", "")
        } else {
            prefix + UUID.nameUUIDFromBytes(id.toByteArray()).toString().replace("-", "")
        }
    }
}