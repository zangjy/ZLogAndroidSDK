# ZLogAndroidSDK

[点此查看完整介绍](https://blog.zangjiayu.top/2023/11/23/zlog/20231123/)

# 使用方法

## 添加依赖

[XBase](https://github.com/zangjy/XBase)  
[ZLogAndroidSDK](https://github.com/zangjy/ZLogAndroidSDK)

```
implementation 'com.github.zangjy:XBase:版本号见Github'
implementation 'com.github.zangjy:ZLogAndroidSDK:版本号见Github'
```

## MMKV对armv7和x86架构的支持

ZLogAndroidSDK使用了MMKV对写入位置进行记录，在v1.3.5版本中MMKV放弃了对armv7和x86架构的支持，在不受支持的架构中使用，你需要

1. [下载SO库](https://github.com/zangjy/MMKV/releases/tag/v1.3.5)
2. 将SO库放到对应文件夹下  
   ![将SO库放到对应文件夹下](https://s21.ax1x.com/2024/06/17/pk0KsC8.png)

## 在工程根目录的`build.gradle`中添加如下：

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

或者在`settings.gradle`中添加如下：

```
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        //引用通过JitPack.io发布的第三方库
        maven { url 'https://www.jitpack.io' }
    }
}
```

## 在程序入口初始化

![初始化SDK](https://z1.ax1x.com/2023/11/27/piBn7Ox.png)

## 写实时日志

```
ZLog.writeOnlineLog(Log.Level.INFO, "写实时日志")
```

## 写离线日志

```
ZLog.writeOfflineLog(Log.Level.INFO, "写离线日志")
```