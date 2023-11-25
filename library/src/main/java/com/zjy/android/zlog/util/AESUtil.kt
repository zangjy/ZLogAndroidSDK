package com.zjy.android.zlog.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 文件名：AESUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/4
 * 描述：
 */
object AESUtil {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CFB/NoPadding"
    private const val IV_LENGTH = 16

    /**
     * 使用AES算法加密数据
     * @param data 要加密的数据
     * @param key 密钥
     * @return Base64编码的加密结果
     */
    fun encryptString(data: String, key: String): String {
        if (data.isEmpty() || key.isEmpty()) {
            return ""
        }
        return try {
            val iv = generateRandomIV()

            val adjustedKey = adjustKeySize(key)

            val secretKeySpec = SecretKeySpec(adjustedKey.toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))

            val encryptedBytes = cipher.doFinal(data.toByteArray())
            val combined = combineIVAndEncryptedData(iv, encryptedBytes)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 使用AES算法加密数据
     * @param data 要加密的数据
     * @param key 密钥
     * @return 字节数组的加密结果
     */
    fun encryptBytes(data: ByteArray, key: String): ByteArray {
        if (data.isEmpty() || key.isEmpty()) {
            return ByteArray(0)
        }
        return try {
            val iv = generateRandomIV()

            val adjustedKey = adjustKeySize(key)

            val secretKeySpec = SecretKeySpec(adjustedKey.toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))

            val encryptedBytes = cipher.doFinal(data)
            combineIVAndEncryptedData(iv, encryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * 使用AES算法解密数据
     * @param data Base64编码的加密数据
     * @param key 密钥
     * @return 解密后的明文数据
     */
    fun decryptString(data: String, key: String): String {
        if (data.isEmpty() || key.isEmpty()) {
            return ""
        }
        return try {
            val combined = Base64.decode(data, Base64.NO_WRAP)
            val iv = ByteArray(IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - IV_LENGTH)

            System.arraycopy(combined, 0, iv, 0, iv.size)
            System.arraycopy(combined, iv.size, encryptedBytes, 0, encryptedBytes.size)

            val adjustedKey = adjustKeySize(key)

            val secretKeySpec = SecretKeySpec(adjustedKey.toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 使用AES算法解密数据
     * @param data 加密数据的字节数组
     * @param key 密钥
     * @return 解密后的明文数据字节数组
     */
    fun decryptBytes(data: ByteArray, key: String): ByteArray {
        if (data.isEmpty() || key.isEmpty()) {
            return ByteArray(0)
        }
        return try {
            val iv = ByteArray(IV_LENGTH)
            val encryptedBytes = ByteArray(data.size - IV_LENGTH)

            System.arraycopy(data, 0, iv, 0, iv.size)
            System.arraycopy(data, iv.size, encryptedBytes, 0, encryptedBytes.size)

            val adjustedKey = adjustKeySize(key)

            val secretKeySpec = SecretKeySpec(adjustedKey.toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

//    /**
//     * 处理密钥长度，确保它是16、24或32字节长
//     * @param key 原始密钥
//     * @return 符合AES密钥长度要求的密钥
//     */
//    private fun adjustKeySize(key: String): String {
//        val validKeySizes = listOf(16, 24, 32)
//        val keyLength = key.length
//
//        return when {
//            validKeySizes.contains(keyLength) -> key
//            keyLength < 16 -> key.padEnd(16, '0') // 填充0
//            keyLength < 24 -> key.padEnd(24, '0') // 填充0
//            else -> key.substring(0, 32) // 截断为32字节
//        }
//    }

    /**
     * 处理密钥长度，确保它是16字节长
     * @param key 原始密钥
     * @return 符合AES128密钥长度要求的密钥
     */
    private fun adjustKeySize(key: String): String {
        val keyLength = key.length

        return when {
            keyLength == 16 -> key
            keyLength < 16 -> key.padEnd(16, '0') // 填充0
            else -> key.substring(0, 16) // 截断为16字节
        }
    }

    /**
     * 生成随机的初始化向量 (IV)
     */
    private fun generateRandomIV(): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * 将初始化向量 (IV) 和加密数据合并成一个字节数组
     */
    private fun combineIVAndEncryptedData(iv: ByteArray, encryptedData: ByteArray): ByteArray {
        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
        return combined
    }
}