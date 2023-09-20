package com.zjy.android.zlog.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * 文件名：ThreadUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/15
 * 描述：
 */
object ThreadUtil {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

    /**
     * 在后台线程池中执行任务
     * @param task 要在后台线程中执行的任务
     * @param onComplete 任务完成时的回调
     */
    fun runOnBackgroundThread(task: () -> Unit, onComplete: () -> Unit = {}) {
        backgroundExecutor.execute {
            try {
                task()
            } finally {
                mainHandler.post(onComplete)
            }
        }
    }

    /**
     * 在主线程中执行任务
     * @param task 要在主线程中执行的任务
     * @param onComplete 任务完成时的回调
     */
    fun runOnMainThread(task: () -> Unit, onComplete: () -> Unit = {}) {
        mainHandler.post {
            try {
                task()
            } finally {
                mainHandler.post(onComplete)
            }
        }
    }
}