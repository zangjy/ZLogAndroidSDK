package com.zjy.zlogandroidsdk.activity

import com.zjy.android.zlog.ZLog
import com.zjy.android.zlog.proto.LogOuterClass.Log
import com.zjy.xbase.activity.BaseActivity
import com.zjy.zlogandroidsdk.databinding.ActivityZlogTestBinding
import kotlin.random.Random

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

    }

    override fun initData() {
        Thread {
            while (true) {
                Thread.sleep((Random.nextInt(2000) + 2000).toLong())

                val randomSentence = generateRandomChineseSentence()

                val generateRandomLogLevel = generateRandomLogLevel()

                ZLog.writeOnlineLog(generateRandomLogLevel, randomSentence)
            }
        }.start()
    }

    private fun generateRandomChineseSentence(): String {
        return mutableListOf(
            "电视机告诉我，它想去旅行，于是它变成了飞船，我不得不跟着它探索宇宙。",
            "今天，我试图用键盘编写一本小说，结果小说突然变成了一只彩色蝴蝶飞向天空。",
            "我的冰箱开始跳舞，飘着音符，唱着歌。我加入它，舞动着，忘记了时间的存在。",
            "把电视机遥控器当作魔杖，我点了一下，立刻飘浮在卧室里，看到了奇异的星际景象。",
            "我的书本开始说话，告诉我关于时间旅行的秘密。我跟随着书本的指引进入了时光隧道。",
            "突然之间，我的笔记本电脑屏幕显示了未来的预测，我开始思考着未来的可能性。",
            "我与橡皮鸭子进行了深刻的对话，它向我透露了宇宙的机密，我不再感到孤独。",
            "书桌上的杯子变成了时光机，我喝下一口，立刻回到了童年的记忆里。",
            "每次我看向窗外的云朵，它们都会写出新的故事，告诉我未知的奇幻世界。",
            "我在床上闭上眼睛，突然觉得自己在漫游星际，与外星生物交流着奇妙的语言。",
            "餐桌上的刀叉开始演奏交响乐，我成为了指挥家，挥舞着餐具的音符。",
            "书页逐渐浮空，组成了一座文字城市，我穿越其中，探索着未知的街道。",
            "一片树叶飘进我的房间，告诉我关于宇宙的秘密密码，我开始解读它们。",
            "我站在镜子前，看到自己的镜像独立出来，变成了我的对话伙伴。",
            "突然，我的房子升空，变成了空中花园，我在悬浮中欣赏着星星的舞蹈。",
            "电梯变成了时间机器，每次按下按钮，我穿越不同的年代。",
            "用遥控器操控自己的思维，进入了梦境中的数字迷宫。",
            "我用笔画出的涂鸦突然活了起来，组成了一场生动的艺术表演。",
            "我的音响变成了口袋剧场，演绎着微型戏剧，我成为了观众和演员。",
            "窗帘变成了时间门，每次拉开，我穿越到不同的现实世界。"
        ).random()
    }

    private fun generateRandomLogLevel(): Log.Level {
        return mutableListOf(
            Log.Level.INFO,
            Log.Level.DEBUG,
            Log.Level.WARN,
            Log.Level.ERROR,
            Log.Level.VERBOSE
        ).random()
    }
}