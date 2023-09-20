package com.zjy.android.zlog.net

import com.zjy.android.zlog.bean.PutOnlineLogReqBean
import com.zjy.android.zlog.model.BaseModel
import com.zjy.android.zlog.model.DeviceRegisterModel
import com.zjy.android.zlog.model.ExchangePubKeyModel
import com.zjy.android.zlog.model.GetTaskModel
import com.zjy.android.zlog.model.VerifySharedKeyModel
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 文件名：Api
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/5
 * 描述：
 */
interface Api {
    companion object {
        /**
         * 需要在初始化时传入服务端地址
         */
        const val TEST_BASE_URL = "http://127.0.0.1:20000/"
    }

    /**
     * 交换公钥
     */
    @POST(ApiPaths.EXCHANGE_PUB_KEY_URL)
    suspend fun exchangePubKey(@Body reqData: MutableMap<String, Any>): ExchangePubKeyModel

    /**
     * 共享密钥验证
     */
    @POST(ApiPaths.VERIFY_SHARED_KEY_URL)
    suspend fun verifySharedKey(@Body reqData: MutableMap<String, Any>): VerifySharedKeyModel

    /**
     * 设备注册
     */
    @POST(ApiPaths.DEVICE_REGISTER_URL)
    suspend fun deviceRegister(@Body reqData: MutableMap<String, Any>): DeviceRegisterModel

    /**
     * 上传实时日志
     */
    @POST(ApiPaths.PUT_ONLINE_LOG_URL)
    suspend fun putOnlineLog(@Body reqBean: PutOnlineLogReqBean): BaseModel

    /**
     * 查询回捞任务
     */
    @GET(ApiPaths.GET_TASK_URL)
    suspend fun getTask(@Query("device_type") deviceType: Int = 1): GetTaskModel

    /**
     * 上传日志文件
     */
    @Multipart
    @POST(ApiPaths.UPLOAD_LOG_FILE_URL)
    suspend fun uploadLogFile(@Part part: MultipartBody.Part): BaseModel

    /**
     * 日志无法上传时的反馈
     */
    @POST(ApiPaths.UPLOAD_LOG_FILE_ERR_CALLBACK_URL)
    suspend fun uploadLogFileErrCallBack(@Body reqData: MutableMap<String, Any>): BaseModel
}