package com.zjy.android.zlog.util

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.zjy.android.zlog.exceptions.ZLogUnInitException
import com.zjy.android.zlog.viewmodel.ZLogGlobalVM

/**
 * 文件名：AppUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/5
 * 描述：
 */
object App {
    /**
     * Application
     */
    private lateinit var application: Application

    /**
     * 是否初始化完成
     */
    private var initSuccess = false

    /**
     * 服务端地址
     */
    private var hostUrl: String = ""

    /**
     * 应用ID
     */
    private var appId: String = ""

    /**
     * 是否将日志输出到控制台
     */
    private var enableConsole = false

    /**
     * 全局的ViewModel
     */
    private val zLogGlobalVM by lazy {
        ViewModelProvider.AndroidViewModelFactory(getApplication()).create(ZLogGlobalVM::class.java)
    }

    /**
     * 设置应用程序上下文
     * @param application 应用程序对象
     */
    fun setApplication(application: Application) {
        this.application = application
    }

    /**
     * 获取应用程序上下文
     * @throws ZLogUnInitException 如果未初始化，将抛出异常
     * @return 应用程序对象
     */
    fun getApplication(): Application {
        if (!this::application.isInitialized) {
            throw ZLogUnInitException("ZLog未初始化")
        }
        return application
    }

    /**
     * SDK初始化完后调用本方法，写入日志时会读取设备信息
     * @param hostUrl 服务端地址
     * @param appId 应用ID
     * @param enableConsole 是否输出日志
     */
    fun init(hostUrl: String, appId: String, enableConsole: Boolean) {
        initSuccess = true
        this.hostUrl = hostUrl
        this.appId = appId
        this.enableConsole = enableConsole
    }

    /**
     * 是否初始化完成
     * @return 返回状态
     */
    fun isInitSuccess() = initSuccess

    /**
     * 获取服务端地址
     * @return 返回服务端地址
     */
    fun getHostUrl() = hostUrl.ifEmpty { "http://127.0.0.1:20000" }

    /**
     * 获取应用Id
     * @return 返回服务端地址
     */
    fun getAppId() = appId

    /**
     * 是否将日志输出到控制台
     * @return 返回是否将日志输出到控制台
     */
    fun enableConsole() = enableConsole

    /**
     * 获取全局ViewModel
     * @return ZLogGlobalVM ViewModel对象
     */
    fun getGlobalVM() = zLogGlobalVM
}