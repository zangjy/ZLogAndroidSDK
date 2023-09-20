package com.zjy.zlogandroidsdk.activity

import android.util.Log
import com.zjy.android.zlog.util.AESUtil
import com.zjy.android.zlog.util.ECDHUtil
import com.zjy.xbase.activity.BaseActivity
import com.zjy.zlogandroidsdk.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var pubKey: String
    private lateinit var privKey: String
    private lateinit var sharedKey: String

    override fun initObservers() {

    }

    override fun initListeners() {
        binding.mbGenerateSharedKey.setOnClickListener {
            val serverPubKey = binding.etServerPubKey.text.toString().trim()
            if (serverPubKey.isNotEmpty() && this::privKey.isInitialized) {
                ECDHUtil.generateSharedSecret(serverPubKey, privKey).run {
                    sharedKey = this
                    binding.tvSharedKey.text = "共享密钥：${this}"
                    Log.i(TAG, "SharedKey:${this}")
                }
            }
        }

        binding.mbEncrypt.setOnClickListener {
            val inputData = binding.etEncrypt.text.toString().trim()
            if (inputData.isNotEmpty() && this::sharedKey.isInitialized) {
                AESUtil.encryptString(inputData, sharedKey).run {
                    binding.tvEncryptData.text = "加密后数据：${this}"
                    binding.tvLocalDecryptData.text =
                        "本地解密后数据：${AESUtil.decryptString(this, sharedKey)}"
                    Log.i(TAG, "EncryptData:${this}")
                }
            }
        }

        binding.mbDecrypt.setOnClickListener {
            val inputData = binding.etDecrypt.text.toString().trim()
            if (inputData.isNotEmpty() && this::sharedKey.isInitialized) {
                AESUtil.decryptString(inputData, sharedKey).run {
                    binding.tvDecryptData.text = "解密后数据：${this}"
                    Log.i(TAG, "DecryptData:${this}")
                }
            }
        }

        binding.mbNewPair.setOnClickListener {
            val keyPair = ECDHUtil.generateKeyPair()
            pubKey = keyPair.first
            privKey = keyPair.second
            Log.i(TAG, "PubKey:${pubKey}")
            Log.i(TAG, "PrivKey:${privKey}")
        }
    }

    override fun initData() {

    }
}