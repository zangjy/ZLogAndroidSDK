package com.zjy.android.zlog.zlog

import com.zjy.android.zlog.builder.ZLogBuilder
import com.zjy.android.zlog.proto.LogOuterClass
import com.zjy.android.zlog.util.ZLogUtil
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

/**
 * 文件名：OnlineLog
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/11
 * 描述：
 */
class OnlineLog(builder: ZLogBuilder) : BaseLog(builder) {

    override fun onWrite(
        cacheFileBuffer: MappedByteBuffer?,
        data: ByteBuffer,
        dataSize: Int,
    ) {
        //当处理后的日志数据字节数小于等于缓冲区剩余字节数时写入数据
        //实时日志应当有且只有一个相对较大的缓存文件，在读取并上传服务端后从0的位置重新写入，在正常轮询情况下不应有超出的情况
        if (dataSize <= (cacheFileBuffer?.remaining() ?: 0)) {
            //将数据写入缓冲区
            cacheFileBuffer?.put(data)
        }
    }

    override fun onReadLog(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        callBack: (logList: MutableList<LogOuterClass.Log>) -> Unit,
    ) {
        val buffer = readDataFromCacheFile()
        if (buffer == null) {
            callBack.invoke(mutableListOf())
        } else {
            callBack.invoke(ZLogUtil.readLogsFromByteBuffer(buffer, builder.secretKey))
        }
    }

    override fun onZipLogFiles(
        startTimeStampMillis: Long,
        endTimeStampMillis: Long,
        outputFilePath: String,
        callBack: (isSuccess: Boolean, noFile: Boolean) -> Unit,
    ) {
        //打包文件是离线日志的功能，实时日志直接将回调的两个参数设置为false即可
        callBack.invoke(false, false)
    }

    override fun onClose() {

    }
}