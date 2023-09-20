package com.zjy.android.zlog.viewmodel

import androidx.lifecycle.ViewModel
import com.zjy.android.zlog.bean.PutOnlineLogReqBean
import com.zjy.android.zlog.constant.SPConstant
import com.zjy.android.zlog.interceptor.ZLogNetInterceptor
import com.zjy.android.zlog.model.ExchangePubKeyModel
import com.zjy.android.zlog.model.GetTaskModel
import com.zjy.android.zlog.net.Api
import com.zjy.android.zlog.util.AESUtil
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.DeviceUtil
import com.zjy.android.zlog.util.ECDHUtil
import com.zjy.xbase.ext.doAsync
import com.zjy.xbase.net.RetrofitClient
import kotlinx.coroutines.Job
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 文件名：ZLogGlobalVM
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/15
 * 描述：
 */
class ZLogGlobalVM : ViewModel() {
    companion object {
        /**
         * OkHttpClient
         */
        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .addInterceptor(ZLogNetInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * 网络请求的实例
         */
        val client: Api by lazy {
            RetrofitClient.getService(defaultOkHttpClient, Api.TEST_BASE_URL, Api::class.java)
        }
    }

    /**
     * 网络请求列表
     */
    private val jobList: MutableList<Job> = mutableListOf()

    /**
     * 重新和服务端交换公钥后计算共享密钥，然后进行设备注册，获得SessionId
     */
    fun getSessionId() {
        //清除本地数据
        App.getSp().clear(true)
        //本地密钥对
        val keyPair = ECDHUtil.generateKeyPair()
        //和服务端交换后获取TmpSessionId和ServerPubKey
        exchangePubKey(keyPair.first) { exchangePubKeyModel ->
            if (exchangePubKeyModel.isSuccessAndNotEmpty()) {
                //计算共享密钥
                val sharedSecret = ECDHUtil.generateSharedSecret(
                    exchangePubKeyModel.serverPubKey, keyPair.second
                )
                //如果成功，则验证一下共享密钥
                if (sharedSecret.isNotEmpty()) {
                    //验证共享密钥
                    verifySharedKey(
                        exchangePubKeyModel.tmpSessionId,
                        sharedSecret,
                        onSuccess = {
                            //保存共享密钥到本地
                            App.getSp().putString(
                                SPConstant.SHARED_SECRET_KEY,
                                sharedSecret,
                                true
                            )
                            //保存TmpSessionId到本地
                            App.getSp().putString(
                                SPConstant.TMP_SESSION_ID_KEY,
                                exchangePubKeyModel.tmpSessionId,
                                true
                            )
                            //进行设备注册
                            deviceRegister { sessionId ->
                                //保存SessionId到本地
                                App.getSp().putString(SPConstant.SESSION_ID_KEY, sessionId, true)
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 和服务端交换公钥
     * @param clientPubKey 客户端生成的公钥
     * @param onSuccess 成功回调
     */
    private fun exchangePubKey(
        clientPubKey: String,
        onSuccess: (model: ExchangePubKeyModel) -> Unit
    ) = jobList.add(
        doAsync({
            client.exchangePubKey(mutableMapOf("client_pub_key" to clientPubKey))
        }, onSuccess = {
            onSuccess(it)
        })
    )

    /**
     * 共享密钥验证
     * @param tmpSessionId 交换公钥后的TmpSessionId
     * @param sharedSecret 共享密钥
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    fun verifySharedKey(
        tmpSessionId: String,
        sharedSecret: String,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val verifyData = "测试数据"
        jobList.add(
            doAsync({
                client.verifySharedKey(
                    mutableMapOf(
                        "tmp_session_id" to tmpSessionId,
                        "verify_data" to AESUtil.encryptString(verifyData, sharedSecret)
                    )
                )
            }, onSuccess = {
                if (it.isSuccessAndNotEmpty() && it.decryptData == verifyData) {
                    onSuccess()
                } else {
                    onError()
                }
            })
        )
    }

    /**
     * 设备注册
     */
    private fun deviceRegister(
        onSuccess: (sessionId: String) -> Unit
    ) = jobList.add(
        doAsync({
            client.deviceRegister(
                mutableMapOf(
                    "app_id" to App.getAppId(),
                    "device_type" to 1,
                    "device_name" to "${DeviceUtil.getManufacturer()} ${DeviceUtil.getModel()}",
                    "device_id" to DeviceUtil.getUniqueDeviceId()
                )
            )
        }, onSuccess = {
            if (it.sessionId.isNotEmpty()) {
                onSuccess(it.sessionId)
            }
        })
    )

    /**
     * 上传实时日志
     * @param reqBean 日志数据
     */
    fun putOnlineLog(reqBean: PutOnlineLogReqBean) = jobList.add(
        doAsync({
            client.putOnlineLog(reqBean)
        }, onSuccess = {

        })
    )

    /**
     * 获取任务列表
     */
    fun getTask(
        onSuccess: (data: MutableList<GetTaskModel.GetTaskInfo>) -> Unit
    ) = jobList.add(
        doAsync({
            client.getTask()
        }, onSuccess = {
            if (it.data.isNotEmpty()) {
                onSuccess(it.data)
            }
        })
    )

    /**
     * 上传日志文件
     * @param zipFile 压缩后的日志文件
     * @param onSuccess 成功的回调
     * @param onComplete 完成的回调
     */
    fun uploadLogFile(
        zipFile: File,
        onSuccess: () -> Unit,
        onComplete: () -> Unit,
    ) = jobList.add(
        doAsync({
            client.uploadLogFile(
                MultipartBody.Part.createFormData(
                    "file",
                    zipFile.name,
                    RequestBody.create(
                        MediaType.parse("multipart/form-data"),
                        zipFile
                    )
                )
            )
        }, onSuccess = {
            if (it.status == SPConstant.SUCCESS_CODE) {
                onSuccess()
            }
        }, onComplete = {
            onComplete()
        })
    )

    /**
     * 日志无法上传时的反馈
     * @param taskId 任务ID
     * @param msg 失败的原因
     * @param onComplete 完成时的回调
     */
    fun uploadLogFileErrCallBack(
        taskId: String,
        msg: String,
        onComplete: () -> Unit
    ) = jobList.add(
        doAsync({
            client.uploadLogFileErrCallBack(
                mutableMapOf(
                    "task_id" to taskId,
                    "msg" to msg
                )
            )
        }, onSuccess = {

        }, onComplete = {
            onComplete()
        })
    )

    /**
     * 结束所有任务
     */
    fun close() = jobList.forEach { it.cancel() }
}