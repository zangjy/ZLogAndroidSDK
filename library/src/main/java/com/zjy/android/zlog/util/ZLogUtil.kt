package com.zjy.android.zlog.util

import com.tencent.mmkv.MMKV
import com.zjy.android.zlog.constant.Constant
import com.zjy.android.zlog.proto.LogOuterClass.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 文件名：ZLog
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/4
 * 描述：
 */
class ZLogUtil private constructor(private val builder: Builder) {

    //<editor-fold desc="基础配置">
    /**
     * 日志文件的根目录
     */
    private val rootDir by lazy {
        File(
            App.getApplication().filesDir.absolutePath,
            builder.rootPath.ifEmpty { "zlog" }
        )
    }

    /**
     * 日志文件过期时间（毫秒）
     */
    private val expireMillis by lazy {
        (if (builder.expireDays <= 0) {
            7
        } else {
            builder.expireDays
        }) * 1000 * 24 * 60 * 60
    }

    /**
     * 日志文件的后缀
     */
    private val logSuffix by lazy {
        (if (builder.isOfflineLog) {
            ""
        } else {
            "-tmp"
        }) + ".zlog"
    }

    /**
     * 缓冲文件大小（字节）
     */
    private val cacheFileSize by lazy {
        (if (builder.cacheFileSize <= 0) {
            64
        } else {
            builder.cacheFileSize
        }) * 1024
    }

    /**
     * 单个日志文件最大大小（字节）
     */
    private val maxFileSize by lazy {
        (if (builder.maxFileSize <= 0) {
            50
        } else {
            builder.maxFileSize
        }) * 1024 * 1024
    }
    //</editor-fold>

    /**
     * 用于记录写入位置
     */
    private val wPosMMKV by lazy {
        MMKV.mmkvWithID(Constant.W_POS_MMKV_NAME)
    }

    /**
     * 日志读写锁
     */
    private val logLock by lazy {
        SafeReadWriteLock()
    }

    /**
     * 创建一个协程作用域，处理IO操作
     */
    private val coroutineScope by lazy {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    /**
     * 创建一个用于协程间通信的通道，传输ByteArray类型的数据
     */
    private val writeChannel by lazy {
        Channel<ByteArray>(capacity = Channel.UNLIMITED)
    }

    //<editor-fold desc="缓冲文件相关">
    /**
     * 缓冲文件
     */
    private var cacheFile: RandomAccessFile? = null

    /**
     * 缓冲文件的缓冲区
     */
    private var cacheFileBuffer: MappedByteBuffer? = null
    //</editor-fold>

    //<editor-fold desc="主文件相关">
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
    //</editor-fold>

    /**
     * 通用格式化日期
     */
    private val dateFormat by lazy {
        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    }

    /**
     * 关闭标记
     */
    private var closeFlag = false

    init {
        createBaseDir()
        removeExpireFiles()
        createCacheFile()
        createNewLogFile()
        startWriting()
    }

    /**
     * 如果日志根目录不存在则先创建
     */
    private fun createBaseDir() {
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理过期文件并记录文件序号
     */
    private fun removeExpireFiles() {
        try {
            //示例匹配的文件名：20240606-001.zlog、20240606-001-tmp.zlog
            val filePattern = "^(\\d{8})-(\\d{3})?${logSuffix}$".toRegex()

            val currentTime = System.currentTimeMillis()

            rootDir.listFiles()?.forEach { file ->
                filePattern.find(file.name)?.let { matchResult ->
                    if (matchResult.groupValues.size == 3) {
                        val fileDate = dateFormat.parse(matchResult.groupValues[1])?.time ?: 0
                        if (fileDate < currentTime - expireMillis) {
                            file.delete()
                        } else {
                            fileNumber = matchResult.groupValues[2].toInt()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建缓冲文件
     */
    private fun createCacheFile() {
        try {
            //示例创建的文件名：cache.zlog、cache-tmp.zlog
            val logFile = File(rootDir, "cache${logSuffix}")
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            cacheFile = RandomAccessFile(logFile, "rw")

            cacheFileBuffer = cacheFile!!.channel.map(
                FileChannel.MapMode.READ_WRITE,
                0,
                cacheFileSize.toLong()
            )

            //从上次的位置继续写
            cacheFileBuffer!!.position(wPosMMKV.decodeInt(Constant.CACHE_FILE_LAST_WRITE_POS_KEY + logSuffix))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建新的日志文件
     */
    private fun createNewLogFile() {
        try {
            val logFile = getNewLogFile()
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            closeFile()

            currentFile = RandomAccessFile(logFile, "rw")

            //从上次的位置继续写
            currentFile!!.seek(currentFile!!.length())

            currentFileChannel = currentFile!!.channel
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取新的日志文件
     * @return File 返回对应文件，文件名示例：20240606-001.zlog、20240606-001-tmp.zlog
     */
    private fun getNewLogFile() = File(
        rootDir,
        "${dateFormat.format(System.currentTimeMillis())}-${formatFileNumber()}${logSuffix}"
    )

    /**
     * 格式化日志文件序号
     * @return String 返回格式化后的序号，不足三位前面补0
     */
    private fun formatFileNumber(): String {
        return String.format(Locale.getDefault(), "%03d", fileNumber)
    }

    /**
     * 在子线程中执行写日志操作
     */
    private fun startWriting() {
        coroutineScope.launch {
            for (logData in writeChannel) {
                try {
                    //标记是否写入成功
                    var writeSuccess = false

                    while (!writeSuccess) {
                        //尝试获取写锁，获取失败时延时100ms再次重试，获取到写锁并写入完成后标记为写入成功，继续写下一条
                        logLock.runUnderWriteLock(onLock = {
                            //是否启用压缩
                            val shouldCompress = builder.compress

                            //是否启用加密
                            val shouldEncrypt = builder.secretKey.isNotEmpty()

                            //是否启用加密标志位
                            val encryptionFlag: Byte = if (shouldEncrypt) 1 else 0

                            //是否启用压缩标志位
                            val compressionFlag: Byte = if (shouldCompress) 1 else 0

                            //如果启用压缩，则对logData进行压缩，否则直接使用原始的logData
                            val compressedData = if (shouldCompress) {
                                GZIPUtil.compressBytes(logData)
                            } else {
                                logData
                            }

                            //如果启用加密，则对compressedData进行加密，否则直接使用compressedData
                            val encryptedData = if (shouldEncrypt) {
                                AESUtil.encryptBytes(compressedData, builder.secretKey)
                            } else {
                                compressedData
                            }

                            //使用四个字节日志数据的长度
                            val lengthByteBuffer = ByteBuffer.allocate(4).putInt(encryptedData.size)
                            lengthByteBuffer.flip()

                            //处理后的日志数据
                            val logDataByteBuffer = ByteBuffer.allocate(
                                8 + encryptedData.size
                            ).apply {
                                //头部开始标记
                                put(0xFF.toByte())
                                put(encryptionFlag)
                                put(compressionFlag)
                                put(lengthByteBuffer)
                                //头部结束标记
                                put(0xFF.toByte())
                                put(encryptedData)
                                flip()
                            }

                            //处理后的日志数据字节数
                            val logDataByteBufferSize = logDataByteBuffer.remaining()

                            //如果处理后的日志数据字节数超过设置的缓冲区字节数，则直接追加到主日志文件，不再经过缓冲区
                            if (logDataByteBufferSize > cacheFileSize) {
                                appendByteBufferToCurrentFile(logDataByteBuffer)
                            } else {
                                //如果缓冲区剩余字节数不足以写入当前数据，则先将缓冲区数据合入主文件后再进行写入
                                if (logDataByteBufferSize > (cacheFileBuffer?.remaining() ?: 0)) {
                                    mergeCacheFile()
                                }

                                //将数据写入缓冲区
                                cacheFileBuffer?.put(logDataByteBuffer)
                                cacheFileBuffer?.force()

                                //记录上次写入位置
                                wPosMMKV.encode(
                                    Constant.CACHE_FILE_LAST_WRITE_POS_KEY + logSuffix,
                                    cacheFileBuffer?.position() ?: 0
                                )
                            }

                            writeSuccess = true
                        }, onLockFailed = {
                            Thread.sleep(100)
                        })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                //如果已经被标记为关闭，则不再读取并写日志
                if (closeFlag) {
                    break
                }
            }

            //释放资源
            close()
        }
    }

    /**
     * 将缓冲区数据追加到主文件
     */
    private fun mergeCacheFile() {
        try {
            cacheFileBuffer?.let { cacheFileBuffer ->
                //获取缓冲区有效数据长度
                val cacheFileBufferCurPos = cacheFileBuffer.position()

                //如果缓冲区有效数据长度大于0，则将其合并到主文件
                if (cacheFileBufferCurPos > 0) {
                    //将缓冲区切换到读模式
                    cacheFileBuffer.flip()

                    //将缓冲区中有效数据提取出来
                    val cacheFileByteBuffer = ByteBuffer.allocate(cacheFileBufferCurPos)
                    cacheFileByteBuffer.put(cacheFileBuffer)
                    cacheFileByteBuffer.flip()

                    //追加数据到主日志文件
                    appendByteBufferToCurrentFile(cacheFileByteBuffer)

                    //清空缓冲区
                    cacheFileBuffer.clear()

                    //重置写入位置
                    wPosMMKV.encode(
                        Constant.CACHE_FILE_LAST_WRITE_POS_KEY + logSuffix,
                        0
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 追加数据到主日志文件
     * @param data 要追加的数据
     */
    private fun appendByteBufferToCurrentFile(data: ByteBuffer) {
        try {
            currentFileChannel?.write(data)
            currentFileChannel?.force(true)

            //如果主日志文件大小超出设置的最大值，则创建新的主文件
            if ((currentFile?.length() ?: 0) > maxFileSize) {
                fileNumber++
                createNewLogFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 读取日志内容(需要在子线程中执行，并且只适合读取实时日志这类小数据的文件)
     * @param startTimeSeconds 起始的时间戳（秒）
     * @param endTimeSeconds 截止的时间戳（秒）
     * @param successCallBack 回调日志内容列表
     * @param errorCallBack 失败时的回调
     */
    fun readLog(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        successCallBack: (logList: MutableList<Log>) -> Unit,
        errorCallBack: () -> Unit,
    ) {
        try {
            logLock.runUnderWriteLock(onLock = {
                logLock.runUnderReadLock(onLock = {
                    //先将缓冲区数据追加到主文件
                    mergeCacheFile()

                    val logList: MutableList<Log> = mutableListOf()

                    getLogFilesInRange(startTimeSeconds, endTimeSeconds).forEach { file ->
                        logList.addAll(readLogFile(file))
                        //如果是实时日志，在读取后需要将文件内容清空
                        if (!builder.isOfflineLog) {
                            RandomAccessFile(file, "rw").use { raf ->
                                raf.setLength(0)
                            }
                        }
                    }

                    //如果是实时日志，在读取后需要切换到当日的第一个文件，例如切换到20240606-001-tmp.zlog
                    if (!builder.isOfflineLog) {
                        fileNumber = 1
                        createNewLogFile()
                    }

                    successCallBack.invoke(logList)
                }, onLockFailed = {
                    errorCallBack.invoke()
                })
            }, onLockFailed = {
                errorCallBack.invoke()
            })
        } catch (e: Exception) {
            e.printStackTrace()
            errorCallBack.invoke()
        }
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
     * @param successCallBack 第一个值是是否成功，如果文件不存在第二个参数返回true
     * @param errorCallBack 失败时的回调
     */
    fun zipLogFiles(
        startTimeSeconds: Long = System.currentTimeMillis() / 1000,
        endTimeSeconds: Long = startTimeSeconds,
        outputFilePath: String,
        successCallBack: (isSuccess: Boolean, noFile: Boolean) -> Unit,
        errorCallBack: () -> Unit,
    ) {
        try {
            logLock.runUnderWriteLock(onLock = {
                logLock.runUnderReadLock(onLock = {
                    //先将缓冲区数据追加到主文件
                    mergeCacheFile()

                    val fileList = getLogFilesInRange(startTimeSeconds, endTimeSeconds)
                    if (fileList.isEmpty()) {
                        successCallBack.invoke(false, true)
                    } else {
                        successCallBack.invoke(ZIPUtil.zipFiles(fileList, outputFilePath), false)
                    }
                }, onLockFailed = {
                    errorCallBack.invoke()
                })
            }, onLockFailed = {
                errorCallBack.invoke()
            })
        } catch (e: Exception) {
            e.printStackTrace()
            errorCallBack.invoke()
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
        endTimeSeconds: Long,
    ): List<File> {
        val logFilesInRange = mutableListOf<File>()

        try {
            val startOfDaySeconds = getDaySeconds(startTimeSeconds, true)
            val endOfDaySeconds = getDaySeconds(endTimeSeconds, false)

            val filePattern = "^(\\d{8})-(\\d{3})?${logSuffix}$".toRegex()

            rootDir.listFiles()?.filter { file ->
                val matchResult = filePattern.find(file.name)
                matchResult != null && matchResult.groupValues.size == 3
            }?.forEach { file ->
                filePattern.find(file.name)?.let { matchResult ->
                    val fileDate = (dateFormat.parse(matchResult.groupValues[1])?.time ?: 0) / 1000
                    if (fileDate in startOfDaySeconds..endOfDaySeconds) {
                        logFilesInRange.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        if (closeFlag) {
            return
        }
        writeChannel.trySend(log.toByteArray())
    }

    /**
     * 释放资源
     */
    fun close() {
        try {
            logLock.runUnderWriteLock(onLock = {
                writeChannel.close()
                coroutineScope.cancel()

                closeFile()

                cacheFile?.close()
                cacheFile = null
            }, onLockFailed = {
                closeFlag = true
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 关闭当前的文件占用
     */
    private fun closeFile() {
        try {
            currentFileChannel?.close()
            currentFileChannel = null

            currentFile?.close()
            currentFile = null
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
        var compress: Boolean = true
            private set
        var secretKey: String = ""
            private set

        /**
         * 设置日志根目录
         * @param rootPath 日志根目录
         * @return Builder
         */
        fun rootPath(rootPath: String) = apply {
            this.rootPath = rootPath
        }

        /**
         * 设置日志过期时间
         * @param expireDays 日志过期时间（天）
         * @return Builder
         */
        fun expireDays(expireDays: Int) = apply {
            this.expireDays = expireDays
        }

        /**
         * 设置是否离线日志
         * @param isOfflineLog 是否离线日志
         * @return Builder
         */
        fun isOfflineLog(isOfflineLog: Boolean) = apply {
            this.isOfflineLog = isOfflineLog
        }

        /**
         * 设置缓存文件大小
         * @param cacheFileSize 缓存文件大小（KB）
         * @return Builder
         */
        fun cacheFileSize(cacheFileSize: Int) = apply {
            this.cacheFileSize = cacheFileSize
        }

        /**
         * 设置单个日志文件最大大小
         * @param maxFileSize 单个日志文件最大大小（MB）
         * @return Builder
         */
        fun maxFileSize(maxFileSize: Int) = apply {
            this.maxFileSize = maxFileSize
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
         * 构建 ZLogUtil
         * @return ZLogUtil
         */
        fun build(): ZLogUtil {
            return ZLogUtil(this)
        }
    }
}