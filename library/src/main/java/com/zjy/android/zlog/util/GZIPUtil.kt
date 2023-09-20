package com.zjy.android.zlog.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 文件名：CompressionUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/5
 * 描述：
 */
object GZIPUtil {
    /**
     * 压缩字节数组
     * @param data 要压缩的字节数组
     * @return 压缩后的字节数组
     */
    fun compressBytes(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return ByteArray(0)
        }
        return try {
            val outputStream = ByteArrayOutputStream()
            val gzipOutputStream = GZIPOutputStream(outputStream)
            gzipOutputStream.write(data)
            gzipOutputStream.close()
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * 压缩字符串并进行Base64编码
     * @param data 要压缩的字符串
     * @return 压缩字符串并进行Base64编码
     */
    fun compressString(data: String): String {
        if (data.isEmpty()) {
            return ""
        }
        return try {
            val compressedData = compressBytes(data.toByteArray())
            Base64.encodeToString(compressedData, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 解压缩字节数组数组
     * @param data 压缩后的字节数组
     * @return 解压缩后的字节数组
     */
    fun deCompressBytes(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return ByteArray(0)
        }
        return try {
            val inputStream = ByteArrayInputStream(data)
            val gzipInputStream = GZIPInputStream(inputStream)
            val buffer = ByteArray(1024)
            val output = ByteArrayOutputStream()
            var bytesRead: Int
            while (gzipInputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * Base64解码数据并进行解压缩数据
     * @param data Base64编码并压缩后的字符串
     * @return 解码并解压缩后的字符串数据
     */
    fun deCompressString(data: String): String {
        if (data.isEmpty()) {
            return ""
        }
        return try {
            val compressedData = Base64.decode(data, Base64.NO_WRAP)
            val decompressedData = deCompressBytes(compressedData)
            String(decompressedData)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}