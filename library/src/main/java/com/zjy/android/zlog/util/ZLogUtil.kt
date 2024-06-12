package com.zjy.android.zlog.util

import com.zjy.android.zlog.proto.LogOuterClass
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * 文件名：ZLogUtil
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/11
 * 描述：
 */
object ZLogUtil {

    /**
     * 如果给定的ByteArray长度大于指定的大小（以KB为单位），则截断到指定大小，否则返回原始ByteArray
     * @param data 原始的ByteArray
     * @param maxSize 指定的最大大小（以KB为单位）
     * @return 截断到指定大小的ByteArray，或原始ByteArray
     */
    fun truncateByteArrayIfNeeded(data: ByteArray, maxSize: Int): ByteArray {
        val maxSizeBytes = maxSize * 1024
        return if (data.size > maxSizeBytes) {
            data.copyOf(maxSizeBytes)
        } else {
            data
        }
    }

    /**
     * 清理过期的文件并获取文件序号
     * @param rootDir 日志文件的根目录
     * @param fileSuffix 日志文件名的后缀
     * @param dateFormat 日期格式化对象，用于解析文件名中的日期
     * @param offlineFilesExpireMillis 文件过期的毫秒数，用于判断文件是否已过期（毫秒）
     * @return 返回最新的文件序号和最后一个匹配到的日志文件路径
     */
    fun cleanupExpireFilesAndGetFileNumber(
        rootDir: File,
        fileSuffix: String,
        dateFormat: SimpleDateFormat,
        offlineFilesExpireMillis: Int,
    ): Pair<Int, String> {
        var fileNumber = 1
        var lastFilePath = ""

        val currentTimeStampMillis = System.currentTimeMillis()

        getMatchedFiles(rootDir, fileSuffix, dateFormat) { file, fileDate, matchResult ->
            lastFilePath = file.absolutePath
            if (fileDate < currentTimeStampMillis - offlineFilesExpireMillis) {
                file.delete()
            } else {
                fileNumber = matchResult.groupValues[2].toInt()
            }
            //清理过期文件时，不需要使用其返回的匹配文件列表，返回false即可
            false
        }

        return Pair(fileNumber, lastFilePath)
    }

    /**
     * 获取指定时间段内的日志文件
     * @param rootDir 日志文件的根目录
     * @param fileSuffix 日志文件名的后缀
     * @param dateFormat 日期格式化对象，用于解析文件名中的日期
     * @param startTimeStampMillis 起始的时间戳（毫秒）
     * @param endTimeStampMillis 截止的时间戳（毫秒）
     * @return 返回匹配到的文件列表
     */
    fun getLogFilesInRange(
        rootDir: File,
        fileSuffix: String,
        dateFormat: SimpleDateFormat,
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
    ): List<File> {
        val logFilesInRange = mutableListOf<File>()

        val startOfDayTimeStampMillis = getDayTimeStampMillis(startTimeStampMillis, true)
        val endOfDayTimeStampMillis = getDayTimeStampMillis(endTimeStampMillis, false)

        logFilesInRange.addAll(
            getMatchedFiles(
                rootDir,
                fileSuffix,
                dateFormat
            ) { _, fileDate, _ ->
                fileDate in startOfDayTimeStampMillis..endOfDayTimeStampMillis
            }
        )

        return logFilesInRange
    }

    /**
     * 匹配日志文件列表
     * @param rootDir 日志文件的根目录
     * @param fileSuffix 日志文件名的后缀
     * @param dateFormat 日期格式化对象，用于解析文件名中的日期
     * @param condition 判断文件是否满足条件的函数
     * @return 返回匹配条件的文件列表
     */
    private fun getMatchedFiles(
        rootDir: File,
        fileSuffix: String,
        dateFormat: SimpleDateFormat,
        condition: (file: File, fileDate: Long, matchResult: MatchResult) -> Boolean,
    ): List<File> {
        val matchedFiles = mutableListOf<File>()

        val filePattern = "^(\\d{8})-(\\d{3})?${fileSuffix}$".toRegex()

        rootDir.listFiles()?.forEach { file ->
            filePattern.find(file.name)?.let { matchResult ->
                if (matchResult.groupValues.size == 3) {
                    val fileDate = dateFormat.parse(matchResult.groupValues[1])?.time ?: 0
                    if (condition(file, fileDate, matchResult)) {
                        matchedFiles.add(file)
                    }
                }
            }
        }

        return matchedFiles
    }

    /**
     * 返回给定时间戳所在日期的开始或结束时间戳
     * @param timeStampMillis 时间戳（毫秒）
     * @param startOfDay 是否获取当天的开始时间戳
     * @return 当天开始或结束的时间戳（毫秒）
     */
    private fun getDayTimeStampMillis(timeStampMillis: Long, startOfDay: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeStampMillis
        if (startOfDay) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    /**
     * 从指定文件中读取日志
     * @param logFile 要读取的日志文件
     * @param secretKey 用于解密的密钥
     * @return 日志列表
     */
    fun readLogsFromFile(logFile: File, secretKey: String): MutableList<LogOuterClass.Log> {
        if (!logFile.exists() || !logFile.isFile) {
            return mutableListOf()
        }

        return try {
            FileInputStream(logFile).use { fileInputStream ->
                readLogsFromInputStream(fileInputStream, secretKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    /**
     * 从ByteBuffer中读取日志
     * @param buffer 包含日志的ByteBuffer
     * @param secretKey 用于解密的密钥
     * @return 日志列表
     */
    fun readLogsFromByteBuffer(
        buffer: ByteBuffer,
        secretKey: String,
    ): MutableList<LogOuterClass.Log> {
        return try {
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)
            ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
                readLogsFromInputStream(byteArrayInputStream, secretKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    /**
     * 从输入流中读取日志
     * @param inputStream 要读取的输入流
     * @param secretKey 用于解密的密钥
     * @return 日志列表
     */
    private fun readLogsFromInputStream(
        inputStream: InputStream,
        secretKey: String,
    ): MutableList<LogOuterClass.Log> {
        val logEntries = mutableListOf<LogOuterClass.Log>()

        while (true) {
            val firstByte = inputStream.read()
            if (firstByte == -1) {
                break
            }

            if (firstByte != 0xFF) {
                continue
            }

            val remainingHeader = ByteArray(7)
            if (inputStream.read(remainingHeader) < 7) {
                break
            }

            if (remainingHeader[6] != 0xFF.toByte()) {
                continue
            }

            val encryptionFlag = remainingHeader[0]
            val compressionFlag = remainingHeader[1]
            val dataLengthBytes = remainingHeader.copyOfRange(2, 6)
            val dataLength = ByteBuffer.wrap(dataLengthBytes).int

            val entryBytes = ByteArray(dataLength)
            if (inputStream.read(entryBytes) < dataLength) {
                break
            }

            val decryptData = if (encryptionFlag == 1.toByte() && secretKey.isNotEmpty()) {
                AESUtil.decryptBytes(entryBytes, secretKey)
            } else {
                entryBytes
            }

            val deCompressData = if (compressionFlag == 1.toByte()) {
                GZIPUtil.deCompressBytes(decryptData)
            } else {
                decryptData
            }

            if (deCompressData.isNotEmpty()) {
                logEntries.add(LogOuterClass.Log.parseFrom(deCompressData))
            }
        }

        return logEntries
    }
}