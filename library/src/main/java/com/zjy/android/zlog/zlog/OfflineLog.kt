package com.zjy.android.zlog.zlog

import com.zjy.android.zlog.builder.ZLogBuilder
import com.zjy.android.zlog.proto.LogOuterClass
import com.zjy.android.zlog.util.ZIPUtil
import com.zjy.android.zlog.util.ZLogUtil
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 文件名：OfflineLog
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/11
 * 描述：
 */
class OfflineLog(builder: ZLogBuilder) : BaseLog(builder) {

    /**
     * 离线日志文件当前的序号
     */
    private var offlineFileNumber = 1

    /**
     * 当前的离线日志文件
     */
    private var currentOfflineFile: RandomAccessFile? = null

    /**
     * 当前离线日志文件的通道
     */
    private var currentOfflineFileChannel: FileChannel? = null

    /**
     * 通用格式化日期
     */
    private val dateFormat by lazy {
        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    }

    init {
        val (fileNumber, lastFilePath) = ZLogUtil.cleanupExpireFilesAndGetFileNumber(
            rootDir,
            fileSuffix,
            dateFormat,
            builder.offlineFilesExpireMillis
        )

        offlineFileNumber = fileNumber

        //缓冲区的数据应被合并到上一个日志文件中，而不是今天的日志文件
        //如果lastFilePath为空，代表日志根目录下文件已经全部过期被清理掉了，此时缓冲区的数据已经没有保留的必要了
        readDataFromCacheFile()?.let { data ->
            try {
                if (lastFilePath.isNotEmpty()) {
                    File(lastFilePath).apply {
                        if (!exists()) {
                            createNewFile()
                        }
                        RandomAccessFile(this, "rw").use { raf ->
                            raf.seek(raf.length())
                            raf.channel.write(data)
                            raf.channel.force(true)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        createNewOfflineFile()
    }

    /**
     * 创建新的日志文件
     */
    private fun createNewOfflineFile() {
        try {
            val logFile = getNewOfflineFile()
            if (!logFile.exists()) {
                logFile.createNewFile()
            }

            closeFile()

            currentOfflineFile = RandomAccessFile(logFile, "rw")

            currentOfflineFile?.seek(currentOfflineFile!!.length())

            currentOfflineFileChannel = currentOfflineFile?.channel
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取新的日志文件
     * @return File 返回对应文件，文件名示例：20240606-001.zlog、20240606-002.zlog
     */
    private fun getNewOfflineFile() = File(
        rootDir,
        "${dateFormat.format(System.currentTimeMillis())}-${formatFileNumber()}${fileSuffix}"
    )

    /**
     * 格式化日志文件序号
     * @return String 返回格式化后的序号，不足三位前面补0
     */
    private fun formatFileNumber(): String {
        return String.format(Locale.getDefault(), "%03d", offlineFileNumber)
    }

    /**
     * 关闭当前的离线日志文件
     */
    private fun closeFile() {
        currentOfflineFileChannel?.close()
        currentOfflineFileChannel = null

        currentOfflineFile?.close()
        currentOfflineFile = null
    }

    /**
     * 将缓冲区数据追加到当前的离线文件
     */
    private fun mergeTmpDataToCurrentOfflineFile() {
        readDataFromCacheFile()?.let {
            appendDataToCurrentOfflineFile(it)
        }
    }

    /**
     * 追加数据到当前的离线文件
     * @param data 要追加的数据
     */
    private fun appendDataToCurrentOfflineFile(data: ByteBuffer) {
        currentOfflineFileChannel?.write(data)
        currentOfflineFileChannel?.force(true)

        if ((currentOfflineFile?.length() ?: 0) > builder.maxFileSizeBytes) {
            offlineFileNumber++
            createNewOfflineFile()
        }
    }

    override fun onWrite(cacheFileBuffer: MappedByteBuffer?, data: ByteBuffer, dataSize: Int) {
        //如果处理后的日志数据字节数超过设置的缓冲区字节数，则直接追加到主日志文件，不再经过缓冲区
        if (dataSize > builder.cacheFileSizeBytes) {
            appendDataToCurrentOfflineFile(data)
        } else {
            //如果缓冲区剩余字节数不足以写入当前数据，则先将缓冲区数据合入主文件后再进行写入
            if (dataSize > (cacheFileBuffer?.remaining() ?: 0)) {
                mergeTmpDataToCurrentOfflineFile()
            }

            //将数据写入缓冲区
            cacheFileBuffer?.put(data)
        }
    }

    override fun onReadLog(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        callBack: (logList: MutableList<LogOuterClass.Log>) -> Unit,
    ) {
        //先将缓冲区数据追加到主文件
        mergeTmpDataToCurrentOfflineFile()

        val logList: MutableList<LogOuterClass.Log> = mutableListOf()

        ZLogUtil.getLogFilesInRange(
            rootDir,
            fileSuffix,
            dateFormat,
            startTimeStampMillis,
            endTimeStampMillis
        ).forEach { file ->
            logList.addAll(ZLogUtil.readLogsFromFile(file, builder.secretKey))
        }

        callBack.invoke(logList)
    }

    override fun onZipLogFiles(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        outputFilePath: String,
        callBack: (isSuccess: Boolean, noFile: Boolean) -> Unit,
    ) {
        //先将缓冲区数据追加到主文件
        mergeTmpDataToCurrentOfflineFile()

        val fileList = ZLogUtil.getLogFilesInRange(
            rootDir,
            fileSuffix,
            dateFormat,
            startTimeStampMillis,
            endTimeStampMillis
        )

        if (fileList.isEmpty()) {
            callBack.invoke(false, true)
        } else {
            callBack.invoke(ZIPUtil.zipFiles(fileList, outputFilePath), false)
        }
    }

    override fun onClose() {
        closeFile()
    }
}