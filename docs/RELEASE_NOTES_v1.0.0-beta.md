## OldBiliRetro 1.0.0 beta

面向**古董设备**的第三方哔哩哔哩客户端，严格还原 2013–2014 年 Holo Dark 界面。

> ⚠️ **本项目完全由 AI 开发**（DeepSeek 模型 + DeepSeek Harness 框架），与哔哩哔哩官方**没有任何关联**。
> 使用前请阅读 [DISCLAIMER.md](https://github.com/4786925/OldBiliRetro/blob/main/DISCLAIMER.md)。

### 安装包

- `OldBiliRetro-1.0.0-beta.apk`（14.5 MB，已签名，release 构建）
- `minSdkVersion 6`（Android 2.0.1），**实测最低可用 Android 2.3**
- 内置 5 个 ABI 的原生播放器：`armeabi` / `armeabi-v7a` / `arm64-v8a` / `x86` / `x86_64`

### 兼容性

| ABI | 最低 API | Android | 原生播放器 | HTTPS |
|---|---|---|---|---|
| armeabi（ARMv5） | 9 | 2.3 ~ 2.3.7 | 自编译 | ✅ |
| armeabi-v7a | 9 | 2.3 ~ 4.4 | 自编译 | ✅ |
| arm64-v8a | 21 | 5.0 ~ 17 | 自编译 | ✅ |
| x86 | 9 | 2.3 ~ 7.x | 自编译 | ✅ |
| x86_64 | 21 | 5.0+ | 自编译 | ✅ |

### 清晰度

- **360P / 720P**：所有设备可用（单流）
- **1080P**：多数视频可用（需登录/大会员）
- **1080P60 / 2K**：需 DASH 模式，Android 2.x 自动隐藏
- **480P**：B 站服务端已下架该档，选择后会被降到 360P

### 主要功能

扫码/Cookie 登录、个性化首页、搜索（AV/BV 直达）、视频详情与分P、自编译 IJK 播放器（固定横屏、DASH/MP4 可选、长按 2 倍速、倍速 0.75x~2.0x）、弹幕（含播放中实时调整）、评论、个人空间、动态、消息、收藏夹、追番、下载、无图/无痕模式、清除缓存、兼容性列表。

### 依赖与致谢

- [IJKPlayer](https://github.com/bilibili/ijkplayer) · FFmpeg 4.0 · OpenSSL 1.0.2u · SoundTouch · libyuv · [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) · ZXing
- **灵感来源与参考项目**：[哔哩终端 修复版](https://github.com/AktuelleKamera) 与 **哔哩经典（BiliClassic）** —— 作者 **AktuelleKamera**
- **贡献者**：DeepSeek（AI 模型）、DeepSeek Harness（智能体框架）

许可见 [LICENSE](https://github.com/4786925/OldBiliRetro/blob/main/LICENSE)（GPL-3.0）。
