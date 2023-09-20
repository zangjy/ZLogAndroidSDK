package com.zjy.android.zlog.model

import com.google.gson.annotations.SerializedName

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
    var errMsg: String = ""
)