package com.zjy.zlogandroidsdk.activity

import com.zjy.android.zlog.ZLog
import com.zjy.android.zlog.zlog.BaseLog
import com.zjy.xbase.activity.BaseActivity
import com.zjy.zlogandroidsdk.databinding.ActivityZlogTest2Binding
import com.zjy.zlogandroidsdk.ext.generateRandomChineseSentence
import com.zjy.zlogandroidsdk.ext.generateRandomLogLevel

/**
 * 文件名：ZLogTest2Activity
 * 创建者：ZangJiaYu
 * 创建日期：2024/6/7
 * 描述：
 */
class ZLogTest2Activity : BaseActivity<ActivityZlogTest2Binding>() {

    override fun initObservers() {

    }

    override fun initListeners() {
        binding.mbImmediateForceType.setOnClickListener {
            ZLog.changeOnlineLogForceType(BaseLog.ForceType.IMMEDIATE)
            ZLog.changeOfflineLogForceType(BaseLog.ForceType.IMMEDIATE)
        }

        binding.mbScheduledForceType.setOnClickListener {
            ZLog.changeOnlineLogForceType(BaseLog.ForceType.SCHEDULED)
            ZLog.changeOfflineLogForceType(BaseLog.ForceType.SCHEDULED)
        }

        binding.mbForce.setOnClickListener {
            ZLog.forceOnlineLog()
            ZLog.forceOfflineLog()
        }

        binding.mbWriteLog.setOnClickListener {
            val randomSentence = generateRandomChineseSentence()

            val generateRandomLogLevel = generateRandomLogLevel()

            ZLog.writeOnlineLog(generateRandomLogLevel, randomSentence)
        }

        binding.mbClose.setOnClickListener {
            ZLog.close()
        }
    }

    override fun initData() {

    }
}