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

## 关于"加速运行但不影响音乐"

**重要架构事实**：PS1 没有独立的声音 CPU——音乐音序器（note 触发、音高、DMA 喂数据）跑在主 CPU 上。
因此一旦主 CPU 加速，音乐的**节奏必然跟着加速**，这是 PS1 硬件架构决定的，任何 PS1 模拟器都绕不过去。
你提到的 yaba（Yaba Sanshiro，世嘉土星模拟器）能做到，是因为土星有独立的声音 CPU（SCSP+68k），
可以把整个声音子系统切到真实时钟运行；PS1 做不到。

本版本（2021 GPL 版）的音频处理只有"重采样（Resampling）"，快进时音乐既加速又变调。
为此本仓库做了两项改进：

1. **新增"快进时静音"开关**（设置 → 音频 → 快进时静音）：开启后快进/涡轮增压时自动静音，
   退出快进恢复正常音量。避免变调音乐刺耳，是 PS1 上最实用的方案（与 RetroArch 主流做法一致）。
2. **修复快进音量不生效的 bug**：原版 Android 上"快进音量"设置实际未生效，现已修复。
   你也可以把"快进音量"调到 0 达到同样静音效果。

> 如果想听到原速原调的背景音乐，推荐做法：开启"快进时静音"，同时用手机音乐 App 后台播放游戏原声 OST。

## 金手指数据库（已内置并汉化）

本 APK **已内置 5200 款 PS1 游戏的金手指数据库**（`database/chtdb.txt`，随 APK assets 打包）。
安装后无需额外下载，启动游戏时自动加载对应金手指。

- **使用方法**：游戏内 → 菜单 → 补丁代码 → 勾选需要的金手指。
- **描述已汉化**：金手指描述已批量翻译为简体中文（覆盖率约 93%），如"无限生命值""无限弹药""最大金钱"等。
  少量游戏专用术语和专有名词保留英文。
- **导入外部金手指**：游戏内 → 菜单 → 导入补丁代码，支持 PCSXR / Libretro / ePSXe 格式的 .cht 文件。
- **自定义金手指**：将 `SERIAL.cht` 文件放到数据目录的 `cheats/` 文件夹下，可覆盖内置数据库。


