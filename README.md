# 定时启动器 (AppLauncher)

一个 Android 定时启动应用工具，可以在设定的时间自动打开指定的 App。

## 功能

- 选择任意已安装应用作为启动目标
- 支持每天两个时段（可分别设置不同时间）
- 每天自动执行（周日~周六无差别）
- 一键启用/禁用定时任务
- 执行日志记录，可查看历史启动记录
- 开机自动恢复定时任务
- 自检诊断，快速定位配置问题

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **定时**：AlarmManager（精确闹钟）
- **存储**：DataStore Preferences + Gson
- **架构**：单 Activity + Repository 模式

## 权限说明

| 权限 | 用途 |
|------|------|
| `SCHEDULE_EXACT_ALARM` | 精确闹钟，保证按时启动应用 |
| `QUERY_ALL_PACKAGES` | 读取已安装应用列表供用户选择 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重新注册闹钟 |
| `WAKE_LOCK` | 闹钟触发时维持 CPU 唤醒 |
| `DISABLE_KEYGUARD` | 在锁屏界面显示目标应用 |
| `USE_FULL_SCREEN_INTENT` | 锁屏时全屏显示 BridgeActivity |

## 开发环境搭建（Ubuntu）

### 1. 安装 JDK 17

```bash
# 安装 OpenJDK 17
sudo apt update
sudo apt install openjdk-17-jdk -y

# 验证安装
java -version
# 预期输出: openjdk version "17.0.x" ...

# 设置 JAVA_HOME（可选，但建议设置）
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### 2. 安装 Android SDK 命令行工具

Android 构建需要 Android SDK。推荐两种方式：

#### 方式 A：安装 Android Studio（推荐）

```bash
# 通过 Snap 安装
sudo snap install android-studio --classic

# 或从官网下载手动安装：
# https://developer.android.com/studio
```

安装后首次启动 Android Studio，按向导完成 SDK 安装。SDK 默认路径：
- Snap 安装：`~/snap/android-studio/current/Android/Sdk`
- 手动安装：`~/Android/Sdk`

#### 方式 B：仅安装命令行工具（无 GUI）

```bash
# 创建目录
mkdir -p ~/Android/cmdline-tools
cd ~/Android/cmdline-tools

# 下载命令行工具（替换为最新版本号）
# 查看最新版本: https://developer.android.com/studio#command-line-tools-only
wget https://dl.google.com/android/repository/commandlinetools-linux-latest.zip
unzip commandlinetools-linux-latest.zip
mv cmdline-tools latest

# 安装必要的 SDK 组件
export ANDROID_SDK_ROOT=~/Android
~/Android/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 3. 配置环境变量

将以下内容添加到 `~/.bashrc`（路径按实际情况调整）：

```bash
# Android SDK — 按实际路径填写
# Android Studio Snap:
export ANDROID_SDK_ROOT=$HOME/snap/android-studio/current/Android/Sdk
# Android Studio 手动安装:
# export ANDROID_SDK_ROOT=$HOME/Android/Sdk

export ANDROID_HOME=$ANDROID_SDK_ROOT
export PATH=$ANDROID_HOME/platform-tools:$PATH
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

```bash
source ~/.bashrc
```

### 4. 验证环境

```bash
# 确认 adb 可用
adb --version

# 确认 sdkmanager 可用
sdkmanager --list | head -5

# 确认 JDK
javac -version
```

### 5. 克隆项目

```bash
git clone <repo-url> app_launcher
cd app_launcher
```

### 6. 创建 local.properties

在项目根目录创建 `local.properties` 文件：

```properties
# Android SDK 路径（必须）
sdk.dir=/home/<你的用户名>/snap/android-studio/current/Android/Sdk

# Release 签名密码（仅构建 Release APK 时需要）
keystore.password=你的密钥库密码
key.password=你的密钥密码
```

> 注意：将 `<你的用户名>` 替换为实际用户名，SDK 路径按实际安装位置填写。

### 7. 生成签名密钥（仅 Release 构建需要）

```bash
keytool -genkey -v -keystore ~/app_launcher.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias applauncher
```

按提示输入密码和基本信息。记住填写的密码，需要写入 `local.properties`。

### 8. 构建

```bash
# Debug 构建（无需签名）
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk

# Release 构建（需要签名密钥）
./gradlew assembleRelease
# APK 输出: app/build/outputs/apk/release/app-release.apk
```

### 9. 安装到设备

```bash
# 确认设备已连接（手机开 USB 调试，连电脑）
adb devices

# 直接安装
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/release/app-release.apk

# 或推送到手机手动安装
adb push app/build/outputs/apk/release/app-release.apk /sdcard/Download/
```

### 常见构建问题

| 问题 | 解决方案 |
|------|----------|
| `sdk.dir not found` | 确认 `local.properties` 存在且 `sdk.dir` 路径正确 |
| `keystore not found` | Debug 构建无需签名；Release 需先生成 `~/app_launcher.jks` |
| Gradle 下载慢 | 项目已配置腾讯云镜像 (`gradle-wrapper.properties`) |
| `JAVA_HOME not set` | 按步骤 1 设置环境变量 |
| `compileSdkVersion 34 not found` | 运行 `sdkmanager "platforms;android-34"` 安装 SDK 平台 |

## 系统要求

- **开发**：JDK 17 + Android SDK 34 + Gradle 8.5（Wrapper 已包含）
- **运行**：Android 12 (API 31) 及以上
