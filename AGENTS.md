# OldBiliRetro 项目记忆（给新会话的说明）

> 每次开始新会话，先读这个文件，再读 `docs/PROJECT_STATE.md` 了解当前状态。
> 这个文件是"项目规则"，改了要同步更新。

## 项目是什么

OldBiliRetro（老版哔哩）—— 面向古董设备的第三方哔哩哔哩 Android 客户端。

- 包名：`com.android.oldbili.retro`
- 技术栈：Java 7 + AGP 2.3.2 + Gradle 3.3，minSdk 6，compileSdk 23
- 界面：严格还原 2013–2014 年 Holo Dark 风格，不要加现代设计
- 开源协议：GPL-3.0；官网：http://136.suzushikaze274.com/

## 硬性规则（不要违反）

1. **不要擅自改版本号/发布 GitHub**：版本号变更、Release、推送，必须先得到用户明确同意。
2. **构建产物只保留两个 APK**：全架构 `OldBiliRetro-*-full.apk` 与 V7 精简 `*-armv7-lite.apk`，放在 `app/build/outputs/apk/`；不要留 `app-release.apk` 等第三个包。
3. **只发布 Release 包**，debug 包不要留。
4. **不要把密码、私钥、Cookie 写进仓库**。FTP/面板/后台密码只在用户本地或聊天里，不落盘到 git。
5. 用户写中文，回复用中文；改功能前先想"老设备（2.3/4.0）会不会崩"，禁止使用高于 minSdk 的 API 而不做版本判断或反射。
6. 截图/日志优先用 `adb` 真机验证：`adb shell screencap -p /sdcard/x.png` + `pull`；2.3 没有 `uiautomator`，也没有 `input tap`。

## 构建命令（Windows）

```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Java\jre1.8.0_491"
$env:GRADLE_USER_HOME = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\gradle-home"
$g = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\gradle\gradle-3.3\bin\gradle.bat"

# 全架构
cmd /c "`"$g`" -p `"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\OldBiliRetro`" assembleRelease"

# V7 精简（只含 armeabi-v7a）
cmd /c "`"$g`" -p `"...\OldBiliRetro`" assembleRelease -Pabi=armeabi-v7a"
```

产物：`app/build/outputs/apk/app-release.apk`（构建后改名/复制成上面两个名字，删除原文件）。

## 发布流程

1. 改 `app/build.gradle` 的 versionCode / versionName
2. 写 `docs/RELEASE_NOTES_v<版本>.md`（`### 更新内容` + `## 已知问题`）
3. `build-tools/make_changelog.py` 的白名单 META 里加新版本，再运行它生成 `app/src/main/assets/changelog.json`
4. 构建两个包 → 只保留两个 APK
5. 联网检查：`build-tools/check_net.ps1`（GitHub 必须全通；不通说明 VPN 没开 TUN）
6. GitHub：
   - 推源码：`git push origin main`
   - 建 Release：**不要用 `gh release create --notes-file`**（Windows 下会按 ANSI 读文件导致中文乱码），用 `build-tools/release_*.py` 里那种"Python + api.github.com + UTF-8 JSON"的方式
   - 上传 APK：`gh release upload <tag> <files> --repo 4786925/OldBiliRetro`
7. 官网（见下）同步：APK 传到 `/obr_files/`，更新 `/obr_data/versions.json` 和 `changelog.json`

## 官网/服务器

- 域名：`136.suzushikaze274.com`，主机是 kangle + easypanel（面板 `http://5.180.98.32:3312/`）
- 网站根目录（FTP 视角）：`OldbiliRetro_website/`
- **注意 easypanel 的目录绑定不能写前导斜杠**：要填 `OldbiliRetro_website`，写 `/OldbiliRetro_website` 会被当绝对路径 → 全站 404
- 数据目录在网站根之外：`/obr_data`（JSON 数据、admin.json、changelog、versions）
- 安装包目录也在网站根之外：`/obr_files`（防止绕过 15 秒等待直链下载）
- kangle **不认 .htaccess**，安全靠"把敏感目录移出 docroot"+ PHP 校验
- HTTPS 证书：Let's Encrypt，`build-tools/acme_issue.py` 可重新签发（HTTP-01 经 FTP webroot）；2027-01-30 到期，已有续期提醒

## 发版技能

已准备技能 `oldbiliretro-release`（文件在 `.dsh/skills/oldbiliretro-release/SKILL.md`，
同时复制到了 `~/.dsh/skills`、`~/.agents/skills`）。
新会话里如果技能列表里能看到，直接 `skill oldbiliretro-release` 加载完整发版流程；
看不到时，按本文件上面的「发布流程」手动执行即可。

## 最近状态

见 `docs/PROJECT_STATE.md`（版本、已修问题、待办）。
