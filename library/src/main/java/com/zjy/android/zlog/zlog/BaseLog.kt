package com.zjy.android.zlog.zlog

import com.tencent.mmkv.MMKV
import com.zjy.android.zlog.builder.ZLogBuilder
import com.zjy.android.zlog.constant.Constant
import com.zjy.android.zlog.proto.LogOuterClass
import com.zjy.android.zlog.util.AESUtil
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.GZIPUtil
import com.zjy.android.zlog.util.SafeLock
import com.zjy.android.zlog.util.ZLogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 文件名：BaseLog
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/11
 * 描述：
 */
abstract class BaseLog(protected val builder: ZLogBuilder) {

    enum class ForceType {
        /**
         * 写入数据之后立即force
         */
        IMMEDIATE,

        /**
         * 通过定时器定时force
         */
        SCHEDULED
    }

    /**
     * 日志文件的根目录
     */
    protected val rootDir by lazy {
        File(
            App.getApplication().filesDir.absolutePath,
            builder.rootDir.ifEmpty { "zlog" }
        )
    }

    /**
     * 日志文件的后缀
     */
    protected val fileSuffix by lazy {
        ".zlog"
    }

    /**
     * 缓存文件名
     */
    private val cacheFileName by lazy {
        if (builder.isOfflineLog) {
            "offline_log_cache${fileSuffix}"
        } else {
            "online_log_cache${fileSuffix}"
        }
    }

    /**
     * 缓存文件的随机访问文件对象
     */
    private var cacheFile: RandomAccessFile? = null

    /**
     * 缓存文件的缓冲区
     */
    private var cacheFileBuffer: MappedByteBuffer? = null

    /**
     * 用于记录写入位置
     */
    private val wPosMMKV by lazy {
        MMKV.mmkvWithID(Constant.W_POS_MMKV_NAME)
    }

    /**
     * 日志操作的锁对象
     */
    private val logLock by lazy {
        SafeLock()
    }

    /**
     * 将缓存文件缓冲区数据强制写入磁盘的锁对象
     */
    private val forceLock by lazy {
        SafeLock()
    }

    /**
     * 默认写入数据之后立即force
     */
    private var forceType = ForceType.IMMEDIATE

    /**
     * 如果为 [ForceType.SCHEDULED] 类型时写入数据该值变为true，定时器执行任务时判断是否需要调用force
     */
    private var needForce = false

    /**
     * 延迟第一次执行的时间
     */
    private val initialDelayMillis by lazy {
        15 * 1000L
    }

    /**
     * 定时将缓存文件缓冲区数据强制写入磁盘的时间间隔（毫秒）
     */
    private val forceMillis by lazy {
        30 * 1000L
    }

    /**
     * 定时将缓存文件缓冲区数据强制写入磁盘的定时器
     */
    private var forceExecutor: ScheduledExecutorService? = null

    /**
     * 创建一个协程作用域，处理IO操作
     */
    private val ioCoroutineScope by lazy {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    /**
     * 创建一个用于协程间通信的通道，传输ByteArray类型的数据
     */
    private val writeChannel by lazy {
        Channel<ByteArray>(capacity = Channel.UNLIMITED)
    }

    /**
     * 关闭标记
     */
    private var closeFlag = false

    init {
        createBaseDir()
        createCacheFile()
        startWriting()
    }

    /**
     * 如果日志根目录不存在则先创建
     */
    private fun createBaseDir() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    /**
     * 创建缓存文件
     */
    private fun createCacheFile() {
        try {
            val logFile = File(rootDir, cacheFileName)
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            cacheFile = RandomAccessFile(logFile, "rw")

            cacheFileBuffer = cacheFile?.channel?.map(
                FileChannel.MapMode.READ_WRITE,
                0,
                builder.cacheFileSizeBytes.toLong()
            )

            cacheFileBuffer?.position(wPosMMKV.decodeInt(cacheFileName, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 写入日志
     * @param log 要写入的日志数据
     */
    fun writeLog(log: LogOuterClass.Log) {
        if (closeFlag) {
            return
        }

        writeChannel.trySend(log.toByteArray())
    }

    /**
     * 在子线程中执行写日志操作
     */
    private fun startWriting() {
        ioCoroutineScope.launch {
            for (logData in writeChannel) {
                //标记是否写入完成
                var writeComplete = false

                while (!writeComplete) {
                    //获取到写锁并写入完成时继续写下一条，否则延时100ms继续尝试获取写锁，直到完成写入
                    logLock.runUnderLock(onLock = {
                        //限制单条日志不能大于16kb
                        val truncateLogData = ZLogUtil.truncateByteArrayIfNeeded(
                            logData,
                            16
                        )

                        //是否启用压缩
                        val shouldCompress = builder.compress

                        //是否启用加密
                        val shouldEncrypt = builder.secretKey.isNotEmpty()

                        //是否启用加密标志位
                        val encryptionFlag: Byte = if (shouldEncrypt) 1 else 0

                        //是否启用压缩标志位
                        val compressionFlag: Byte = if (shouldCompress) 1 else 0

                        //如果启用压缩，则对truncateLogData进行压缩，否则直接使用原始的truncateLogData
                        val compressedData = if (shouldCompress) {
                            GZIPUtil.compressBytes(truncateLogData)
                        } else {
                            truncateLogData
                        }

                        //如果启用加密，则对compressedData进行加密，否则直接使用compressedData
                        val encryptedData = if (shouldEncrypt) {
                            AESUtil.encryptBytes(compressedData, builder.secretKey)
                        } else {
                            compressedData
                        }

                        //使用四个字节日志数据的长度
                        val lenBuffer = ByteBuffer.allocate(4).putInt(encryptedData.size)
                        lenBuffer.flip()

                        //处理后的日志数据
                        val processedDataBuffer = ByteBuffer.allocate(
                            8 + encryptedData.size
                        ).apply {
                            //头部开始标记
                            put(0xFF.toByte())
                            put(encryptionFlag)
                            put(compressionFlag)
                            put(lenBuffer)
                            //头部结束标记
                            put(0xFF.toByte())
                            put(encryptedData)
                            flip()
                        }

                        onWrite(
                            cacheFileBuffer,
                            processedDataBuffer,
                            processedDataBuffer.remaining()
                        )

                        if (forceType == ForceType.IMMEDIATE) {
                            force()
                        } else {
                            needForce = true
                        }

                        writeComplete = true
                    }, onLockFailed = {
                        Thread.sleep(100)
                    })
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
     * 读取日志内容(只适合读取实时日志这类小数据的文件)
     * @param startTimeStampMillis 起始的时间戳，如果想获取当日的，开始和结束均使用 [java.lang.System.currentTimeMillis] 即可（毫秒）
     * @param endTimeStampMillis 截止的时间戳（毫秒）
     * @param successCallBack 回调日志内容列表
     * @param errorCallBack 失败时的回调
     */
    fun readLog(
        startTimeStampMillis: Long = System.currentTimeMillis(),
        endTimeStampMillis: Long = startTimeStampMillis,
        successCallBack: (logList: MutableList<LogOuterClass.Log>) -> Unit,
        errorCallBack: () -> Unit,
    ) {
        if (closeFlag) {
            errorCallBack.invoke()
            return
        }

        ioCoroutineScope.launch {
            logLock.runUnderLock(onLock = {
                onReadLog(
                    startTimeStampMillis,
                    endTimeStampMillis,
                    successCallBack
                )
            }, onLockFailed = {
                errorCallBack.invoke()
            })
        }
    }

    /**
     * 压缩指定时间段内的日志文件
     * @param startTimeStampMillis 起始的时间戳，如果想压缩当日的，开始和结束均使用 [java.lang.System.currentTimeMillis] 即可（毫秒）
     * @param endTimeStampMillis 截止的时间戳（毫秒）
     * @param outputFilePath 压缩后的Zip文件路径
     * @param successCallBack 第一个值是是否成功，如果文件不存在第二个参数返回true
     * @param errorCallBack 失败时的回调
     */
    fun zipLogFiles(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        outputFilePath: String,
        successCallBack: (isSuccess: Boolean, noFile: Boolean) -> Unit,
        errorCallBack: () -> Unit,
    ) {
        if (closeFlag) {
            errorCallBack.invoke()
            return
        }

        ioCoroutineScope.launch {
            logLock.runUnderLock(onLock = {
                onZipLogFiles(
                    startTimeStampMillis,
                    endTimeStampMillis,
                    outputFilePath,
                    successCallBack
                )
            }, onLockFailed = {
                errorCallBack.invoke()
            })
        }
    }

    /**
     * 更改强制执行类型
     * @param type 要设置的强制执行类型
     */
    fun changeForceType(type: ForceType) {
        if (closeFlag || type == forceType) {
            return
        }

        shutdownForceExecutor()
        if (type == ForceType.SCHEDULED) {
            forceExecutor = Executors.newSingleThreadScheduledExecutor()
            forceExecutor?.scheduleWithFixedDelay({
                if (needForce) {
                    force()
                    needForce = false
                }
            }, initialDelayMillis, forceMillis, TimeUnit.MILLISECONDS)
        }

        forceType = type
    }

    /**
     * 关闭将缓存文件缓冲区数据强制写入磁盘的定时器
     */
    private fun shutdownForceExecutor() {
        forceExecutor?.let {
            if (!it.isShutdown) {
                it.shutdown()
            }
        }

        needForce = false
    }

    /**
     * 将缓存文件缓冲区数据强制写入磁盘
     */
    fun force() {
        if (closeFlag) {
            return
        }

        ioCoroutineScope.launch {
            forceLock.runUnderLock(onLock = {
                //将缓存文件缓冲区数据强制写入磁盘
                cacheFileBuffer?.force()

                //记录上次写入位置
                wPosMMKV.encode(cacheFileName, cacheFileBuffer?.position() ?: 0)
            })
        }
    }

    /**
     * 从缓存文件缓冲区读取数据
     * @return 返回缓存文件缓冲区中的数据，如果不存在有效数据返回null
     */
    protected fun readDataFromCacheFile(): ByteBuffer? {
        if (closeFlag) {
            return null
        }

        return cacheFileBuffer?.let { buffer ->
            val dataSize = buffer.position()

            if (dataSize > 0) {
                //将缓存文件缓冲区切换到读模式
                buffer.flip()

                //将缓冲区中有效数据提取出来
                val byteBuffer = ByteBuffer.allocate(dataSize)
                byteBuffer.put(buffer)
                byteBuffer.flip()

                //清空缓存文件的缓冲区
                buffer.clear()

                //重置写入位置
                wPosMMKV.encode(cacheFileName, 0)

                //将读取的数据返回
                byteBuffer
            } else {
                null
            }
        }
    }

    /**
     * 释放资源
     */
    fun close() {
        logLock.runUnderLock(onLock = {
            shutdownForceExecutor()

            writeChannel.close()
            ioCoroutineScope.cancel()

            onClose()

            cacheFile?.close()
            cacheFile = null
        }, onLockFailed = {
            closeFlag = true
        })
    }

    protected abstract fun onWrite(
        cacheFileBuffer: MappedByteBuffer?,
        data: ByteBuffer,
        dataSize: Int,
    )

    protected abstract fun onReadLog(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        callBack: (logList: MutableList<LogOuterClass.Log>) -> Unit,
    )

    protected abstract fun onZipLogFiles(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        outputFilePath: String,
        callBack: (isSuccess: Boolean, noFile: Boolean) -> Unit,
    )

    protected abstract fun onClose()
}