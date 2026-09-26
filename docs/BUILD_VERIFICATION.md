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
