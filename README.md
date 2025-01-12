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