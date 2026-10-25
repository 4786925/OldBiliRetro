# OldBiliRetro · 老版哔哩

> 面向**古董设备**的第三方哔哩哔哩客户端：Android 2.3 ~ Android 17 全兼容，严格还原 2013–2014 年 Holo Dark 界面。

![minSdk](https://img.shields.io/badge/minSdk-6%20(Android%202.0.1)-green)
![ABI](https://img.shields.io/badge/ABI-armeabi%20%7C%20v7a%20%7C%20arm64%20%7C%20x86%20%7C%20x86__64-blue)
![License](https://img.shields.io/badge/License-GPL--3.0-orange)
![AI](https://img.shields.io/badge/Developed%20by-AI%20100%25-purple)

**语言：** **中文** · [English](README.en.md) · [日本語](README.ja.md)

---

## ⚠️ 重要声明（请先阅读）

1. **本项目完全由 AI 开发。** 全部代码、图标、文档、编译脚本均由 AI（DeepSeek 模型 + DeepSeek Harness 智能体框架）生成；人类仅负责提出需求、实机测试与最终决策。
2. **本项目与哔哩哔哩（bilibili，上海宽娱数码科技有限公司）没有任何关联**，不是官方客户端，也未获得官方授权或认可。
3. 应用内的视频、弹幕、评论、番剧等内容，**版权均归原作者及哔哩哔哩所有**；本应用只是访问官方公开接口的第三方客户端。
4. 请勿将本项目用于**商业用途、批量抓取、绕过付费/会员限制、二次分发付费内容**等行为。
5. 使用第三方客户端存在**账号被风控、限流甚至封禁**的风险，请自行评估并承担。
6. 完整法律条款见 **[DISCLAIMER.md](DISCLAIMER.md)**；第三方库许可见 **[THIRD_PARTY.md](THIRD_PARTY.md)**。

---

## 特性

- **登录**：扫码登录 / Cookie 登录 / 手动导入 Cookie / 导出 Cookie（老设备没有可用 WebView 也能登）
- **首页**：登录后个性化推荐，未登录默认热门；推荐 / 热门 / 直播 / 番剧 / 影视分区
- **搜索**：视频、番剧、影视、直播、用户；AV 号 / BV 号直达；20 条历史 + 一键清空；翻页保持滚动位置
- **视频详情**：分 P 选集、合集、相关推荐、UP 主入口、简介长按复制
- **播放器（自编译 IJKPlayer）**：
  - 固定横屏（不再跟随重力翻转）
  - 清晰度按服务端实际可用档位动态显示（360P / 720P / 1080P / 1080P60 / 2K）
  - 播放流格式可选：**DASH**（全清晰度，默认）/ **MP4**（单流最稳）
  - 手势：亮度 / 音量 / 进度 / 双击暂停；**长按 2 倍速**
  - 倍速菜单：0.75x / 1.0x / 1.25x / 1.5x / 2.0x
  - 播放中可实时调整弹幕（字号/透明度/速度/行数），与设置页同步
  - 顶部：返回 + 标题 + 点赞 / 投币 / 分享 / 关注；底部：进度条在上，播放/弹幕开关/弹幕设置/发弹幕，右侧倍速 + 清晰度
- **弹幕**：DanmakuFlameMaster 完整版（描边、阴影、碰撞检测、位图缓存）；发送弹幕支持颜色/位置
- **评论**：查看 / 回复 / 楼中楼 / 点赞 / 发表；图文评论
- **个人空间**：任意 UP 的视频 / 动态 / 简介；关注状态识别
- **动态 / 消息 / 收藏夹 / 追番**：缓存与清理策略可控
- **下载**：分 P 下载、断点续传、清晰度选择
- **设置**：
  - 无痕模式、**无图模式**、**清除缓存**
  - 播放流格式、默认清晰度、解码方式
  - **兼容性列表**、利用规约、设备信息检测、崩溃日志导出
  - 退出登录时**一并清除消息/动态/收藏缓存**，避免换人使用泄露

---

## 兼容性

### 系统与架构

| ABI | 最低 API | 对应 Android | 原生播放器 | HTTPS |
|---|---|---|---|---|
| armeabi（ARMv5） | **9** | 2.3 ~ 2.3.7 | 自编译 | ✅ |
| armeabi-v7a（ARMv7） | **9** | 2.3 ~ 4.4 | 自编译 | ✅ |
| arm64-v8a | 21 | 5.0 ~ 17 | 自编译 | ✅ |
| x86 | **9** | 2.3 ~ 7.x | 自编译 | ✅ |
| x86_64 | 21 | 5.0+ | 自编译 | ✅ |

- `minSdkVersion 6`（Android 2.0.1），**实测可完整使用的最低版本为 Android 2.3**
- `targetSdkVersion 26`，可安装运行到 Android 16 / 17
- 应用内置兼容性列表页面（首次安装同意协议前、以及设置里均可查看）

### 清晰度

| 清晰度 | 可用性 | 说明 |
|---|---|---|
| 360P | 全平台 | 单流 |
| 480P | ❌ 已下架 | B 站服务端已不再提供 32 档，选择后会被降到 360P |
| 720P | 全平台 | 单流 |
| 1080P | 多数视频 | 单流，部分需要登录 / 大会员 |
| 1080P 60 帧 | 部分视频 | 需 DASH，Android 2.x 隐藏 |
| 2K | 少数视频 | 需 DASH + 大会员，Android 2.x 隐藏 |

> 清晰度列表是动态的：应用按服务端实际返回的档位显示，不同视频可能不同。
> 同一视频短时间内反复切换清晰度可能触发服务端限流降级（会明确提示"服务端已降级"）。

---

## 构建

刻意停留在 2013–2014 年的"黄金组合"，与参考项目保持一致：

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 2.3.2 |
| Gradle | 3.3 |
| JDK | 7（编译）/ 8（运行 Gradle） |
| compileSdk / buildTools | 23 / 25.0.3 |

```bash
# 发布包（签名 + 混淆 + 资源压缩）
gradlew.bat assembleRelease

# 产物
app/build/outputs/apk/app-release.apk
```

签名配置读取 `local.properties`（**该文件已被 .gitignore 排除，请勿上传**）：

```properties
KEY_PATH=/absolute/path/to/your.jks
KEY_PASSWORD=******
ALIAS_NAME=your_alias
ALIAS_PASSWORD=******
```

> 没有 `local.properties` 时也能编译出未签名包，但安装前需自行签名。

### 原生播放器（可选）

`app/src/main/jniLibs/` 下 5 个 ABI 的 `libijkffmpeg.so` / `libijkplayer.so` / `libijksdl.so` 均为**项目自行编译**：

- IJKPlayer（FFmpeg `ff4.0--ijk0.8.8--20210426--001` 分支）
- FFmpeg 4.0（`--disable-gpl`，LGPL-2.1+）
- OpenSSL 1.0.2u（静态链入，用于 HTTPS 播放）
- SoundTouch `ijk-r0.1.2-dev`（倍速）
- libyuv `ijk-r0.2.1-dev`（色彩转换）
- NDK：r14b（API 9 的 armeabi/armeabi-v7a/x86）、r16b（arm64-v8a / x86_64）

编译脚本与说明整理在 [docs/BUILD_NATIVE.md](docs/BUILD_NATIVE.md)（可选，普通用户无需关心）。

---

## 依赖与参考

### 第三方库

| 库 | 版本 | 许可 | 用途 |
|---|---|---|---|
| IJKPlayer | ff4.0--ijk0.8.8 | Apache-2.0 | 播放内核 |
| FFmpeg | 4.0（ijk 分支） | **LGPL-2.1+** | 音视频解码 |
| OpenSSL | 1.0.2u | OpenSSL/SSLeay | HTTPS |
| SoundTouch | ijk-r0.1.2-dev | LGPL-2.1 | 倍速变调 |
| libyuv | ijk-r0.2.1-dev | BSD-3-Clause | 色彩转换 |
| DanmakuFlameMaster | 2014 | Apache-2.0 | 弹幕引擎 |
| ZXing | core 3.3.0 | Apache-2.0 | 二维码 |
| Android Support Library | v4 19.1.0 | Apache-2.0 | 兼容层（源码 vendor） |
| RecyclerView / CardView | 21.0.0 | Apache-2.0 | 列表与卡片（源码 vendor） |
| SwetakeQRCode | 2005 | 原作者免费条款 | 登录二维码 |

详见 **[THIRD_PARTY.md](THIRD_PARTY.md)**。

### 参考项目与灵感来源（特别致谢）

| 项目 | 作者 | 关系 |
|---|---|---|
| **哔哩终端 修复版（BiliTerminal）** | **AktuelleKamera** | **主要灵感来源与行为参考** |
| **哔哩经典（BiliClassic）** | **AktuelleKamera** | **界面风格与老设备兼容策略参考** |
| BiliClient-CE | 社区 | 部分接口行为参考 |

本项目的界面风格、老设备兼容思路（包括低内存弹幕裁剪、API 9 编译链选择）来自上述项目的启发。
本项目**未复制其源代码**；`ref/` 目录下的参考源码仅用于阅读比对，**不随仓库发布**。

---

## 贡献者

| 角色 | 贡献者 |
|---|---|
| 需求、测试、决策 | 项目发起人（人类） |
| **代码生成 / 文档撰写 / 图标绘制 / 编译脚本** | **DeepSeek**（AI 模型） |
| **智能体框架 / 工程编排** | **DeepSeek Harness** |
| **灵感来源与参考项目** | **AktuelleKamera**（哔哩终端修复版、哔哩经典） |

---

## 许可

本项目以 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

- 本项目代码：GPL-3.0
- FFmpeg：**LGPL-2.1+**（编译时 `--disable-gpl`），以静态方式链接进 `libijkffmpeg.so`。
  为满足 LGPL 的"可重新链接"要求，**完整源代码与编译脚本均已公开**，任何人可自行重新编译并替换该 `.so`。
- 其他第三方组件许可见 [THIRD_PARTY.md](THIRD_PARTY.md)。

---

## 免责声明（摘要）

本项目按 **"现状"（AS IS）** 提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性与非侵权担保。
作者与贡献者**不对**因使用本软件产生的任何直接、间接、附带、特殊或后果性损害承担责任，包括但不限于账号封禁、数据丢失、设备损坏、法律纠纷。

本项目**非官方**，与哔哩哔哩无任何隶属关系；"哔哩哔哩"、"Bilibili"、小电视图标等商标归其权利人所有。
若权利人认为本项目侵犯其权益，请通过 Issue 或邮件联系，我们会在核实后**立即删除相关内容**。

**完整条款请阅读 [DISCLAIMER.md](DISCLAIMER.md)，使用即视为同意。**
