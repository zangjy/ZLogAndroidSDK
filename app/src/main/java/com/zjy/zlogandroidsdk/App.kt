package com.zjy.zlogandroidsdk

import android.app.Application
import com.zjy.android.zlog.ZLog

/**
 * 文件名：App
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/5
 * 描述：
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ZLog.preInit(this)
        ZLog.init("http://192.168.2.188:20000/", "709421174920052736")
        ZLog.changeIdentifyValue("155****0332")
    }

    override fun onTerminate() {
        ZLog.close()
        super.onTerminate()
    }
}