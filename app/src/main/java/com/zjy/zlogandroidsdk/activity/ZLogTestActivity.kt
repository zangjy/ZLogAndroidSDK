package com.zjy.zlogandroidsdk.activity

import android.os.SystemClock
import com.zjy.android.zlog.ZLog
import com.zjy.android.zlog.proto.LogOuterClass.Log
import com.zjy.android.zlog.util.ThreadUtil
import com.zjy.xbase.activity.BaseActivity
import com.zjy.zlogandroidsdk.databinding.ActivityZlogTestBinding

/**
 * 文件名：ZLogTestActivity
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/8
 * 描述：
 */
class ZLogTestActivity : BaseActivity<ActivityZlogTestBinding>() {
    private val longData by lazy {
        "{\"status\":\"0\",\"data\":[{\"id\":\"WuaLsTH3Q4ay7hEMuPaNZA\",\"courseTypeName\":\"万物归一\",\"courseTypeSubclassName\":\"医师\",\"courseName\":\"课程1\",\"teachers\":\"测试老师\",\"imgUrl\":\"https://shijizhongshi-image.obs.cn-north-4.myhuaweicloud.com/2023/3/17/8256657215243829951/1678945261746.png\",\"palyTime\":\"0\",\"addtime\":1679035971000,\"isremmend\":0,\"hwassetId\":\"671a0b847aa67b7d3d4784f9a3da3b20\",\"hwUrl\":\"https://vodd.caiquecloud.com/asset/671a0b847aa67b7d3d4784f9a3da3b20/play_video/ceabca00b6af3c747135cd01733a4d72_1.m3u8\",\"hwSize\":22078464,\"hwDuration\":160,\"qualityList\":[{\"quality\":\"SD\",\"qurl\":\"https://vodd.caiquecloud.com/asset/671a0b847aa67b7d3d4784f9a3da3b20/play_video/ceabca00b6af3c747135cd01733a4d72_2.m3u8\",\"qsize\":13977600,\"qduration\":160},{\"quality\":\"HD\",\"qurl\":\"https://vodd.caiquecloud.com/asset/671a0b847aa67b7d3d4784f9a3da3b20/play_video/ceabca00b6af3c747135cd01733a4d72_1.m3u8\",\"qsize\":22078464,\"qduration\":160}],\"watchNumber\":73,\"newhwurl\":\"https://vodd.caiquecloud.com/asset/671a0b847aa67b7d3d4784f9a3da3b20/8807c355570dc8bee9aad8d355c2c3de.mp4\"},{\"id\":\"7Wh2cJj5TjGVGBcuc6py0Q\",\"courseTypeName\":\"万物归一\",\"courseTypeSubclassName\":\"医师\",\"courseName\":\"天天\",\"teachers\":\"李老师\",\"imgUrl\":\"https://shijizhongshi-image.obs.cn-north-4.myhuaweicloud.com/2023/3/8/5604686769269418873/8NPCINIU55LD0031.jpg\",\"palyTime\":\"0\",\"addtime\":1678246216000,\"isremmend\":0,\"hwassetId\":\"1f88249172d3a1d311ab42c841c910a8\",\"hwUrl\":\"https://vodd.caiquecloud.com/asset/1f88249172d3a1d311ab42c841c910a8/play_video/e2695235f643ee7e097bd0b285b6169b_2.m3u8\",\"hwSize\":852992,\"hwDuration\":9,\"qualityList\":[{\"quality\":\"SD\",\"qurl\":\"https://vodd.caiquecloud.com/asset/1f88249172d3a1d311ab42c841c910a8/play_video/e2695235f643ee7e097bd0b285b6169b_2.m3u8\",\"qsize\":852992,\"qduration\":9}],\"watchNumber\":32,\"newhwurl\":\"https://vodd.caiquecloud.com/asset/1f88249172d3a1d311ab42c841c910a8/d984712ea76c2d8b042bce6c22129226.mp4\"},{\"id\":\"Uy08V02_SkK84_gpg8hcjg\",\"courseTypeName\":\"万物归一\",\"courseTypeSubclassName\":\"医师\",\"courseName\":\"他物123\",\"teachers\":\"李老师\",\"describes\":\"欢迎同学听课\",\"imgUrl\":\"https://shijizhongshi-image.obs.cn-north-4.myhuaweicloud.com/2023/3/8/6054065544573806084/e0b41a06f005d2ef.jpg\",\"palyTime\":\"0\",\"addtime\":1678242246000,\"isremmend\":0,\"hwassetId\":\"2c6e4f344e9ebecfc3b3b1afe01a5e31\",\"hwUrl\":\"https://vodd.caiquecloud.com/asset/2c6e4f344e9ebecfc3b3b1afe01a5e31/play_video/b0165370e2288983db0246e0c399efba_2.m3u8\",\"hwSize\":1096704,\"hwDuration\":12,\"qualityList\":[{\"quality\":\"SD\",\"qurl\":\"https://vodd.caiquecloud.com/asset/2c6e4f344e9ebecfc3b3b1afe01a5e31/play_video/b0165370e2288983db0246e0c399efba_2.m3u8\",\"qsize\":1096704,\"qduration\":12}],\"watchNumber\":5,\"newhwurl\":\"https://vodd.caiquecloud.com/asset/2c6e4f344e9ebecfc3b3b1afe01a5e31/5640166cad6dbcf995bee46f6f045954.mp4\"}],\"count\":0}"
    }

    private val shortData by lazy {
        "Test Data"
    }

    override fun initObservers() {

    }

    override fun initListeners() {
        binding.mbWriteLog.setOnClickListener {
            ThreadUtil.runOnBackgroundThread(task = {
                for (i in 1..100) {
                    ZLog.writeOnlineLog(Log.Level.INFO, longData)
                }
            })
        }
    }

    override fun initData() {
        Thread {
            SystemClock.sleep(400)
            ZLog.writeOnlineLog(Log.Level.INFO, shortData)
        }.start()
    }
}