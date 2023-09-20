package com.zjy.android.zlog

import android.app.Application
import com.zjy.android.zlog.bean.PutOnlineLogReqBean
import com.zjy.android.zlog.constant.SPConstant
import com.zjy.android.zlog.model.GetTaskModel
import com.zjy.android.zlog.proto.LogOuterClass.Log
import com.zjy.android.zlog.util.App
import com.zjy.android.zlog.util.AppUtil
import com.zjy.android.zlog.util.DeviceUtil
import com.zjy.android.zlog.util.ThreadUtil
import com.zjy.android.zlog.util.ZLogUtil
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * 文件名：ZLogHelper
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
object ZLog {
    /**
     * 定时上传实时日志
     */
    private val logExecutor by lazy {
        Executors.newSingleThreadScheduledExecutor()
    }

    /**
     * 定时上传实时日志的锁对象
     */
    private val logLock by lazy {
        ReentrantLock()
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
            .rootPath("zlog") //日志根目录
            .logPrefix("log-") //日志文件前缀
            .maxLogAgeDays(7) //日志保留天数，超出将自动清理
            .flushIntervalSeconds(5) //设置落盘时间
            .isOfflineLog(true) //离线日志
            .maxLogFileSize(50) //单文件大小，单位为MB
            .compress(true) //启用压缩
            .secretKey(App.getSp().getString(SPConstant.SHARED_SECRET_KEY)) //数据加解密密钥
            .build()
    }

    /**
     * 实时日志实例
     */
    private val onlineLog by lazy {
        ZLogUtil.Builder()
            .rootPath("zlog") //日志根目录
            .logPrefix("log-") //日志文件前缀
            .maxLogAgeDays(1) //日志保留天数，超出将自动清理
            .flushIntervalSeconds(5) //设置落盘时间
            .isOfflineLog(false) //实时日志
            .maxLogFileSize(50) //单文件大小，单位为MB
            .compress(true) //启用压缩
            .secretKey(App.getSp().getString(SPConstant.SHARED_SECRET_KEY)) //数据加解密密钥
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
        //定时上传实时日志(30s执行一次)
        logExecutor.scheduleAtFixedRate({
            putOnlineLog()
        }, 30000, 30000, TimeUnit.MILLISECONDS)
        //定时查询日志回捞任务(60s执行一次)
        getTaskExecutor.scheduleAtFixedRate({
            getLogTask()
        }, 30000, 60000, TimeUnit.MILLISECONDS)
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

        //遍历堆栈，找到在本类之后的第一个不是当前类的元素
        for (element in stackTrace) {
            if (element.className != this::class.java.name) {
                val fileName = element.fileName
                val lineNumber = element.lineNumber
                return Pair(fileName, lineNumber)
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
            if (logLock.tryLock()) {
                try {
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
                } finally {
                    logLock.unlock()
                }
            }
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
            if (logLock.tryLock()) {
                try {
                    //创建 CountDownLatch
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
                        App.getGlobalVM().uploadLogFile(zipFile = zipFile, onSuccess = {
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
                            taskInfo.taskId,
                            "对应时间段的日志文件列表为空",
                            onComplete = {
                                latch.countDown()
                            })
                    }

                    //等待异步操作完成
                    latch.await()
                } finally {
                    logLock.unlock()
                }
            }
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
        logExecutor.shutdown()
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