package com.zjy.android.zlog.util

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * 文件名：ECDHUtil
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/4
 * 描述：
 */
object ECDHUtil {
    /**
     * 默认的公钥
     */
    private val DEFAULT_PUBLIC_KEY by lazy {
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWuCa7IySk1avuQODG/xubWN2+vBVEKMjWhQ2Pm9iGC3nZObWct7xQfNmT6EXFk92/OyiHCwG56tQa7La+V45sA=="
    }

    /**
     * 默认的私钥
     */
    private val DEFAULT_PRIVATE_KEY by lazy {
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgW05ayM+XRxNR2z7wCxw+Bi0FNYEpT2pOHw9LpPVf7FChRANCAARa4JrsjJKTVq+5A4Mb/G5tY3b68FUQoyNaFDY+b2IYLedk5tZy3vFB82ZPoRcWT3b87KIcLAbnq1Brstr5Xjmw"
    }

    /**
     * 生成ECDH密钥对
     * @return 返回包含公钥和私钥的Pair，以Base64编码的形式
     */
    fun generateKeyPair(): Pair<String, String> {
        return try {
            //创建KeyPairGenerator实例，指定算法为"EC"
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")

            //初始化KeyPairGenerator，设置密钥长度为256位
            keyPairGenerator.initialize(256)

            //生成密钥对
            val keyPair = keyPairGenerator.generateKeyPair()

            //获取公钥和私钥
            val publicKey = keyPair.public
            val privateKey = keyPair.private

            //将公钥和私钥转换为Base64编码的字符串
            val publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            val privateKeyBase64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)

            //返回公钥和私钥的Pair
            Pair(publicKeyBase64, privateKeyBase64)
        } catch (e: Exception) {
            e.printStackTrace()
            //处理异常，返回默认值
            Pair(DEFAULT_PUBLIC_KEY, DEFAULT_PRIVATE_KEY)
        }
    }

    /**
     * 计算ECDH共享密钥
     * @param publicKeyBase64 对方的公钥，以Base64编码的形式
     * @param privateKeyBase64 自己的私钥，以Base64编码的形式
     * @return 返回计算得到的共享密钥，以Base64编码的形式
     */
    fun generateSharedSecret(publicKeyBase64: String, privateKeyBase64: String): String {
        return try {
            //创建KeyFactory实例，指定算法为"EC"
            val keyFactory = KeyFactory.getInstance("EC")

            //将对方的公钥和自己的私钥从Base64编码的字符串转换为字节数组
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)

            //创建X509EncodedKeySpec和PKCS8EncodedKeySpec实例，用于表示公钥和私钥的KeySpec
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)

            //根据KeySpec生成对方的公钥和自己的私钥
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            val privateKey = keyFactory.generatePrivate(privateKeySpec)

            //创建KeyAgreement实例，指定算法为"ECDH"
            val keyAgreement = KeyAgreement.getInstance("ECDH")

            //使用自己的私钥初始化KeyAgreement
            keyAgreement.init(privateKey)

            //执行相位一，使用对方的公钥
            keyAgreement.doPhase(publicKey, true)

            //生成共享密钥
            val sharedSecret = keyAgreement.generateSecret()

            //将共享密钥转换为Base64编码的字符串
            Base64.encodeToString(sharedSecret, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            //处理异常，返回空
            ""
        }
    }
}