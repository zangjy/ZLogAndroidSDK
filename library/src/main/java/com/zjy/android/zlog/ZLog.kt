package com.zjy.android.zlog

import android.app.Application
import com.tencent.mmkv.MMKV
import com.zjy.android.zlog.bean.PutOnlineLogReqBean
import com.zjy.android.zlog.builder.ZLogBuilder
import com.zjy.android.zlog.constant.Constant
import com.zjy.android.zlog.ext.printToConsole
import com.zjy.android.zlog.model.GetTaskModel
import com.zjy.android.zlog.proto.LogOuterClass.Log
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.AppUtil
import com.zjy.android.zlog.util.DeviceUtil
import com.zjy.android.zlog.util.ThreadUtil
import com.zjy.android.zlog.zlog.BaseLog
import com.zjy.android.zlog.zlog.OfflineLog
import com.zjy.android.zlog.zlog.OnlineLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 文件名：ZLogHelper
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
object ZLog {

    /**
     * 是否正在上传文件
     */
    private var isUploading = false

    /**
     * 延迟第一次执行的时间
     */
    private val initialDelayMillis by lazy {
        15 * 1000L
    }

    /**
     * 定时查询回捞任务和上传实时日志的时间间隔（毫秒）
     */
    private val executorMillis by lazy {
        30 * 1000L
    }

    /**
     * 定时查询回捞任务和上传实时日志
     */
    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor()
    }

    /**
     * 默认的MMKV
     */
    private val defaultMMKV by lazy {
        MMKV.mmkvWithID(Constant.DEFAULT_MMKV_NAME)
    }

    /**
     * App的版本
     */
    private val appVersion by lazy {
        AppUtil.getAppVersionName()
    }

    /**
     * 设备信息
     */
    private val systemVersion by lazy {
        DeviceUtil.getSDKVersionName() + "(" + DeviceUtil.getSDKVersionCode() + ")"
    }

    /**
     * 计数器变量，用于生成唯一的序列值
     */
    private val seq by lazy {
        AtomicLong()
    }

    /**
     * 离线日志实例
     */
    private val offlineLog by lazy {
        OfflineLog(
            ZLogBuilder.Builder()
                .rootDir("zlog") //设置日志根目录
                .offlineFilesExpireMillis(7 * 24 * 60 * 60 * 1000) //设置日志过期时间，单位为毫秒
                .isOfflineLog(true) //设置是否离线日志
                .cacheFileSizeBytes(64 * 1024) //设置缓存文件大小，单位为字节
                .maxFileSizeBytes(50 * 1024 * 1024) //设置单个日志文件最大大小，单位为字节
                .compress(true) //设置是否压缩日志
                .secretKey(defaultMMKV.decodeString(Constant.SHARED_SECRET_KEY) ?: "") //设置加密密钥
                .build()
        )
    }

    /**
     * 实时日志实例
     */
    private val onlineLog by lazy {
        OnlineLog(
            ZLogBuilder.Builder()
                .rootDir("zlog") //设置日志根目录
                .isOfflineLog(false) //设置是否离线日志
                .cacheFileSizeBytes(2048 * 1024) //设置缓存文件大小，单位为字节
                .compress(true) //设置是否压缩日志
                .secretKey(defaultMMKV.decodeString(Constant.SHARED_SECRET_KEY) ?: "") //设置加密密钥
                .build()
        )
    }

    /**
     * 附加字段
     */
    private var identify = ""

    /**
     * 直接使用 [ZLog] 预初始化，不需要再调用 [App.setApplication]
     * @param application Application
     */
    fun preInit(application: Application) {
        App.setApplication(application)
        MMKV.initialize(application)
        identify = defaultMMKV.decodeString(Constant.IDENTIFY_VALUE_KEY) ?: ""
    }

    /**
     * 直接使用 [ZLog] 初始化，不需要再调用 [App.init]
     * @param hostUrl 服务端地址
     * @param appId 应用ID
     * @param enableConsole 是否将日志输出到控制台
     */
    fun init(hostUrl: String, appId: String, enableConsole: Boolean) {
        App.init(hostUrl, appId, enableConsole)

        //如果应用ID和上次不一致时清除默认MMVM文件内的数据
        defaultMMKV.run {
            if ((decodeString(Constant.APP_ID_KEY) ?: "") != appId) {
                clearAll()
                encode(Constant.APP_ID_KEY, appId)
            }
        }

        //SessionId和共享密钥均存在时和服务端进行交互验证密钥，验证失败重新进行设备注册
        getRegisterInfo().let {
            if (it.third) {
                App.getGlobalVM().verifySharedKey(it.first, it.second, onError = {
                    App.getGlobalVM().getSessionId()
                })
            } else {
                App.getGlobalVM().getSessionId()
            }
        }

        //定时查询回捞任务和上传实时日志
        executor.scheduleWithFixedDelay({
            putOnlineLog()
            getLogTask()
        }, initialDelayMillis, executorMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * 写离线日志
     * @param level 日志级别
     * @param msg 日志消息
     */
    fun writeOfflineLog(
        level: Log.Level,
        msg: String,
    ) {
        val (className, lineNumber) = getCallingMethodInfo()

        val serializeLog = serializeLog(level, "$className:$lineNumber", msg)

        if (App.enableConsole()) {
            serializeLog.printToConsole()
        }

        offlineLog.writeLog(serializeLog)
    }

    /**
     * 写实时日志
     * @param level 日志级别
     * @param msg 日志消息
     * @param syncDataToOfflineLog 是否同步数据到离线日志中
     */
    fun writeOnlineLog(
        level: Log.Level,
        msg: String,
        syncDataToOfflineLog: Boolean = true,
    ) {
        val (className, lineNumber) = getCallingMethodInfo()

        val serializeLog = serializeLog(level, "$className:$lineNumber", msg)

        if (App.enableConsole()) {
            serializeLog.printToConsole()
        }

        onlineLog.writeLog(serializeLog)

        if (syncDataToOfflineLog) {
            offlineLog.writeLog(serializeLog)
        }
    }

    /**
     * 获取调用该函数的方法的文件名和行号信息
     * @return 包含文件名和行号的 Pair 对象
     */
    private fun getCallingMethodInfo(): Pair<String, Int> {
        val stackTrace = Thread.currentThread().stackTrace

        val currentClassName = this::class.java.name

        var foundCurrentClass = false

        //遍历堆栈，找到在本类之后的第一个不是当前类的元素
        for (element in stackTrace) {
            val className = element.className

            if (foundCurrentClass && className != currentClassName) {
                val fileName = element.fileName
                val lineNumber = element.lineNumber
                return Pair(fileName, lineNumber)
            }

            if (className == currentClassName) {
                foundCurrentClass = true
            }
        }

        //如果无法获取调用者信息，仍然返回一个 Pair 对象，其中元素为空字符串和-1
        return Pair("", 0)
    }

    /**
     * 将日志序列化为字节数组
     * @param level 日志级别
     * @param tag 日志标签
     * @param msg 日志消息
     * @return 序列化后的字节数组
     */
    private fun serializeLog(level: Log.Level, tag: String, msg: String): Log {
        val builder = Log.newBuilder()
            .setSequence(getSeqValue())
            .setAppVersion(appVersion)
            .setTimestamp(System.currentTimeMillis() / 1000)
            .setLogLevel(level)
            .setIdentify(identify)
            .setTag(tag)
            .setMsg(msg)

        if (App.isInitSuccess()) {
            builder.systemVersion = systemVersion
        }

        return builder.build()
    }

    /**
     * 获取seq的值，并在达到最大值时重置为0
     * @return 下一个seq的值
     */
    private fun getSeqValue(): Long {
        val maxSeqValue = Long.MAX_VALUE

        val currentValue = seq.get()
        val nextValue = if (currentValue == maxSeqValue) 0 else currentValue + 1

        seq.compareAndSet(currentValue, nextValue)

        return nextValue
    }

    /**
     * 上传实时日志
     */
    private fun putOnlineLog() {
        onlineLog.readLog(successCallBack = { logList ->
            if (getRegisterInfo().third) {
                val reqBean = PutOnlineLogReqBean()

                for (log in logList) {
                    reqBean.data.add(
                        PutOnlineLogReqBean.PutOnlineLogBean(
                            sequence = log.sequence,
                            system_version = log.systemVersion,
                            app_version = log.appVersion,
                            time_stamp = log.timestamp,
                            log_level = log.logLevelValue,
                            identify = log.identify,
                            tag = log.tag,
                            msg = log.msg
                        )
                    )
                }

                if (reqBean.data.isNotEmpty()) {
                    App.getGlobalVM().putOnlineLog(reqBean, onComplete = {})
                }
            }
        }, errorCallBack = {})
    }

    /**
     * 查询日志回捞任务
     */
    private fun getLogTask() {
        if (getRegisterInfo().third) {
            App.getGlobalVM().getTask {
                if (!isUploading) {
                    ThreadUtil.runOnBackgroundThread(task = {
                        isUploading = true

                        val latch = CountDownLatch(1)
                        uploadLogFile(it[0]) {
                            latch.countDown()
                        }
                        latch.await()

                        isUploading = false
                    })
                }
            }
        }
    }

    /**
     * 执行日志上传任务
     * @param taskInfo 任务信息
     * @param onComplete 完成时的回调
     */
    private fun uploadLogFile(taskInfo: GetTaskModel.GetTaskInfo, onComplete: () -> Unit) {
        //保存Zip的位置
        val filePath =
            App.getApplication().cacheDir.absolutePath + File.separator + taskInfo.taskId + ".zip"

        offlineLog.zipLogFiles(
            taskInfo.startTime * 1000,
            taskInfo.endTime * 1000,
            filePath,
            successCallBack = { isSuccess, noFile ->
                if (isSuccess) {
                    //待上传的日志文件
                    val zipFile = File(filePath)

                    //上传文件
                    App.getGlobalVM().uploadLogFile(
                        zipFile = zipFile,
                        onComplete = {
                            //删除临时文件
                            if (zipFile.exists()) {
                                zipFile.delete()
                            }
                            onComplete.invoke()
                        }
                    )
                } else if (noFile) {
                    //反馈问题给服务端
                    App.getGlobalVM().uploadLogFileErrCallBack(
                        taskId = taskInfo.taskId,
                        msg = "对应时间段的日志文件列表为空",
                        onComplete = {
                            onComplete.invoke()
                        }
                    )
                } else {
                    onComplete.invoke()
                }
            },
            errorCallBack = {
                onComplete.invoke()
            }
        )
    }

    /**
     * 获取SessionId、共享密钥以及是否已注册
     * @return 返回信息
     */
    private fun getRegisterInfo(): Triple<String, String, Boolean> {
        val sessionId = defaultMMKV.decodeString(Constant.SESSION_ID_KEY) ?: ""
        val sharedSecret = defaultMMKV.decodeString(Constant.SHARED_SECRET_KEY) ?: ""
        return Triple(sessionId, sharedSecret, sessionId.isNotEmpty() && sharedSecret.isNotEmpty())
    }

    /**
     * 直接使用 [ZLog] 关闭，就不需要调用 [ZLogUtil] 的关闭方法了
     */
    fun close() {
        executor.shutdown()
        offlineLog.close()
        onlineLog.close()
        App.getGlobalVM().close()
    }

    /**
     * 更改附加字段
     * @param identify 可以是用户手机号等标识用户身份的内容
     */
    fun changeIdentifyValue(identify: String) {
        defaultMMKV.encode(Constant.IDENTIFY_VALUE_KEY, identify)

        ZLog.identify = identify
    }

    /**
     * 更改离线日志强制执行类型
     * @param type 要设置的强制执行类型
     */
    fun changeOfflineLogForceType(type: BaseLog.ForceType) {
        offlineLog.changeForceType(type)
    }

    /**
     * 更改实时日志强制执行类型
     * @param type 要设置的强制执行类型
     */
    fun changeOnlineLogForceType(type: BaseLog.ForceType) {
        onlineLog.changeForceType(type)
    }

    /**
     * 将缓存文件缓冲区数据强制写入磁盘
     */
    fun forceOfflineLog() {
        offlineLog.force()
    }

    /**
     * 将缓存文件缓冲区数据强制写入磁盘
     */
    fun forceOnlineLog() {
        onlineLog.force()
    }
}