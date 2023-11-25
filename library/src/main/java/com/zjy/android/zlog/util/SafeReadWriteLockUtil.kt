package com.zjy.android.zlog.util

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 文件名：SafeReadWriteLockUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/10/31
 * 描述：
 */
class SafeReadWriteLock() {
    private val lock = ReentrantReadWriteLock()

    fun readLock(): SafeReadLock {
        val readLock = lock.readLock()
        return SafeReadLock(readLock)
    }

    fun writeLock(): SafeWriteLock {
        val writeLock = lock.writeLock()
        return SafeWriteLock(writeLock)
    }

    inner class SafeReadLock(private val readLock: ReentrantReadWriteLock.ReadLock) {
        fun <T> runUnderLock(
            onLock: () -> T,
            onLockFailed: () -> Unit = {},
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ): T? {
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
    }

    inner class SafeWriteLock(private val writeLock: ReentrantReadWriteLock.WriteLock) {
        fun <T> runUnderLock(
            onLock: () -> T,
            onLockFailed: () -> Unit = {},
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ): T? {
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
}