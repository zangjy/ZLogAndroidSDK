package com.zjy.android.zlog.util

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * 文件名：SafeLockUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/10/31
 * 描述：
 */
class SafeLock {

    private val lock = ReentrantLock()

    fun <T> runUnderLock(
        onLock: () -> T,
        onLockFailed: () -> Unit = {},
        waitTime: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        useTryLock: Boolean = true,
    ): T? {
        return if (useTryLock) {
            if (lock.tryLock(waitTime, unit)) {
                try {
                    onLock()
                } finally {
                    lock.unlock()
                }
            } else {
                onLockFailed()
                null
            }
        } else {
            lock.lock()
            try {
                onLock()
            } finally {
                lock.unlock()
            }
        }
    }
}