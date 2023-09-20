package com.zjy.android.zlog.util

import com.zjy.android.zlog.proto.LogOuterClass.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * 文件名：ZLog
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/4
 * 描述：
 */
class ZLogUtil private constructor(private val builder: Builder) {

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private val logBuffer = LinkedBlockingQueue<ByteArray>()

    private val logLock = ReentrantLock()

    private var logPrefix = builder.logPrefix.ifEmpty { "log-" }

    private var logPath = File(
        App.getApplication().filesDir.absolutePath, builder.rootPath.ifEmpty { "zlog" }
    )

    private var maxLogAgeMillis: Int =
        (if (builder.maxLogAgeDays <= 0) 7 else builder.maxLogAgeDays) * 1000 * 24 * 60 * 60

    private var flushIntervalMillis: Long =
        (if (builder.flushIntervalSeconds <= 0) 10 else builder.flushIntervalSeconds) * 1000

    private var maxLogFileSize: Int =
        (if (builder.maxLogFileSize <= 0) 50 else builder.maxLogFileSize) * 1024 * 1024

    private var currentFileNumber: Int = 1

    private var currentRandomAccessFile: RandomAccessFile? = null

    private var currentRandomAccessFileChannel: FileChannel? = null

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    private var closeFlag = 0

    init {
        createLogDirectory()
        deleteOldLogs()
        createNewLogFile()

        executor.scheduleAtFixedRate({
            flushLogBuffer()
        }, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * 创建日志根目录
     */
    private fun createLogDirectory() {
        if (!logPath.exists()) {
            logPath.mkdirs()
        }
    }

    /**
     * 清理过期文件并记录文件序号
     */
    private fun deleteOldLogs() {
        val logFilePattern =
            "^$logPrefix(\\d{8})-(\\d{3})?${if (builder.isOfflineLog) "" else "-tmp"}.zlog$".toRegex()

        val currentTime = System.currentTimeMillis()

        logPath.listFiles()?.forEach { file ->
            val matchResult = logFilePattern.find(file.name)
            if (matchResult != null && matchResult.groupValues.size == 3) {
                try {
                    val fileDate = dateFormat.parse(matchResult.groupValues[1])?.time ?: 0
                    if (fileDate < currentTime - maxLogAgeMillis) {
                        file.delete()
                    } else {
                        currentFileNumber = matchResult.groupValues[2].toInt()
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 创建新的日志文件
     */
    private fun createNewLogFile() {
        val logFile = getCurrentLogFile()
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        closeFile()

        currentRandomAccessFile = RandomAccessFile(logFile, "rw")
        currentRandomAccessFileChannel = currentRandomAccessFile!!.channel
    }

    /**
     * 获取当日包含特定序号的日志文件
     * @return File 返回对应文件
     */
    private fun getCurrentLogFile(): File {
        return File(
            logPath,
            "$logPrefix${dateFormat.format(System.currentTimeMillis())}-${formatCurrentFileNumber()}${if (builder.isOfflineLog) "" else "-tmp"}.zlog"
        )
    }

    /**
     * 格式化文件序号
     * @return String 三位，不足补0
     */
    private fun formatCurrentFileNumber(): String {
        return String.format("%03d", currentFileNumber)
    }

    /**
     * 将日志添加到待写入缓冲区
     * @param log 要写入的日志数据
     */
    fun writeLog(log: Log) {
        logBuffer.offer(log.toByteArray())
    }

    /**
     * 读取日志内容(强烈建议在子线程中执行，并且只适合读取实时日志这类小数据的文件)
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @param removeFilesAfterReader 是否读取之后删除文件
     * @return List<String> 返回日志内容列表
     */
    fun readLog(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        removeFilesAfterReader: Boolean = true
    ): List<Log> {
        val logEntries: MutableList<Log> = mutableListOf()

        if (logLock.tryLock()) {
            try {
                val fileList = getLogFilesInRange(startTimeSeconds, endTimeSeconds)

                fileList.forEach { file ->
                    logEntries.addAll(readLogFile(file))
                }

                if (removeFilesAfterReader) {
                    //当天的日期
                    val currentTime = dateFormat.format(System.currentTimeMillis())

                    //是否需要重新创建文件
                    var reCreateLogFile = false

                    for (file in fileList) {
                        if (file.name.contains(currentTime)) {
                            reCreateLogFile = true
                        }

                        file.delete()
                    }

                    if (reCreateLogFile) {
                        currentFileNumber = 1
                        createNewLogFile()
                    }
                }
            } finally {
                logLock.unlock()
            }
        }

        return logEntries
    }

    /**
     * 压缩指定时间段内的日志文件
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @param outputFilePath 压缩后的Zip文件路径
     * @return 第一个值是状态，第二个如果是文件不存在为true
     */
    fun zipLogFiles(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        outputFilePath: String
    ): Pair<Boolean, Boolean> {
        val locked = logLock.tryLock(3, TimeUnit.SECONDS)
        try {
            if (locked) {
                val fileList = getLogFilesInRange(startTimeSeconds, endTimeSeconds)

                if (fileList.isEmpty()) {
                    return Pair(false, true)
                }

                return Pair(ZIPUtil.zipFiles(fileList, outputFilePath), false)
            } else {
                return Pair(false, false)
            }
        } finally {
            if (locked) {
                logLock.unlock()
            }
        }
    }

    /**
     * 获取指定时间段内的日志文件
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @return List<File> 返回匹配到的文件列表
     */
    private fun getLogFilesInRange(
        startTimeSeconds: Long,
        endTimeSeconds: Long
    ): List<File> {
        val logFilesInRange = mutableListOf<File>()

        val startOfDaySeconds = getDaySeconds(startTimeSeconds, true)
        val endOfDaySeconds = getDaySeconds(endTimeSeconds, false)

        val logFilePattern =
            "^$logPrefix(\\d{8})-(\\d{3})?${if (builder.isOfflineLog) "" else "-tmp"}.zlog$".toRegex()

        val matchingFiles = logPath.listFiles()?.filter { file ->
            val matchResult = logFilePattern.find(file.name)
            matchResult != null && matchResult.groupValues.size == 3
        }

        matchingFiles?.forEach { file ->
            val matchResult = logFilePattern.find(file.name)
            if (matchResult != null) {
                try {
                    val fileDate = (dateFormat.parse(matchResult.groupValues[1])?.time ?: 0) / 1000
                    if (fileDate in startOfDaySeconds..endOfDaySeconds) {
                        logFilesInRange.add(file)
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
        }

        return logFilesInRange
    }

    /**
     * 获取指定时间戳对应日期的秒数
     * @param timestampSeconds 时间戳的秒数
     * @param startOfDay true 表示获取当天的开始时间（0 秒），false 表示获取当天的结束时间（23:59:59秒）
     * @return 对应日期的秒数
     */
    private fun getDaySeconds(timestampSeconds: Long, startOfDay: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestampSeconds * 1000 // 将秒转换为毫秒
        if (startOfDay) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
        }
        val resultCalendar = Calendar.getInstance()
        resultCalendar.timeInMillis = calendar.timeInMillis
        return resultCalendar.timeInMillis / 1000
    }

    /**
     * 读取单个文件的日志内容
     * @param logFile 日志文件
     * @return List<String> 返回日志内容列表
     */
    private fun readLogFile(logFile: File): List<Log> {
        val logEntries = mutableListOf<Log>()

        if (!logFile.exists() || !logFile.isFile) {
            return logEntries
        }

        try {
            val fileInputStream = FileInputStream(logFile)

            while (true) {
                val firstByte = fileInputStream.read()
                if (firstByte == -1) {
                    break
                }

                if (firstByte == 0xFF) {
                    val remainingHeader = ByteArray(7)
                    val bytesRead = fileInputStream.read(remainingHeader)
                    if (bytesRead != 7) {
                        break
                    }

                    if (remainingHeader[6] == 0xFF.toByte()) {
                        val encryptionFlag = remainingHeader[0]
                        val compressionFlag = remainingHeader[1]
                        val dataLengthBytes = remainingHeader.copyOfRange(2, 6)
                        val dataLength = ByteBuffer.wrap(dataLengthBytes).int

                        val entryBytes = ByteArray(dataLength)
                        val dataBytesRead = fileInputStream.read(entryBytes)
                        if (dataBytesRead != dataLength) {
                            continue
                        }

                        val decryptData = if (encryptionFlag == 1.toByte() &&
                            builder.secretKey.trim().isNotEmpty()
                        ) {
                            AESUtil.decryptBytes(entryBytes, builder.secretKey)
                        } else {
                            entryBytes
                        }

                        val deCompressData = if (compressionFlag == 1.toByte()) {
                            GZIPUtil.deCompressBytes(decryptData)
                        } else {
                            decryptData
                        }

                        if (deCompressData.isNotEmpty()) {
                            logEntries.add(Log.parseFrom(deCompressData))
                        }
                    }
                }
            }

            fileInputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return logEntries
    }

    /**
     * 数据归档
     */
    private fun flushLogBuffer() {
        if (logLock.tryLock()) {
            try {
                val shouldCompress = builder.compress
                val shouldEncrypt = builder.secretKey.isNotBlank()
                var needBreak = false

                while (!needBreak && logBuffer.isNotEmpty()) {
                    val logData = logBuffer.poll()

                    if (logData == null || logData.isEmpty()) {
                        continue
                    }

                    //压缩和加密数据
                    val compressedData = if (shouldCompress) {
                        GZIPUtil.compressBytes(logData)
                    } else {
                        logData
                    }
                    val encryptedData = if (shouldEncrypt) {
                        AESUtil.encryptBytes(compressedData, builder.secretKey)
                    } else {
                        compressedData
                    }

                    //两个字节分别记录是否启用加密和压缩
                    val encryptionFlag: Byte = if (shouldEncrypt) 1 else 0
                    val compressionFlag: Byte = if (shouldCompress) 1 else 0
                    //四个字节记录数据的长度
                    val lengthBuffer = ByteBuffer.allocate(4).putInt(encryptedData.size)
                    lengthBuffer.flip()

                    //创建一个缓冲区
                    val bufferData =
                        ByteBuffer.allocate(1 + 1 + 1 + 4 + 1 + encryptedData.size).apply {
                            //头部开始标记
                            put(0xFF.toByte())
                            put(encryptionFlag)
                            put(compressionFlag)
                            put(lengthBuffer)
                            //头部结束标记
                            put(0xFF.toByte())
                            put(encryptedData)
                            flip()
                        }

                    currentRandomAccessFileChannel.let { channel ->
                        if (channel != null && channel.isOpen) {
                            //使用内存映射创建一个新的 ByteBuffer，从当前文件通道位置开始
                            val mappedBuffer = channel.map(
                                FileChannel.MapMode.READ_WRITE,
                                channel.position(),
                                bufferData.remaining().toLong()
                            )
                            //将数据从 bufferData 复制到 mappedBuffer
                            mappedBuffer.put(bufferData)
                            //更新文件通道的位置，以便下次写入从正确的位置开始
                            channel.position(channel.size())
                            //强制刷新内存映射，将数据写入文件
                            mappedBuffer.force()

                            currentRandomAccessFile.let { file ->
                                if (file != null) {
                                    //如果文件大小超过了 maxLogFileSize，创建新文件
                                    if (file.length() > maxLogFileSize) {
                                        currentFileNumber++
                                        createNewLogFile()
                                    }
                                } else {
                                    needBreak = true
                                }
                            }
                        } else {
                            needBreak = true
                        }
                    }

                    //判断关闭标志位
                    if (closeFlag == 1) {
                        closeFile()
                        needBreak = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                logLock.unlock()
            }
        }
    }

    /**
     * 释放资源
     */
    fun close() {
        executor.shutdown()
        //如果没有任务正在执行，则立即关闭
        if (logLock.tryLock()) {
            try {
                closeFile()
            } finally {
                logLock.unlock()
            }
        } else {
            //设置关闭标志位
            closeFlag = 1
        }
    }

    /**
     * 关闭文件
     */
    private fun closeFile() {
        try {
            currentRandomAccessFileChannel?.close()
            currentRandomAccessFileChannel = null
            currentRandomAccessFile?.close()
            currentRandomAccessFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class Builder {
        var rootPath = "zlog"
            private set
        var logPrefix = "log-"
            private set
        var maxLogAgeDays: Int = 7
            private set
        var flushIntervalSeconds: Long = 10
            private set
        var isOfflineLog: Boolean = false
            private set
        var maxLogFileSize: Int = 50
            private set
        var compress: Boolean = false
            private set
        var secretKey: String = ""
            private set

        fun rootPath(rootPath: String) = apply { this.rootPath = rootPath }

        fun logPrefix(logPrefix: String) = apply { this.logPrefix = logPrefix }

        fun maxLogAgeDays(maxLogAgeDays: Int) = apply { this.maxLogAgeDays = maxLogAgeDays }

        fun flushIntervalSeconds(flushIntervalSeconds: Long) =
            apply { this.flushIntervalSeconds = flushIntervalSeconds }

        fun isOfflineLog(isOfflineLog: Boolean) = apply { this.isOfflineLog = isOfflineLog }

        fun maxLogFileSize(maxLogFileSize: Int) = apply { this.maxLogFileSize = maxLogFileSize }

        fun compress(compress: Boolean) = apply { this.compress = compress }

        fun secretKey(secretKey: String) = apply { this.secretKey = secretKey }

        fun build(): ZLogUtil {
            return ZLogUtil(this)
        }
    }
}