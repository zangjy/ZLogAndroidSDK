package com.zjy.android.zlog

import android.app.Application
import com.zjy.android.zlog.bean.PutOnlineLogReqBean
import com.zjy.android.zlog.constant.SPConstant
import com.zjy.android.zlog.model.GetTaskModel
import com.zjy.android.zlog.proto.LogOuterClass.Log
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.AppUtil
import com.zjy.android.zlog.util.DeviceUtil
import com.zjy.android.zlog.util.SafeLock
import com.zjy.android.zlog.util.ThreadUtil
import com.zjy.android.zlog.util.ZLogUtil
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
     * 上传实时日志的锁对象
     */
    private val uploadLogLock by lazy {
        SafeLock()
    }

    /**
     * 上传实时任务的时间间隔（毫秒）
     */
    private val uploadLogMillis by lazy {
        60 * 1000L
    }

    /**
     * 定时上传实时日志
     */
    private val uploadLogExecutor by lazy {
        Executors.newSingleThreadScheduledExecutor()
    }

    /**
     * 上传回捞任务的锁对象
     */
    private val uploadLogFileLock by lazy {
        SafeLock()
    }

    /**
     * 定时查询回捞任务的时间间隔（毫秒）
     */
    private val getTaskMillis by lazy {
        30 * 1000L
    }

    /**
     * 定时查询日志回捞任务
     */
    private val getTaskExecutor by lazy {
        Executors.newSingleThreadScheduledExecutor()
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
        ZLogUtil.Builder()
            .rootPath("zlog") //设置日志根目录
            .expireDays(7) //设置日志过期时间，单位为天
            .isOfflineLog(true) //设置是否离线日志
            .cacheFileSize(32) //设置缓存文件大小，单位为KB
            .maxFileSize(50) //设置单个日志文件最大大小，单位为MB
            .mergeCacheFileSeconds(30) //设置合并缓存文件到主文件的时间间隔，单位为秒
            .compress(true) //设置是否压缩日志
            .secretKey(App.getSp().getString(SPConstant.SHARED_SECRET_KEY)) //设置加密密钥
            .build()
    }

    /**
     * 实时日志实例
     */
    private val onlineLog by lazy {
        ZLogUtil.Builder()
            .rootPath("zlog") //设置日志根目录
            .expireDays(1) //设置日志过期时间，单位为天
            .isOfflineLog(false) //设置是否离线日志
            .cacheFileSize(32) //设置缓存文件大小，单位为KB
            .maxFileSize(50) //单文件大小，单位为MB
            .mergeCacheFileSeconds(30) //设置合并缓存文件到主文件的时间间隔，单位为秒
            .compress(true) //设置是否压缩日志
            .secretKey(App.getSp().getString(SPConstant.SHARED_SECRET_KEY)) //设置加密密钥
            .build()
    }

    /**
     * 附加字段
     */
    private var identify = ""

    /**
     * 直接使用 [ZLog] 预初始化，则就不需要调用 [App.setApplication]
     * @param application Application
     */
    fun preInit(application: Application) {
        App.setApplication(application)
        identify = App.getSp().getString(SPConstant.IDENTIFY_VALUE_KEY)
    }

    /**
     * 直接使用 [ZLog] 初始化，则不需要调用 [App.init]
     * @param hostUrl 服务端地址
     * @param appId 应用ID
     */
    fun init(hostUrl: String, appId: String) {
        App.init(hostUrl, appId)

        //如果应用ID和上次不一致，则重新进行设备注册
        if (App.getSp().getString(SPConstant.APP_ID_KEY) != appId) {
            //清除本地数据
            App.getSp().clear(true)
        }

        val info = getRegisterInfo()
        //如果SessionId和共享密钥均存在，则和服务端进行验证密钥操作，否则进行设备注册
        if (info.third) {
            //验证密钥
            App.getGlobalVM().verifySharedKey(info.first, info.second, onError = {
                //验证失败，重新获取SessionId
                App.getGlobalVM().getSessionId()
            })
        } else {
            App.getGlobalVM().getSessionId()
        }

        //记录应用ID
        App.getSp().putString(SPConstant.APP_ID_KEY, appId)

        //定时上传实时日志(60s执行一次)
        uploadLogExecutor.scheduleAtFixedRate({
            putOnlineLog()
        }, uploadLogMillis, uploadLogMillis, TimeUnit.MILLISECONDS)

        //定时查询日志回捞任务(30s执行一次)
        getTaskExecutor.scheduleAtFixedRate({
            getLogTask()
        }, getTaskMillis, getTaskMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * 写离线日志
     * @param level 日志级别
     * @param msg 日志消息
     */
    fun writeOfflineLog(
        level: Log.Level,
        msg: String
    ) {
        val (className, lineNumber) = getCallingMethodInfo()

        offlineLog.writeLog(serializeLog(level, "$className:$lineNumber", msg))
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
        syncDataToOfflineLog: Boolean = true
    ) {
        val (className, lineNumber) = getCallingMethodInfo()

        val serializeLog = serializeLog(level, "$className:$lineNumber", msg)

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
        ThreadUtil.runOnBackgroundThread(task = {
            uploadLogLock.lock().withLock({
                val logList = onlineLog.readLog(System.currentTimeMillis() / 1000)

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
                        App.getGlobalVM().putOnlineLog(reqBean)
                    }
                }
            })
        })
    }

    /**
     * 查询日志回捞任务
     */
    private fun getLogTask() {
        if (getRegisterInfo().third) {
            App.getGlobalVM().getTask {
                uploadLogFile(it[0])
            }
        }
    }

    /**
     * 执行日志上传任务
     * @param taskInfo 任务信息
     */
    private fun uploadLogFile(taskInfo: GetTaskModel.GetTaskInfo) {
        ThreadUtil.runOnBackgroundThread(task = {
            uploadLogFileLock.lock().withLock({
                val latch = CountDownLatch(1)

                //保存Zip的位置
                val filePath =
                    App.getApplication().cacheDir.absolutePath + File.separator + taskInfo.taskId + ".zip"

                val (state, fileListIsEmpty) = offlineLog.zipLogFiles(
                    taskInfo.startTime,
                    taskInfo.endTime,
                    filePath
                )

                if (state) {
                    //待上传的日志文件
                    val zipFile = File(filePath)

                    //上传文件
                    App.getGlobalVM().uploadLogFile(
                        zipFile = zipFile,
                        onSuccess = {
                            //删除临时文件
                            if (zipFile.exists()) {
                                zipFile.delete()
                            }
                        }, onComplete = {
                            latch.countDown()
                        })
                } else if (fileListIsEmpty) {
                    //反馈问题给服务端
                    App.getGlobalVM().uploadLogFileErrCallBack(
                        taskId = taskInfo.taskId,
                        msg = "对应时间段的日志文件列表为空",
                        onComplete = {
                            latch.countDown()
                        })
                }

                //等待异步操作完成
                latch.await()
            })
        })
    }

    /**
     * 获取SessionId、共享密钥以及是否已注册
     * @return 返回信息
     */
    private fun getRegisterInfo(): Triple<String, String, Boolean> {
        val sessionId = App.getSp().getString(SPConstant.SESSION_ID_KEY)
        val sharedSecret = App.getSp().getString(SPConstant.SHARED_SECRET_KEY)
        return Triple(sessionId, sharedSecret, sessionId.isNotEmpty() && sharedSecret.isNotEmpty())
    }

    /**
     * 直接使用 [ZLog] 关闭，就不需要调用 [ZLogUtil] 的关闭方法了
     */
    fun close() {
        uploadLogExecutor.shutdown()
        getTaskExecutor.shutdown()
        offlineLog.close()
        onlineLog.close()
        App.getGlobalVM().close()
    }

    /**
     * 更改附加字段
     * @param identify 可以是用户手机号等标识用户身份的内容
     */
    fun changeIdentifyValue(identify: String) {
        App.getSp().putString(SPConstant.IDENTIFY_VALUE_KEY, identify)

        ZLog.identify = identify
    }
}