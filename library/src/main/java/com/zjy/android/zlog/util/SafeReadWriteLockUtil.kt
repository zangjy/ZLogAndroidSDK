package com.zjy.android.zlog.util

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 文件名：SafeReadWriteLockUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/10/31
 * 描述：
 */
class SafeReadWriteLock {

    private val lock = ReentrantReadWriteLock()

    fun <T> runUnderReadLock(
        onLock: () -> T,
        onLockFailed: () -> Unit = {},
        waitTime: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        useTryLock: Boolean = true,
    ): T? {
        val readLock = lock.readLock()
        return if (useTryLock) {
            if (readLock.tryLock(waitTime, unit)) {
                try {
                    onLock()
                } finally {
                    readLock.unlock()
                }
            } else {
                onLockFailed()
                null
            }
        } else {
            readLock.lock()
            try {
                onLock()
            } finally {
                readLock.unlock()
            }
        }
    }

    fun <T> runUnderWriteLock(
        onLock: () -> T,
        onLockFailed: () -> Unit = {},
        waitTime: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        useTryLock: Boolean = true,
    ): T? {
        val writeLock = lock.writeLock()
        return if (useTryLock) {
            if (writeLock.tryLock(waitTime, unit)) {
                try {
                    onLock()
                } finally {
                    writeLock.unlock()
                }
            } else {
                onLockFailed()
                null
            }
        } else {
            writeLock.lock()
            try {
                onLock()
            } finally {
                writeLock.unlock()
            }
        }
    }
}