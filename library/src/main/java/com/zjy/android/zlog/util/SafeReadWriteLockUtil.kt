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
        fun <T> withLock(
            action: () -> T,
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ): T? {
            return if (useTryLock) {
                if (readLock.tryLock(waitTime, unit)) {
                    try {
                        action()
                    } finally {
                        readLock.unlock()
                    }
                } else {
                    null
                }
            } else {
                readLock.lock()
                try {
                    action()
                } finally {
                    readLock.unlock()
                }
            }
        }

        fun withFailedLock(
            action: () -> Unit,
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ) {
            if (useTryLock && !readLock.tryLock(waitTime, unit)) {
                try {
                    action()
                } finally {
                    readLock.unlock()
                }
            }
        }
    }

    inner class SafeWriteLock(private val writeLock: ReentrantReadWriteLock.WriteLock) {
        fun <T> withLock(
            action: () -> T,
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ): T? {
            return if (useTryLock) {
                if (writeLock.tryLock(waitTime, unit)) {
                    try {
                        action()
                    } finally {
                        writeLock.unlock()
                    }
                } else {
                    null
                }
            } else {
                writeLock.lock()
                try {
                    action()
                } finally {
                    writeLock.unlock()
                }
            }
        }

        fun withFailedLock(
            action: () -> Unit,
            waitTime: Long = 0,
            unit: TimeUnit = TimeUnit.MILLISECONDS,
            useTryLock: Boolean = true
        ) {
            if (useTryLock && !writeLock.tryLock(waitTime, unit)) {
                try {
                    action()
                } finally {
                    writeLock.unlock()
                }
            }
        }
    }
}