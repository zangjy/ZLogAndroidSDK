package com.zjy.android.zlog.util

import com.zjy.android.zlog.constant.SPConstant
import com.zjy.android.zlog.proto.LogOuterClass.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 文件名：ZLog
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/4
 * 描述：
 */
class ZLogUtil private constructor(private val builder: Builder) {
    /**
     * SharedPreferences实例
     */
    private val spUtil by lazy {
        SPUtil.getInstance(SPConstant.CACHE_FILE_LAST_POSITION_SP_NAME)
    }

    /**
     * 日志文件的根目录
     */
    private var rootPath =
        File(App.getApplication().filesDir.absolutePath, builder.rootPath.ifEmpty { "zlog" })

    /**
     * 日志文件过期时间（毫秒）
     */
    private var expireMillis =
        (if (builder.expireDays <= 0) 7 else builder.expireDays) * 1000 * 24 * 60 * 60

    /**
     * 日志文件的后缀
     */
    private var logSuffix = "${if (builder.isOfflineLog) "" else "-tmp"}.zlog"

    /**
     * 缓存文件大小（字节）
     */
    private var cacheFileSize =
        (if (builder.cacheFileSize <= 0) 64 else builder.cacheFileSize) * 1024

    /**
     * 单个日志文件最大大小（字节）
     */
    private var maxFileSize =
        (if (builder.maxFileSize <= 0) 50 else builder.maxFileSize) * 1024 * 1024

    /**
     * 日志锁
     */
    private val logLock = SafeReadWriteLock()

    /**
     * 日志内容的队列
     */
    private val logBuffer = LinkedBlockingQueue<ByteArray>()

    /**
     * 记录缓存文件上次写入位置的Key
     */
    private val cacheFileLastPositionKey =
        if (builder.isOfflineLog) SPConstant.CACHE_FILE_LAST_POSITION_KEY else SPConstant.TMP_CACHE_FILE_LAST_POSITION_KEY

    /**
     * 缓存文件
     */
    private var cacheFile: RandomAccessFile? = null

    /**
     * 缓存文件的缓冲区
     */
    private var cacheFileBuffer: MappedByteBuffer? = null

    /**
     * 将日志队列中的数据写入到缓冲区的间隔（毫秒）
     */
    private var writeToCacheFileBufferMillis = 10 * 1000L

    /**
     * 写日志到缓冲区的线程
     */
    private val writeToCacheFileBufferExecutor = Executors.newSingleThreadScheduledExecutor()

    /**
     * 日志文件当前的序号
     */
    private var fileNumber = 1

    /**
     * 当前的日志文件
     */
    private var currentFile: RandomAccessFile? = null

    /**
     * 当前的日志文件的通道
     */
    private var currentFileChannel: FileChannel? = null

    /**
     * 合并缓存文件到主文件的间隔（毫秒）
     */
    private var mergeCacheFileMillis =
        (if (builder.mergeCacheFileSeconds <= 0) 30 else builder.mergeCacheFileSeconds) * 1000

    /**
     * 合并缓存文件到主文件的线程
     */
    private val mergeCacheFileExecutor = Executors.newSingleThreadScheduledExecutor()

    /**
     * 通用格式化日期
     */
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    /**
     * 是否标记为关闭
     */
    private var closeFlag = 0

    init {
        createBaseDir()
        removeExpireFiles()
        createCacheFile()
        createNewLogFile()

        writeToCacheFileBufferExecutor.scheduleAtFixedRate({
            //只有当日志队列不为空时才尝试获取锁写入日志
            if (logBuffer.size > 0) {
                logLock.writeLock().withLock({
                    writeToCacheFileBuffer()
                })
            }
        }, writeToCacheFileBufferMillis, writeToCacheFileBufferMillis, TimeUnit.MILLISECONDS)

        mergeCacheFileExecutor.scheduleAtFixedRate({
            //只有当缓冲区里有数据时才尝试获取锁合并文件
            if ((cacheFileBuffer?.position() ?: 0) > 0) {
                logLock.writeLock().withLock({
                    mergeFile()
                }, waitTime = 2000)
            }
        }, mergeCacheFileMillis, mergeCacheFileMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * 如果日志根目录不存在则先创建
     */
    private fun createBaseDir() {
        if (!rootPath.exists()) {
            rootPath.mkdirs()
        }
    }

    /**
     * 清理过期文件并记录文件序号
     */
    private fun removeExpireFiles() {
        val filePattern = "^(\\d{8})-(\\d{3})?${logSuffix}$".toRegex()

        val currentTime = System.currentTimeMillis()

        rootPath.listFiles()?.forEach { file ->
            val matchResult = filePattern.find(file.name)
            if (matchResult != null && matchResult.groupValues.size == 3) {
                try {
                    val fileDate = dateFormat.parse(matchResult.groupValues[1])?.time ?: 0
                    if (fileDate < currentTime - expireMillis) {
                        file.delete()
                    } else {
                        fileNumber = matchResult.groupValues[2].toInt()
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 创建缓存文件
     */
    private fun createCacheFile() {
        val logFile = File(rootPath, "cache${logSuffix}")
        if (!logFile.exists()) {
            logFile.createNewFile()
        }

        cacheFile = RandomAccessFile(logFile, "rw")

        cacheFileBuffer = cacheFile!!.channel.map(
            FileChannel.MapMode.READ_WRITE,
            0,
            cacheFileSize.toLong()
        )

        //获取缓存文件最后写入的位置
        val cacheFileCurPos = spUtil.getLong(cacheFileLastPositionKey, 0)

        //设置开始写入的位置
        if (cacheFileCurPos <= cacheFileSize) {
            cacheFileBuffer!!.position(cacheFileCurPos.toInt())
        }
    }

    /**
     * 创建新的日志文件
     */
    private fun createNewLogFile() {
        val logFile = getToadyLogFile()
        if (!logFile.exists()) {
            logFile.createNewFile()
        }

        closeFile()

        currentFile = RandomAccessFile(logFile, "rw")
        currentFile!!.seek(currentFile!!.length())

        currentFileChannel = currentFile!!.channel
    }

    /**
     * 获取当天带序号的日志文件
     * @return File 返回对应文件
     */
    private fun getToadyLogFile(): File {
        return File(
            rootPath,
            "${dateFormat.format(System.currentTimeMillis())}-${formatFileNumber()}${logSuffix}"
        )
    }

    /**
     * 格式化日志文件序号
     * @return String 返回格式化后的序号，不足三位前面补0
     */
    private fun formatFileNumber(): String {
        return String.format("%03d", fileNumber)
    }

    /**
     * 读取日志内容(需要在子线程中执行，并且只适合读取实时日志这类小数据的文件)
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @param removeFilesAfterReader 读取完成后是否删除文件
     * @return List<String> 返回日志内容列表
     */
    fun readLog(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        removeFilesAfterReader: Boolean = true
    ): List<Log> {
        val logEntries: MutableList<Log> = mutableListOf()

        logLock.readLock().withLock({
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
                    fileNumber = 1
                    createNewLogFile()
                }
            }
        })

        return logEntries
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
     * 压缩指定时间段内的日志文件
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @param outputFilePath 压缩后的Zip文件路径
     * @return 第一个值是状态，如果文件不存在第二个参数返回true
     */
    fun zipLogFiles(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        outputFilePath: String
    ): Pair<Boolean, Boolean> {
        var result: Pair<Boolean, Boolean> = Pair(false, false)

        logLock.readLock().withLock({
            val fileList = getLogFilesInRange(startTimeSeconds, endTimeSeconds)
            result = if (fileList.isEmpty()) {
                Pair(false, true)
            } else {
                Pair(ZIPUtil.zipFiles(fileList, outputFilePath), false)
            }
        })

        return result
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

        val filePattern = "^(\\d{8})-(\\d{3})?${logSuffix}$".toRegex()

        val matchingFiles = rootPath.listFiles()?.filter { file ->
            val matchResult = filePattern.find(file.name)
            matchResult != null && matchResult.groupValues.size == 3
        }

        matchingFiles?.forEach { file ->
            val matchResult = filePattern.find(file.name)
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
     * @param timeStampSeconds 时间戳的秒数
     * @param startOfDay true 表示获取当天的开始时间（0 秒），false 表示获取当天的结束时间（23:59:59秒）
     * @return 对应日期的秒数
     */
    private fun getDaySeconds(timeStampSeconds: Long, startOfDay: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeStampSeconds * 1000
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
     * 写入日志
     * @param log 要写入的日志数据
     */
    fun writeLog(log: Log) {
        if (closeFlag == 1) {
            return
        }
        logBuffer.offer(log.toByteArray())
    }

    /**
     * 写入日志
     */
    private fun writeToCacheFileBuffer() {
        try {
            val shouldCompress = builder.compress
            val shouldEncrypt = builder.secretKey.isNotBlank()

            while (logBuffer.isNotEmpty()) {
                val logData = logBuffer.poll()

                if (logData == null || logData.isEmpty()) {
                    continue
                }

                //压缩数据
                val compressedData = if (shouldCompress) {
                    GZIPUtil.compressBytes(logData)
                } else {
                    logData
                }

                //加密数据
                val encryptedData = if (shouldEncrypt) {
                    AESUtil.encryptBytes(compressedData, builder.secretKey)
                } else {
                    compressedData
                }

                //是否启用了加密
                val encryptionFlag: Byte = if (shouldEncrypt) 1 else 0

                //是否启用了压缩
                val compressionFlag: Byte = if (shouldCompress) 1 else 0

                //四个字节记录数据的长度
                val lengthBuffer = ByteBuffer.allocate(4).putInt(encryptedData.size)
                lengthBuffer.flip()

                //创建一个缓冲区
                val dataToWrite =
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

                //如果缓冲区不足以写入当前数据，则先将缓存文件合入主文件后再进行写入
                if (dataToWrite.remaining() > (cacheFileBuffer?.remaining() ?: 0)) {
                    mergeFile()
                }

                //将数据写入缓冲区
                cacheFileBuffer?.put(dataToWrite)

                //判断关闭标志位
                if (closeFlag == 1) {
                    closeFile()
                    break
                }
            }

            //将缓冲区数据写入缓存文件中并记录位置
            forceCacheFileBuffer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 将缓冲区数据合入主文件
     */
    private fun mergeFile() {
        try {
            cacheFileBuffer?.let { buffer ->
                //获取缓冲区有效数据长度
                val dataSize = buffer.position()

                //如果数据不为空，则将其合并到主文件
                if (dataSize > 0) {
                    //将缓冲区切换到读模式
                    buffer.flip()

                    //将缓冲区中有效数据提取出来
                    val dataToWrite = ByteBuffer.allocate(dataSize)
                    dataToWrite.put(buffer)
                    dataToWrite.flip()

                    //将有效数据合入到主文件
                    currentFileChannel?.write(dataToWrite)

                    //强制刷新数据到文件
                    currentFileChannel?.force(true)

                    //清空缓存文件的缓冲区
                    buffer.clear()

                    //重置缓存文件最后写入位置
                    spUtil.putLong(cacheFileLastPositionKey, 0, true)
                }
            }

            //如果主文件大小超出设置的最大值，则创建新的主文件
            if ((currentFile?.length() ?: 0) > maxFileSize) {
                fileNumber++
                createNewLogFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 将缓冲区数据写入缓存文件中并记录位置
     */
    private fun forceCacheFileBuffer() {
        cacheFileBuffer?.force()

        spUtil.putLong(
            cacheFileLastPositionKey,
            (cacheFileBuffer?.position() ?: 0).toLong(),
            true
        )
    }

    /**
     * 释放资源
     */
    fun close() {
        writeToCacheFileBufferExecutor.shutdown()
        mergeCacheFileExecutor.shutdown()

        //设置关闭标志位
        closeFlag = 1

        //如果没有写任务正在执行，则立即关闭
        logLock.writeLock().withLock({
            closeFile()
        })
    }

    /**
     * 关闭文件
     */
    private fun closeFile() {
        try {
            currentFileChannel?.close()
            currentFileChannel = null

            currentFile?.close()
            currentFile = null

            if (closeFlag == 1) {
                cacheFile?.close()
                cacheFile = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class Builder {
        var rootPath = "zlog"
            private set
        var expireDays: Int = 7
            private set
        var isOfflineLog: Boolean = false
            private set
        var cacheFileSize: Int = 64
            private set
        var maxFileSize: Int = 50
            private set
        var mergeCacheFileSeconds: Long = 30
            private set
        var compress: Boolean = true
            private set
        var secretKey: String = ""
            private set

        /**
         * 设置日志根目录
         * @param rootPath 日志根目录
         * @return Builder
         */
        fun rootPath(rootPath: String) = apply { this.rootPath = rootPath }

        /**
         * 设置日志过期时间
         * @param expireDays 日志过期时间（天）
         * @return Builder
         */
        fun expireDays(expireDays: Int) = apply { this.expireDays = expireDays }

        /**
         * 设置是否离线日志
         * @param isOfflineLog 是否离线日志
         * @return Builder
         */
        fun isOfflineLog(isOfflineLog: Boolean) = apply { this.isOfflineLog = isOfflineLog }

        /**
         * 设置缓存文件大小
         * @param cacheFileSize 缓存文件大小（KB）
         * @return Builder
         */
        fun cacheFileSize(cacheFileSize: Int) = apply { this.cacheFileSize = cacheFileSize }

        /**
         * 设置单个日志文件最大大小
         * @param maxFileSize 单个日志文件最大大小（MB）
         * @return Builder
         */
        fun maxFileSize(maxFileSize: Int) = apply { this.maxFileSize = maxFileSize }

        /**
         * 设置合并缓存文件到主文件的时间间隔
         * @param mergeCacheFileSeconds 合并缓存文件到主文件的时间间隔（秒）
         * @return Builder
         */
        fun mergeCacheFileSeconds(mergeCacheFileSeconds: Long) =
            apply { this.mergeCacheFileSeconds = mergeCacheFileSeconds }

        /**
         * 设置是否压缩日志
         * @param compress 是否压缩日志
         * @return Builder
         */
        fun compress(compress: Boolean) = apply { this.compress = compress }

        /**
         * 设置加密密钥
         * @param secretKey 加密密钥
         * @return Builder
         */
        fun secretKey(secretKey: String) = apply { this.secretKey = secretKey }

        /**
         * 构建 ZLogUtil
         * @return ZLogUtil
         */
        fun build(): ZLogUtil {
            return ZLogUtil(this)
        }
    }
}