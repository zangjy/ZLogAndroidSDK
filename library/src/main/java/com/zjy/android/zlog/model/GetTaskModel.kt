package com.zjy.android.zlog.model

import com.google.gson.annotations.SerializedName

/**
 * 文件名：GetTaskModel
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/16
 * 描述：
 */
data class GetTaskModel(
    @SerializedName("data")
    val data: MutableList<GetTaskInfo> = mutableListOf(),
) : BaseModel() {
    data class GetTaskInfo(
        @SerializedName("start_time")
        val startTime: Long = 0,
        @SerializedName("end_time")
        val endTime: Long = 0,
        @SerializedName("task_id")
        val taskId: String = "",
    )
}