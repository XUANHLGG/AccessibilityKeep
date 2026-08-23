# 无障碍保活

一个面向 ColorOS 15 的 LSPosed 模块，针对最近任务移除时停止的应用被撤销无障碍服务授权的问题。

## 安装

1. 安装 APK。
2. 在 LSPosed 中启用模块。
3. 作用域只勾选带有“推荐应用”标记的“系统框架”。
4. 重启系统。
5. 打开“无障碍保活”，授予 Root 权限。
6. 勾选需要保护的无障碍应用。

## 构建

```
./gradlew :app:assembleDebug
```

输出位于 `app/build/outputs/apk/debug/`。

## 设计

- Hook 运行在 `android`/`system_server` 作用域。
- 白名单保存在 Secure Settings，由 Root 写入。
- 只处理被最近任务移除的应用，不拦截设置中的“强行停止”。

## 已测试设备

- 一加 12（PJD110）
- ColorOS 15.0.2
- Android 15 / API 35
- KernelSU 32302
- LSPosed Zygisk 2.0.4 (7741)

## License

GPL-3.0-only
