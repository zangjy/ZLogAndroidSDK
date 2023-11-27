package com.zjy.android.zlog.constant

/**
 * 文件名：SPConstant
 * 创建者：ZangJiaYu
 * 创建日期：2023/9/14
 * 描述：
 */
object SPConstant {
    /**
     * 应用ID
     */
    const val APP_ID_KEY = "APP_ID_KEY"

    /**
     * 临时的会话ID
     */
    const val TMP_SESSION_ID_KEY = "TMP_SESSION_ID_KEY"

    /**
     * 初始化完成后的会话ID
     */
    const val SESSION_ID_KEY = "SESSION_ID_KEY"

    /**
     * 本机和服务端公钥计算的共享密钥
     */
    const val SHARED_SECRET_KEY = "SHARED_SECRET_KEY"

    /**
     * 附加字段，可以用此字段记录用户身份等信息
     */
    const val IDENTIFY_VALUE_KEY = "IDENTIFY_VALUE_KEY"

    /**
     * 服务端交互成功的标志
     */
    const val SUCCESS_CODE = "0000"

    /**
     * 默认的SP文件名
     */
    const val DEFAULT_SP_NAME = "zlog_conf"
}