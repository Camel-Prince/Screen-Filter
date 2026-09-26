# 本次构建验证

日期：2026-09-23。版本：0.1.0（调试侧载版）。

## 已完成

- JDK 17、Gradle 8.11.1、Android Gradle Plugin 8.9.2。
- 编译目标 Android 35；最低系统 Android 14 / API 34。
- `testDebugUnitTest lintDebug assembleDebug`：成功。
- 15 项单元测试全部通过，无失败、无跳过。范围：关键词规范化、字面匹配、空规则、双列卡片选择、边界裁剪、窗口坐标映射、区域去重、旧帧及暂停结果拒绝。
- Android Lint：`No issues found.` 平台原生 Switch 的兼容组件建议在对应字段/方法上注明原因后局部抑制；没有关闭错误检查或建立忽略基线。
- 打包后权限清单检查：不含 `INTERNET` 或 `ACCESS_NETWORK_STATE`。
- APK 签名验证通过（调试签名，v2）。
- APK ZIP 对齐检查通过（包括 16 KiB ZIP 页面对齐；不等于已验证所有设备的原生库运行兼容性）。
- SDK 平台和构建工具下载内容已与官方仓库提供的校验值核对。

## 交付

- `dist/ScreenFilter-0.1.0-debug.apk`，约 50 MiB，包含本地中文 OCR 模型及多种 CPU 架构的原生库。
- SHA-256：`59d00ab6bae01ce55896fe1fc67155877f149de8c1897e62d19f154a7aaeb1c1`。
- 校验文件：`dist/SHA256SUMS.txt`。

## 尚未验证

设备检测没有发现连接的 Android 手机；未运行设备端 UI、触摸穿透、OCR 精度、耗电或目标 App 实测。知乎、小红书、虎扑目前完成的是目标包名配置与通用界面树/OCR处理，不能宣称各版本页面已完整适配。

使用 `DEVICE_TEST_PLAN.md` 继续真机验收。应用内演示页只展示模拟内容，不替代真实窗口截图和追踪测试。

本机原无 Android 开发环境，本次 JDK/SDK/Gradle 位于临时目录。`local.properties` 指向该临时 SDK；若临时目录被清理，用 Android Studio 配置常规 SDK 路径即可重新构建。

## 2026-09-24：0.2.0 兼容测试版

针对用户提供的 nova 12 Ultra / HarmonyOS 4.2.0 增加 API 30–33 的界面文字模式，不截图、不初始化 OCR 识别器，图片文字开关置灰；API 34+ 保留窗口 OCR。仅降低安装门槛并增加接口分支，不代表已确认该机的 Android API 等级或完成真机适配。

- 最低 API 30；版本号 0.2.0 / versionCode 2。
- 15 项单元测试通过；随后修复资源配置并完成最终 `lintDebug assembleDebug`。
- 最终 Lint：No issues found.
- APK 签名验证通过；合并清单无网络权限。
- 安装包：`dist/ScreenFilter-0.2.0-compat-debug.apk`。
- SHA-256：`981b6cfc939e67b0c40d93f364c6a65e904aaa1e63b0778c7e63c259f2694cd9`。
- API 30–33 真机启动、无障碍权限、文字可读性、遮罩触摸和耗电均待测试。

## 2026-09-26：0.3.0 图文体验版

三轮修改经过编译/逻辑测试/静态复查，见 `ITERATIONS_0.3.0.md`。最终代码执行 `testDebugUnitTest lintDebug assembleDebug` 成功。

- 最低 API 30，目标 API 35，versionCode 3 / versionName 0.3.0。
- 30 项单元测试全部通过，0失败、0错误、0跳过。15项原有几何/匹配测试，加15项模型协议、HTTPS地址、响应拒绝、页面版本、唯一锚点/反向滚动、安慰主题和图文请求构造测试。
- 最终 Android Lint：No issues found.
- 合并清单增加 INTERNET；无 ACCESS_NETWORK_STATE。云端默认关闭，单独授权后才启用模型请求。安全窗口截图不绕过。
- 签名验证通过（v2调试签名）；与0.2.0签名一致。包名不变，版本号递增，用于覆盖安装。
- ZIP对齐检查通过（16KiB），不代替原生库设备兼容性验证。
- APK：`dist/ScreenFilter-0.3.0-vision-debug.apk`，52,674,065字节。
- APK SHA-256：`e6034a97f02868140cc9798265ab1e6ca9dcd86c594b5ea7d7c05945a48b0021`。
- ZIP：`ScreenFilter-0.3.0-vision-transfer.zip`，52,674,233字节，仅包含同一APK，解压后逐字节校验一致。
- ZIP SHA-256：`14aa8e920a22597bca545b02ad86cadd8e43883367e5908d09922d807c7c14a9`。

验证边界：没有连接手机或Android模拟器，没有模型Key，未执行新版真机UI、触摸、截图、耗电、真实模型API/定位准确率测试。内置红蓝方块测试供用户在手机上验证连接。不得将30项逻辑测试描述为30项真机测试。

## 2026-09-26：0.3.1 小红书排查修订

针对用户反馈相关帖子未遮挡，修正无变化的内容通知反复取消慢请求的路径，并增加返回后的本地截图复核和可复制诊断。该缺陷已由代码和模拟事件序列定位，但尚未取得用户设备诊断，不能确认是其手机此次故障的唯一原因。

- 最终 `testDebugUnitTest lintDebug assembleDebug` 成功；36项单元测试全部通过，0失败/错误/跳过。
- 新增6项测试覆盖重复通知不饿死慢请求、真实页面变化/返回旧页、纯图片变化、渲染噪声、顿号分隔、无关横幅变化与候选区域复核。原30项通过。
- Android Lint：No issues found.
- 复核采用48×72颜色样本，是启发式变化检查；不是光流、内容语义判等或首帧零曝光保证。普通模式只复核命中区域；先遮后审和零命中结果检查整图。增加一次本地截图和额外等待，复核截图不上传。
- 密钥、截图、规则正文不写入诊断；用户按按钮复制会话计数。兼容截图受多窗口限制时，本地关键词路径继续工作。
- 最低API30，versionCode4/versionName0.3.1。
- ScreenFilter-0.3.1-vision-debug.apk：53034869字节，SHA-256 `005e9b63e9baa6b321997b5c85c5ca57ec63d0903dd4d1e62eac36ce7a750be4`。
- ScreenFilter-0.3.1-vision-transfer.zip：53035037字节，SHA-256 `cb6d194e53b3427f8835ed8e11ef45f40cb8906c0b77207b266dff50b4327f13`。

尚无新版真机、真实千问调用或小红书准确率结果。用户未提供密钥，不请求其发送密钥。

## 2026-09-26：0.3.2 模型运行日志

按用户要求增加应用内持久日志。每次图文识别和内置连接测试独立编号，覆盖截图、请求、HTTP、区域解析、复核和终态。日志异步写入应用私有 no-backup 目录的 AtomicFile；与过滤设置使用独立偏好文件，切换日志开关不触发过滤规则重置。

- 最终 `testDebugUnitTest lintDebug assembleDebug` 成功；45项单元测试全部通过，0失败/错误/跳过。
- 新增9项逻辑测试覆盖中文及多行日志重载、400条淘汰、UTF-8字节上限、损坏/超大文件拒绝、清空编码、原样和JSON转义密钥过滤、Bearer/常见Key过滤、图片数据省略、截断边界不泄露部分密钥、诊断信息可读性。
- Android Lint：No issues found. 修正文案资源提示，未增加忽略基线。
- 原始回复默认不记录，需单独确认开启。只有开启后新开始的请求可记录，写入前及写入时复查开关；清空增加日志代次，旧请求迟到回调不回填。关闭开关保留历史记录，清空才删除。
- 日志不包含截图或请求正文；原始服务回复可能包含页面文字。配置密钥、常见密钥及图片数据在排队写盘前过滤，单条原始正文约6000字符上限。日志页禁截图，复制含原始回复时提示个人信息；没有自动上传日志。
- 最低API30，versionCode5/versionName0.3.2。最终APK内包名和版本已核对。
- 调试签名校验通过，与旧版一致；16KiB ZIP对齐校验通过；ZIP内APK逐字节一致。
- ScreenFilter-0.3.2-vision-debug.apk：53098741字节，SHA-256 `d522935e4d05f68153751d6d63eaeeebc77c6dd972a6afaff8208f8ac20fdb00`。
- ScreenFilter-0.3.2-vision-transfer.zip：53098909字节，SHA-256 `f13153d5784b2e2c7a40307210bb4d8a0564f23f3f24783411ec2f47b788ccc9`。

验证边界：单元测试验证日志核心数据与脱敏逻辑；没有Android设备/模拟器或真实千问Key，尚未完成日志页面交互、手机重启持久性、连续运行耗电、华为截图和真实模型准确率验收。具体设备验收项见 `DEVICE_TEST_PLAN.md`。
