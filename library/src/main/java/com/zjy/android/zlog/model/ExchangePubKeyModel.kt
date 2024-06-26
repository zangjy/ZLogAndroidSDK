package com.zjy.android.zlog.model

import com.google.gson.annotations.SerializedName

/**
 * 文件名：ExchangePubKeyModel
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/15
 * 描述：
 */
data class ExchangePubKeyModel(
    @SerializedName("server_pub_key")
    var serverPubKey: String = "",
    @SerializedName("tmp_session_id")
    var tmpSessionId: String = "",
) : BaseModel()