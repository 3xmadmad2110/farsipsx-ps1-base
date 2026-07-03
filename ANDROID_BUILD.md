# DuckStation Android — 自编译构建说明

本仓库用于在 GitHub Actions 上**在线编译 DuckStation 的 Android APK**。

## 来源与许可

- 基于 `stenzek/duckstation` 的提交 `81da9be`（2021-06-07）。
- 这是 DuckStation **公开仓库中最后一个仍包含 Android 前端源码**的提交；上游于
  2021-06-08 在提交 `6e49adb` 中移除了 Android 应用（"Remove Android app.
  This repository exists solely for the desktop version now."），此后 Android 版仅以
  二进制形式发布于 Google Play。
- 该提交的许可证为 **GPL-3.0**（见 `LICENSE`），允许自由修改与再分发。
- 上游后续（2024 年）改为 PolyForm Strict 等非自由许可；本仓库不包含那些变更，始终保持 GPL-3.0。

> 注意：此为 2021 年的代码，缺少上游近几年的改进。仅供学习与自编译使用。

## 如何获取 APK

1. 进入仓库的 **Actions** 页签。
2. 选择最新的 `Build Android APK` 运行。
3. 在运行详情底部 **Artifacts** 区域下载 `duckstation-android-apks`。
4. 解压后得到三个 APK（按 CPU 架构）：
   - `duckstation-arm64-v8a-release.apk` — 现代 64 位手机用这个
   - `duckstation-armeabi-v7a-release.apk` — 老 32 位 ARM 设备
   - `duckstation-x86_64-release.apk` — x86 模拟器/平板
5. APK 已用 CI 生成的自签名证书签名，可直接安装（需在手机上允许"安装未知来源应用"）。

## 本地编译

需要 JDK 11、Android SDK（platforms;android-29、build-tools;30.0.2、ndk;21.4.7075529、cmake;3.10.2.4988404）。

```bash
cd android
./gradlew assembleRelease
# 产物在 android/app/build/outputs/apk/release/
```

## 加速运行但不影响音乐

DuckStation **已内置**此功能，无需改源码：

- 设置 → Audio → **Stretch Mode = Time Stretch**（保调时间拉伸，默认即此项）。
- 设置 → General → **Fast Forward Speed / Turbo Speed** 设为**有限倍率**（如 `2.0`、`5.0`），
  **不要用 0（无限）**——无限速时保调拉伸会失效。
- 可选：设置快进时专用音量 **Fast Forward Volume** 调低，避免加速时太吵。

原理：SPU 仍按被加速的 CPU 时钟产出更多样本，音频输出层用 SoundTouch 的 tempo 算法
把过密的样本拉伸回 44100Hz 原速播放，从而保持音调不变。
