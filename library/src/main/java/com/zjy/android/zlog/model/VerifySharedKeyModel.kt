package com.zjy.android.zlog.model

import com.google.gson.annotations.SerializedName

/**
 * 文件名：VerifySharedKeyModel
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/15
 * 描述：
 */
data class VerifySharedKeyModel(
    @SerializedName("decrypt_data")
    var decryptData: String = "",
) : BaseModel()