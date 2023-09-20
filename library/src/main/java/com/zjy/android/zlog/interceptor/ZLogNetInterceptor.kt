package com.zjy.android.zlog.interceptor

import com.zjy.android.zlog.constant.SPConstant
import com.zjy.android.zlog.net.ApiPaths
import com.zjy.android.zlog.util.AESUtil
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.GZIPUtil
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Okio
import java.io.IOException

/**
 * 文件名：ZLogNetInterceptor
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/16
 * 描述：
 */
class ZLogNetInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        //添加全局Header
        request = request.newBuilder()
            .addHeader("TMP_SESSION_ID", App.getSp().getString(SPConstant.TMP_SESSION_ID_KEY))
            .addHeader("SESSION_ID", App.getSp().getString(SPConstant.SESSION_ID_KEY))
            .build()
        //替换服务端地址
        request = replaceUrl(request)
        //根据URL判断是否需要加密、解密、压缩、解压缩数据
        return if (shouldProcessData(request.url().toString())) {
            val encryptedRequest = compressAndEncryptRequest(request)
            val response = chain.proceed(encryptedRequest)
            deCompressAndDecryptResponse(response)
        } else {
            chain.proceed(request)
        }
    }

    /**
     * 替换服务端地址
     */
    private fun replaceUrl(request: Request): Request {
        return try {
            var tmpRequest: Request
            HttpUrl.get(App.getHostUrl()).let { tmpHttpUrl ->
                val newHttpUrl = request.url().newBuilder()
                    .scheme(tmpHttpUrl.scheme())
                    .host(tmpHttpUrl.host())
                    .port(tmpHttpUrl.port())
                    .build()
                tmpRequest = request.newBuilder().url(newHttpUrl).build()
            }
            tmpRequest
        } catch (_: Exception) {
            request
        }
    }

    /**
     * 根据URL判断是否需要加密、解密、压缩、解压缩数据
     * @param url 请求的URL
     * @return 需要时返回true
     */
    private fun shouldProcessData(url: String): Boolean {
        val unNeedUrls = listOf(
            ApiPaths.EXCHANGE_PUB_KEY_URL,
            ApiPaths.VERIFY_SHARED_KEY_URL,
            ApiPaths.UPLOAD_LOG_FILE_URL
        )
        return unNeedUrls.none { url.endsWith(it) }
    }

    /**
     * 压缩并加密请求数据
     * @param request 压缩并加密的请求对象 [Request]
     * @return 压缩并加密后的请求对象 [Request]
     */
    private fun compressAndEncryptRequest(request: Request): Request {
        //GET请求直接返回
        if (request.method() == "GET") {
            return request
        }
        val compressedBody = compressAndEncryptRequestBody(request.body())
        return if (compressedBody == null) {
            request
        } else {
            request.newBuilder()
                .method(request.method(), compressedBody)
                .build()
        }
    }

    /**
     * 实现请求数据的压缩加密逻辑
     * @param requestBody 要加密的请求体 [RequestBody]
     * @return 加密后的请求体 [RequestBody]
     */
    private fun compressAndEncryptRequestBody(requestBody: RequestBody?): RequestBody? {
        val sharedSecret = App.getSp().getString(SPConstant.SHARED_SECRET_KEY)

        if (sharedSecret.isEmpty() || requestBody == null) {
            return requestBody
        }
        val jsonText = readJsonTextFromRequestBody(requestBody)
        val compressedData = GZIPUtil.compressString(jsonText)
        val encryptedData = AESUtil.encryptString(compressedData, sharedSecret)

        if (encryptedData.isEmpty()) {
            return requestBody
        }

        return RequestBody.create(requestBody.contentType(), encryptedData)
    }

    /**
     * 解密并解压缩响应数据
     * @param response 解密并解压缩的响应对象 [Response]
     * @return 解密并解压缩后的响应对象 [Response]
     */
    private fun deCompressAndDecryptResponse(response: Response): Response {
        val deCompressedBody = deCompressAndDecryptResponseBody(response.body())
        return if (deCompressedBody == null) {
            response
        } else {
            response.newBuilder()
                .body(deCompressedBody)
                .build()
        }
    }

    /**
     * 实现响应数据的解密和解压缩逻辑
     * @param responseBody 要解密和解压缩的响应体 [ResponseBody]
     * @return 解密和解压缩后的响应体 [ResponseBody]
     */
    private fun deCompressAndDecryptResponseBody(responseBody: ResponseBody?): ResponseBody? {
        val sharedSecret = App.getSp().getString(SPConstant.SHARED_SECRET_KEY)

        if (sharedSecret.isEmpty() || responseBody == null) {
            return responseBody
        }

        val encryptedData = readJsonTextFromResponseBody(responseBody)
        val decryptedData = AESUtil.decryptString(encryptedData, sharedSecret)
        val decompressedData = GZIPUtil.deCompressString(decryptedData)

        if (decompressedData.isEmpty()) {
            return responseBody
        }

        return ResponseBody.create(responseBody.contentType(), decompressedData)
    }

    /**
     * 从请求体 [RequestBody] 中读取 JSON 数据
     * @param requestBody 要读取的请求体 [RequestBody]
     * @return 从请求体中读取的 JSON
     */
    private fun readJsonTextFromRequestBody(requestBody: RequestBody?): String {
        if (requestBody == null) {
            return ""
        }

        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 从响应体 [RequestBody] 中读取 JSON 数据
     * @param responseBody 要读取的响应体 [ResponseBody]
     * @return 从响应体中读取的 JSON
     */
    private fun readJsonTextFromResponseBody(responseBody: ResponseBody?): String {
        if (responseBody == null) {
            return ""
        }

        return try {
            responseBody.use { body ->
                Okio.buffer(body.source()).readUtf8()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }
}