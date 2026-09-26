# 屏幕过滤 · Android 基线

面向 Android 11+（API 30+）的本地关键词遮挡原型；Android 14+ 支持额外的窗口 OCR，较低版本使用不截屏的界面文字兼容模式。优先处理知乎 `com.zhihu.android`、小红书 `com.xingin.xhs`、虎扑 `com.hupu.games` 的标准版本。**已配置目标包名不等于已完成这三个 App 的真机适配。**

已生成侧载安装包：`dist/ScreenFilter-0.2.0-compat-debug.apk`。本次编译、15 项单元测试和静态检查均通过；尚未真机验收，详见 [构建验证记录](docs/BUILD_VERIFICATION.md)。

## 下载安装包

- **[打开 0.2.0 兼容测试版下载页](https://github.com/Camel-Prince/Screen-Filter/releases/tag/v0.2.0)**：展开页面下方 **Assets**，选择 `ScreenFilter-0.2.0-compat-debug.apk`，不要选择 Source code。
- [直接下载 0.2.0 APK](https://github.com/Camel-Prince/Screen-Filter/releases/download/v0.2.0/ScreenFilter-0.2.0-compat-debug.apk)：Android 11+；Android 11–13 使用界面文字模式，Android 14+ 可开启窗口 OCR。
- [手机下载 ZIP 后解压安装](https://github.com/Camel-Prince/Screen-Filter/releases/download/v0.2.0/ScreenFilter-0.2.0-compat-transfer.zip)：APK 下载一直停在 100% 时可尝试。使用手机文件管理解压，再打开其中的 APK；内容与直接下载的 APK 完全相同，不是新的兼容性修复版本。
- [备用仓库原始文件下载](https://github.com/Camel-Prince/Screen-Filter/raw/refs/heads/main/dist/ScreenFilter-0.2.0-compat-debug.apk)：此入口会跳转到 `raw.githubusercontent.com`，部分网络可能无法访问。
- [下载 0.1.0 原始测试版 APK](https://github.com/Camel-Prince/Screen-Filter/raw/refs/heads/main/dist/ScreenFilter-0.1.0-debug.apk)：仅 Android 14+。
- [SHA-256 校验值](dist/SHA256SUMS.txt)。两个安装包均为调试签名的测试版，尚未完成真机验收。

可直接在手机浏览器下载，或在电脑下载后通过微信/QQ 发送为文件。请优先使用 **0.2.0-compat**；若通讯软件将后缀改为 `.apk.1`，保存到手机后改回 `.apk` 再安装。

如果手机能打开 GitHub 网页，却无法下载文件，可能是下载域名的网络访问问题。可先试上述 Releases 入口；它仍依赖 GitHub 的下载网络。如果两个入口都失败，可在电脑下载后通过微信文件传输助手、QQ 或 USB 传到手机。浏览器明确提示风险拦截时，请保留提示信息以便区分网络故障与系统下载限制。

## 当前功能

- 用户自定义关键词（逐行或逗号分隔，最多 100 个），命中任意关键词就遮挡。
- 界面文字快速匹配；Android API 34+ 额外使用内置 ML Kit 中文 OCR。API 30–33 只读取无障碍界面文字，不截屏，OCR 开关置灰。
- 匹配忽略大小写、全角/半角和空白；使用字面子串，不是语义分类或正则表达式。
- 优先根据无障碍树中的列表/网格子项推断卡片边界；无可靠边界时只遮文字块。不会猜测小红书整屏两列布局，也不保证识别所有版本的卡片。
- 完全不透明的装饰性大方块遮罩，非原图像素化；避免保留图像轮廓。
- 读取新坐标更新遮罩；页面变化/滚动后立即丢弃旧结果。当前是轮询重新定位，**不是光流跟踪或逐帧运动预测**。
- 暂停按钮、系统快捷设置开关、服务权限说明、应用内模拟演示。
- 仅本地处理，截图和读取内容不写文件、不打印日志；不申请网络权限。

## 使用

已针对用户的 nova 12 Ultra / HarmonyOS 4.2.0 增加较低 Android API 的兼容路径，但尚未读取这台手机的实际 API 等级，也未进行真机验收。它不是鸿蒙 NEXT 原生版本。

1. 安装调试 APK（若已有构建，见 `dist/`）。最低 Android 11 / API 30；HarmonyOS 版本号不等于 Android API 等级，是否可安装及运行仍需实机确认。
2. 打开「屏幕过滤」，填写关键词，选中需要过滤的 App。
3. 点击「保存并开启过滤」，阅读说明并同意，到系统无障碍设置中开启「屏幕过滤」。
4. 返回目标 App。可先用该页面上确实存在的独特文字测试，避免把导航标签设成屏蔽词。
5. 下拉快捷设置中的「屏幕过滤」可以暂停；也可随时回到应用点击「立即暂停」。

首次使用侧载 APK 时，部分系统可能阻止打开无障碍服务。若系统提示「受限设置」，按手机系统提供的应用信息页面说明处理；不同厂商步骤不同。服务被系统关闭后需重新开启。卸载应用即可移除设置。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。Gradle Wrapper 使用 8.11.1，Android Gradle Plugin 使用 8.9.2。

在 Android Studio 中打开本目录，让其安装 SDK 和同步依赖；或者配置 `ANDROID_HOME` / `local.properties` 后执行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。调试包用于侧载测试，不是商店发行签名版本。

## 为什么用无障碍服务

此基线使用用户主动授权的 `AccessibilityService` 和 `TYPE_ACCESSIBILITY_OVERLAY`；仅 API 34+ 调用 `takeScreenshotOfWindow`。不使用 MediaProjection，不需要单独申请普通悬浮窗权限；无障碍授权是必需的。Android 14 的窗口截图能排除无障碍遮罩，避免识别自身。遮罩设置为不接收触摸，不代理点击或滑动，不请求手势控制。

服务接收全局窗口变化事件以知道何时离开目标 App，但只遍历选中包名的界面内容；仅 API 34+ 且 OCR 开启时截取其当前活动窗口。输入法出现、锁屏或非目标窗口时停止。设置页中的开关还受用户同意状态约束；单独在系统中打开服务不会绕过产品内说明。

## 数据与边界

- 应用自身保存关键词、选择的 App、授权说明同意状态及开关；依赖组件可能维护本地运行元数据。禁用云端备份和设备迁移。
- OCR 模型随 APK 打包；合并清单移除依赖带入的网络权限。内存截图在识别完成后释放。
- 文字节点轮询目标间隔 120 ms，OCR 请求最短间隔 900 ms，均为调度配置，非实测延迟或 FPS 保证。OCR 单次在途，不堆积帧；旧页面结果通过版本标记作废。
- OCR 遮罩最长保留 1600 ms；截图失败退避 5 秒，界面文字路径继续工作。
- “擦边”“软色情”等视觉语义、同义话题、讽刺及无文字图片不在当前能力范围。
- API 30–33 兼容模式无法识别不向无障碍服务提供文字的内容，尤其是图片内文字及部分自绘页面。API 34+ 无卡片结构的自绘界面只能依赖 OCR 文本块。文字可能被拆成多个节点/块而漏检；不同内容的误合并也可能误遮。
- 滚动中会短暂清除过期遮罩并重新定位，存在曝光、闪烁、延迟；不保证用户完全看不到内容。
- 安全窗口、特殊页面、浮窗、多窗口、旋转及不同厂商系统需要真机验收；遇到受保护截图不绕过保护。
- 当前设置按 App 生效，不区分首页/详情页；应谨慎选择容易出现在导航栏的词。
- 应用内演示使用合成卡片，不是目标 App 的测试结果。发布前还需按目标商店要求完成无障碍权限用途申报与审核。

## 文件导航

- `MainActivity.java`：设置、授权说明与开关。
- `FilterAccessibilityService.java`：窗口选择、读取、截图、OCR 与生命周期。
- `NodeReader.java`：从界面树提取文字和候选卡片。
- `core/`：可独立测试的关键词与矩形策略。
- `MosaicView.java`：不透明遮罩。
- `docs/DEVICE_TEST_PLAN.md`：三个 App 的真机验收步骤。

## 依据

- [Android 无障碍服务与窗口截图](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshotOfWindow(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback))
- [Android 窗口类型与触摸规则](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [ML Kit 本地中文 OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
