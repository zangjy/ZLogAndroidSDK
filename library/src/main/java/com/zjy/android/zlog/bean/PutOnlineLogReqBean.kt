package com.zjy.android.zlog.bean

/**
 * 文件名：PutOnlineLogReqBean
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/16
 * 描述：
 */
data class PutOnlineLogReqBean(
    var data: MutableList<PutOnlineLogBean> = mutableListOf(),
) {
    data class PutOnlineLogBean(
        val sequence: Long,
        val system_version: String,
        val app_version: String,
        val time_stamp: Long,
        val log_level: Int,
        val identify: String,
        val tag: String,
        val msg: String,
    )
}