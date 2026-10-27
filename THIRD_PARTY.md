# 第三方组件与参考项目清单 / Third-Party Components

本文件列出 OldBiliRetro 使用的**全部第三方组件**、其版本、许可与用途，以及**参考过的项目**。
如与各组件自身的许可条款冲突，以各组件许可为准。

最后更新：2026-10

---

## 一、运行时库（随 APK 分发）

|组件|版本 / 分支|许可|用途|来源|
|-|-|-|-|-|
|**IJKPlayer**（ijkplayer-java + libijkplayer + libijksdl）|`ff4.0--ijk0.8.8--20210426--001`|**Apache-2.0**|视频播放内核|https://github.com/bilibili/ijkplayer|
|**FFmpeg**|4.0（ijkplayer fork）|**LGPL-2.1-or-later**（编译参数 `--disable-gpl`）|音视频解码、协议|https://github.com/Bilibili/FFmpeg|
|**OpenSSL**|1.0.2u|OpenSSL License + SSLeay License（BSD 风格）|HTTPS / TLS 支持|https://github.com/openssl/openssl|
|**SoundTouch**|`ijk-r0.1.2-dev`（bilibili fork）|**LGPL-2.1**|倍速播放变调|https://github.com/Bilibili/soundtouch|
|**libyuv**|`ijk-r0.2.1-dev`（bilibili fork）|**BSD-3-Clause**|YUV 色彩空间转换|https://github.com/Bilibili/libyuv|
|**DanmakuFlameMaster**|2014 完整版（含 `libndkbitmap.so`）|**Apache-2.0**|弹幕渲染引擎|https://github.com/bilibili/DanmakuFlameMaster|
|**ZXing core**|3.3.0|**Apache-2.0**|二维码生成 / 解析|https://github.com/zxing/zxing|
|**Android Support Library v4**|19.1.0|**Apache-2.0**|老系统兼容（Fragment/ViewPager 等）|AOSP `frameworks/support`（源码 vendor）|
|**RecyclerView**|21.0.0|**Apache-2.0**|列表（源码 vendor）|Android SDK Extras|
|**CardView**|21.0.0|**Apache-2.0**|卡片样式（源码 vendor）|Android SDK Extras|
|**SwetakeQRCode**|2005（改）|原作者免费使用条款|扫码登录二维码|原作者公开源码|

### 关于 FFmpeg 的 LGPL 说明（重要）

1. 本项目编译 FFmpeg 时使用 `--disable-gpl --disable-nonfree`，因此产物为 **LGPL-2.1-or-later**。
2. FFmpeg 以**静态链接**方式编入 `libijkffmpeg.so` 并随 APK 分发。
3. 为满足 LGPL 第 6 条"允许用户用修改后的库替换"的要求，本项目提供：

   * **完整源代码**（本仓库）；
   * **完整的编译脚本与 NDK 版本说明**（见 [docs/BUILD_NATIVE.md](docs/BUILD_NATIVE.md)）；
   * 任何人可重新编译 FFmpeg 与 IJKPlayer，并替换 APK 中的 `libijkffmpeg.so`。
4. **OpenSSL 1.0.2** 采用 OpenSSL/SSLeay 双许可（BSD 风格），与 LGPL 兼容；FFmpeg 的 `--enable-openssl` 属官方支持组合。

---


### 内置字体（assets/fonts）

| 字体 | 版本 | 许可 | 用途 |
|---|---|---|---|
| 霞鹜文楷 LXGW WenKai | Regular | **SIL Open Font License 1.1** | 「楷体」选项 |

该字体按 GB2312 字符集子集化后打包（约 3.5 MB），仅用于界面显示。
OFL 允许随软件一起分发，但**不得单独售卖字体本身**。

## 二、构建期工具（不随 APK 分发）

|工具|版本|许可|用途|
|-|-|-|-|
|Android Gradle Plugin|2.3.2|Apache-2.0|构建|
|Gradle|3.3|Apache-2.0|构建|
|Android SDK Build-Tools|25.0.3|Android SDK License|打包|
|Android NDK|**r14b**（API 9 的 armeabi / armeabi-v7a / x86）、**r16b**（arm64-v8a / x86_64）|Android NDK License|原生库交叉编译|
|JDK|1.7（编译）/ 1.8（Gradle JVM）|Oracle Binary Code License|编译|

---

## 三、参考项目（**不含其源代码**）

以下项目对 OldBiliRetro 的**设计思路、交互行为与老设备兼容策略**提供了重要参考。
本仓库**不包含**这些项目的源代码；开发过程中仅在本地阅读比对，未复制其代码。

|项目|作者|参考内容|
|-|-|-|
|**哔哩终端 修复版（BiliTerminal）**|**AktuelleKamera**|**主要灵感来源**：整体功能取舍、老设备适配思路、API 调用方式参考|
|**哔哩经典（BiliClassic）**|**AktuelleKamera**|**界面风格参考**：Holo Dark 布局、2013 时代控件组织方式、老设备上的性能策略|
|BiliClient-CE|社区开发者|部分接口行为与播放器封装方式参考|

> **特别声明**：本项目与上述项目的作者**没有隶属或合作关系**，也未获得其代码授权。
> 若上述作者认为本项目存在不当借鉴，请通过 Issue 联系，我们会立即调整或致谢方式。

---

## 四、AI 开发声明

本项目的代码、文档、图标与编译脚本**由 AI 生成**：

|角色|名称|
|-|-|
|AI 模型|**DeepSeek**|
|智能体框架|**DeepSeek Harness**|

人类角色仅限于：提出需求、在真机上测试、审核结果与最终决策。

---

## 五、许可兼容性汇总

|本项目许可|GPL-3.0|
|-|-|
|与 Apache-2.0 组件|✅ 兼容|
|与 LGPL-2.1 组件（FFmpeg、SoundTouch）|✅ 兼容（GPL 允许与 LGPL 组合，但整体分发受 GPL 约束）|
|与 BSD-3-Clause（libyuv）|✅ 兼容|
|与 OpenSSL/SSLeay|✅ 兼容（BSD 风格）|

> 注意：由于本项目整体以 GPL-3.0 发布，任何衍生作品在分发时也必须以 GPL-3.0（或兼容许可）开源。

---

## 六、联系与删除请求

若你是上述任一组件的权利人，认为本项目的使用方式不当，请提交 Issue 并标注 `[侵权]`，或邮件联系（`sturdyfawn41583@gmail.com`）。
我们会在核实后 **72 小时内**删除或调整相关内容。

