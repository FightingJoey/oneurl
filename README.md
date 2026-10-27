# 网址直达 (OneURL)

一个只有一件事的安卓应用：**第一次打开问你一个网址，存下来，以后每次打开直接进这个网址。**

典型用法：把手机变成某个网页的专属入口——比如家里 Kindle 的遥控页、自建的服务面板、某个常看的站点。

## 怎么用

1. 装好后打开，弹出输入框。
2. 输入网址，比如 `192.168.1.10:8080` 或 `example.com`。不写 `http://` / `https://` 也行，会自动补：**IP 和 localhost 补 `http://`，其它补 `https://`**。
3. 点「保存并打开」，之后就直接进去了。

以后每次打开直接跳转，不再问。

## 打不开怎么办

- 页面报错弹「打不开这个网址」时，可以选 **重试 / 修改网址 / 退出**。
- 想彻底重来：系统设置 → 应用 → 网址直达 → 清除数据，下次打开会重新问。

## 下载

`Releases` 里下载 `oneurl.apk` 装到手机即可（需要允许「安装未知来源应用」）。

> 每次 CI 构建都用全新的调试签名，所以**装新版本前要先卸载旧版本**，否则会提示签名冲突。卸载会清掉已保存的网址。

## 自己构建

不依赖 Android Studio，GitHub Actions 上跑：

- 推送到 `main` → 自动构建，产物在 Actions 的 `oneurl-apk` artifact 里。
- 打 tag（`git tag v1.0.0 && git push origin v1.0.0`）→ 额外创建一个 Release 并附上 APK。

本地构建需要 JDK 17 + Android SDK（`ANDROID_HOME`），然后：

```bash
gradle :app:assembleDebug     # 产物 app/build/outputs/apk/debug/app-debug.apk
```

## 实现

- 纯 Android 框架，**零第三方依赖**，一个 `Activity` + 一个 `WebView`。
- 网址存在 `SharedPreferences`（`oneurl` / `start_url`）。
- `usesCleartextTraffic=true`，所以 `http://` 的局域网地址也能打开（不然 Android 9+ 默认拦明文）。
- 返回键先网页后退，退无可退才退出应用。
