package com.zjy.android.zlog.model

import com.google.gson.annotations.SerializedName
import com.zjy.android.zlog.constant.Constant
import com.zjy.xbase.net.BaseResp

/**
 * 文件名：BaseModel
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/15
 * 描述：
 */
open class BaseModel(
    @SerializedName("status")
    var status: String = "",
    @SerializedName("err_msg")
    var errMsg: String = "",
) : BaseResp() {
    override fun paresResp(): Pair<Boolean, Throwable> {
        return Pair(status == Constant.SUCCESS_CODE, Throwable(errMsg))
    }
}