# 手表TXT（Watch TXT Reader）

把手机里的 `.txt` 文档发送到 **Samsung Galaxy Watch6（Wear OS 4）**，在手表上阅读。

- **手机端（mobile）**：选择 TXT（可多选），或在「我的文件」里直接「分享」TXT 到本应用，通过 Wear OS 数据层（Wearable Data Layer / ChannelClient）发送到手表。自动识别编码：UTF-8（含 BOM）、UTF-16，以及 GBK/GB18030 的中文 TXT，统一转成 UTF-8 发送。界面显示已连接的手表名称和发送记录（已送达 / 失败）。
- **手表端（wear）**：后台接收文件，保存在应用私有目录。打开应用可看到文件列表，点按打开阅读；阅读页支持**旋转表圈（Watch6 触摸表圈）滚动**、字号 A−/A+ 调节（会记住）、自动记住每本的阅读位置；列表中可进入「删除文件…」模式删除文件。

> 两个 APK 的包名都是 `com.carloshee.watchtxt`，并且**必须用同一个签名**，否则手机和手表之间无法传输。请始终安装**同一次构建 / 同一个 Release** 里的两个 APK。

---

## 下载

在 GitHub 仓库的 **Releases**（打 `v*` 标签时自动生成）或 **Actions → 某次构建 → Artifacts** 里下载：

- `watchtxt-phone.apk` —— 装在手机上
- `watchtxt-watch.apk` —— 装在手表上

## 安装

### 1. 手机

把 `watchtxt-phone.apk` 下载到手机，点开安装即可（第一次需要允许「安装未知应用」）。

### 2. 手表（Galaxy Watch6）

手表不能直接点 APK 安装，需要用 ADB 无线调试：

1. **打开开发者选项**：手表「设置 → 关于手表 → 软件信息」，连续点「软件版本」约 5 次，直到提示开发者模式已开启。
2. **打开调试**：「设置 → 开发者选项」，打开 **ADB 调试** 和 **无线调试（Wi‑Fi 调试）**。手表和电脑/手机要连同一个 Wi‑Fi。
3. 在「无线调试」里点 **配对新设备**，屏幕上会显示 **配对码** 和 `IP:端口`。

**方法 A：用电脑（已安装 Android platform-tools）**

```bash
adb pair 192.168.1.23:37123        # 配对用的 IP:端口，然后输入手表上的 6 位配对码
adb connect 192.168.1.23:41234     # 注意：这是「无线调试」主页面显示的 IP:端口，和配对端口不同
adb install -r watchtxt-watch.apk
```

**方法 B：只用手机**

在手机上安装 **Bugjaeger** 或 **Easy Fire Tools**（应用商店可下载），按应用里的提示输入手表的 IP、端口和配对码连接手表，然后选择 `watchtxt-watch.apk` 安装到手表。

装好后建议在手表上关闭「无线调试」（省电）。

### 更新 / 签名说明

- 如果仓库没有配置签名密钥，CI **每次构建都会生成一个新的临时密钥**：同一次构建的手机端和手表端签名相同（可以正常通信），但和上一次构建不同。此时更新需要**先卸载**手机和手表上的旧版本，再安装新的两个 APK。
- 想让以后的版本能直接覆盖安装，请生成一个固定密钥并配置到仓库 **Settings → Secrets and variables → Actions**：

```bash
keytool -genkeypair -keystore watchtxt.jks -alias watchtxt -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 watchtxt.jks   # 把输出内容填到 KEYSTORE_BASE64
```

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 base64 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名（上例为 `watchtxt`） |
| `KEY_PASSWORD` | 密钥密码（不填则使用 keystore 密码） |

## 使用

1. 确认手机的 **Galaxy Wearable** 已连接手表，并且手表上已安装「手表TXT」（手机端顶部会显示「已连接手表：Galaxy Watch6 …」）。
2. 发送文件（两种方式任选）：
   - 打开手机上的「手表TXT」，点 **选择 TXT 文件发送到手表**，可多选；
   - 或在 **我的文件** 里长按一个或多个 `.txt` → **分享** → 选择 **手表TXT**。
3. 发送记录会显示「已送达」。在手表上打开「手表TXT」，点文件名开始阅读：
   - 转动表圈 / 手指在表圈上滑动即可上下滚动，也可以直接触摸滑动；
   - 文首和文末有 **A− / A+** 调节字号；
   - 从左向右滑动返回列表；
   - 列表底部 **删除文件…** → 点选文件 → 确认删除。

> 同名文件再次发送会覆盖手表上的旧文件。单个文件上限 20 MB。

## 自己构建

需要 JDK 17 和 Android SDK（compileSdk 34）：

```bash
./gradlew :mobile:assembleDebug :wear:assembleDebug
# 输出：mobile/build/outputs/apk/debug/mobile-debug.apk
#       wear/build/outputs/apk/debug/wear-debug.apk
```

设置环境变量 `WATCHTXT_KEYSTORE`、`WATCHTXT_KEYSTORE_PASSWORD`、`WATCHTXT_KEY_ALIAS`、`WATCHTXT_KEY_PASSWORD` 后，两个模块都会用这个 keystore 签名；否则都使用本机的默认 debug 签名（同一台电脑上两者一致）。

推送到 `main`、打 `v*` 标签或手动运行时，`.github/workflows/build.yml` 会自动构建；打标签时会创建 Release 并附上两个 APK。

## 技术要点

- 传输：`ChannelClient`，路径 `/txt/<URL 编码的文件名>`，内容为 UTF-8。手表保存成功后以错误码 0 关闭通道，手机据此显示「已送达」。
- 手机通过 Capability `watchtxt_receiver` 找到安装了手表端的手表。
- 版本：AGP 8.5.2、Gradle 8.7、Kotlin 1.9.24、Compose BOM 2024.06.00、Wear Compose 1.3.1、play-services-wearable 18.2.0。
