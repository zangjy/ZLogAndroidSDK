package com.zjy.android.zlog.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件名：ZipUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/12
 * 描述：
 */
object ZIPUtil {
    /**
     * 压缩文件列表为一个Zip文件
     *
     * @param filesToZip   要压缩的文件列表
     * @param outputFilePath 压缩后的Zip文件路径（包括文件名和目录）
     * @return 成功返回true，失败返回false
     */
    fun zipFiles(filesToZip: List<File>, outputFilePath: String): Boolean {
        try {
            val outputDirectory = File(outputFilePath).parentFile
            if (outputDirectory != null) {
                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs()
                }
            }

            val zipOutputStream = ZipOutputStream(FileOutputStream(outputFilePath))

            for (file in filesToZip) {
                if (file.exists() && file.isFile) {
                    // 压缩文件
                    zipFile(file, zipOutputStream)
                }
            }
            zipOutputStream.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun zipFile(file: File, zipOutputStream: ZipOutputStream) {
        val buffer = ByteArray(1024)
        val inputStream = FileInputStream(file)
        val zipEntry = ZipEntry(file.name)
        zipOutputStream.putNextEntry(zipEntry)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            zipOutputStream.write(buffer, 0, length)
        }
        inputStream.close()
    }
}