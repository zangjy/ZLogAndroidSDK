package com.zjy.zlogandroidsdk.activity

import com.zjy.android.zlog.ZLog
import com.zjy.android.zlog.util.ThreadUtil
import com.zjy.xbase.activity.BaseActivity
import com.zjy.zlogandroidsdk.databinding.ActivityZlogTestBinding
import com.zjy.zlogandroidsdk.ext.generateRandomChineseSentence
import com.zjy.zlogandroidsdk.ext.generateRandomLogLevel

/**
 * 文件名：ZLogTestActivity
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/8
 * 描述：
 */
class ZLogTestActivity : BaseActivity<ActivityZlogTestBinding>() {

    override fun initObservers() {

    }

    override fun initListeners() {
        binding.mbWriteLog.setOnClickListener {
            ThreadUtil.runOnBackgroundThread(task = {
                for (i in 1..50000) {
                    val randomSentence = generateRandomChineseSentence()

                    val generateRandomLogLevel = generateRandomLogLevel()

                    ZLog.writeOfflineLog(generateRandomLogLevel, randomSentence)
                }
            })
        }
    }

    override fun initData() {

    }
}