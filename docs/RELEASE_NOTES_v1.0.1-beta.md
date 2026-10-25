## OldBiliRetro 1.0.1 beta（下载修复）

### 修复内容

**下载全部清晰度都失败（HTTP 403）** —— 两个根因：

1. **取流只拿到 http 化地址**
   `collectUrls` 在排序时，只要已经加入过「http 化」版本，就会把 https 原始地址丢弃。
   arm64 设备因此只剩 `http://upos-*.bilivideo.com/...`，而 B 站 CDN 已关闭明文 http 口 → **403**。
   → 现在 https 原始地址**始终保留**。

2. **下载请求的 UA 被 CDN 拒绝**
   下载器原先使用自制 UA `Mozilla/5.0 (Linux; Android 4.0; OldBiliRetro)...`，
   CDN 按 UA 校验后直接 **403**（播放器用的是浏览器 UA，所以播放正常）。
   → 下载 UA 改为与播放器一致，并补 `Origin` / `Accept` 头。

### 额外改进

- 下载前把 CDN 的 `http://` 地址**自动规范化为 `https://`**（B 站已拒绝明文 http）
- 下载改为**多候选线路重试**：https 优先，任一条失败自动换下一条，全部失败才报错
- 换线路时自动清理半截 `.part` 文件，避免续传出错

### 验证

Pixel 9（Android 17，arm64-v8a）实测：

- 选择 **1080P** 下载 → 成功，文件 `av117498192071075_1080P高清.mp4`（4.4 MB）
- 文件头校验：`00 00 00 20 66 74 79 70 69 73 6f 6d`（`ftypisom`，合法 MP4）
- 日志中不再出现 `线路[n]失败: HTTP 403`

### 安装包

- `OldBiliRetro-1.0.1-beta.apk`（14.5 MB，已签名）
- `versionCode 2` / `versionName 1.0.1 beta`
- 覆盖安装即可，无需卸载（签名不变）

完整变更：https://github.com/4786925/OldBiliRetro/commit/53f9e79
