package com.zjy.android.zlog.builder

/**
 * 文件名：ZLogBuilder
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/11
 * 描述：
 */
class ZLogBuilder private constructor(
    val rootDir: String,
    val offlineFilesExpireMillis: Int,
    val isOfflineLog: Boolean,
    val cacheFileSizeBytes: Int,
    val maxFileSizeBytes: Int,
    val compress: Boolean,
    val secretKey: String,
) {
    class Builder {
        private var rootDir = "zlog"
        private var offlineFilesExpireMillis: Int = 7 * 24 * 60 * 60 * 1000
        private var isOfflineLog: Boolean = false
        private var cacheFileSizeBytes: Int = 64 * 1024
        private var maxFileSizeBytes: Int = 50 * 1024 * 1024
        private var compress: Boolean = true
        private var secretKey: String = ""

        /**
         * 设置日志根目录
         * @param rootDir 日志根目录
         * @return Builder
         */
        fun rootDir(rootDir: String) = apply {
            this.rootDir = rootDir
        }

        /**
         * 设置离线日志过期时间
         * @param offlineFilesExpireMillis 日志过期时间（毫秒）
         * @return Builder
         */
        fun offlineFilesExpireMillis(offlineFilesExpireMillis: Int) = apply {
            this.offlineFilesExpireMillis = offlineFilesExpireMillis
        }

        /**
         * 设置是否是离线日志
         * @param isOfflineLog 是否是离线日志
         * @return Builder
         */
        fun isOfflineLog(isOfflineLog: Boolean) = apply {
            this.isOfflineLog = isOfflineLog
        }

        /**
         * 设置缓冲文件大小
         * @param cacheFileSizeBytes 缓冲文件大小（字节）
         * @return Builder
         */
        fun cacheFileSizeBytes(cacheFileSizeBytes: Int) = apply {
            this.cacheFileSizeBytes = cacheFileSizeBytes
        }

        /**
         * 设置单个日志文件最大大小
         * @param maxFileSizeBytes 单个日志文件最大大小（字节）
         * @return Builder
         */
        fun maxFileSizeBytes(maxFileSizeBytes: Int) = apply {
            this.maxFileSizeBytes = maxFileSizeBytes
        }

        /**
         * 设置是否压缩日志
         * @param compress 是否压缩日志
         * @return Builder
         */
        fun compress(compress: Boolean) = apply {
            this.compress = compress
        }

        /**
         * 设置加密密钥
         * @param secretKey 加密密钥
         * @return Builder
         */
        fun secretKey(secretKey: String) = apply {
            this.secretKey = secretKey
        }

        /**
         * 使用当前配置创建一个ZLogBuilder实例
         * @return 创建的ZLogBuilder实例
         */
        fun build() = ZLogBuilder(
            rootDir,
            offlineFilesExpireMillis,
            isOfflineLog,
            cacheFileSizeBytes,
            maxFileSizeBytes,
            compress,
            secretKey
        )
    }
}