# OldBiliRetro 当前状态快照

> 最后更新：2026-11-08
> 每次大改动（发版、修重大 bug、服务器变更）后，更新这个文件。

## 版本与产物

| 项目 | 值 |
|---|---|
| 当前版本 | **1.0.6 beta**（versionCode **8**，2026-11-08 重新打包：动态评论 + 消息加载修复） |
| Git 提交 | 见 git log 最新 |
| GitHub Release | https://github.com/4786925/OldBiliRetro/releases/tag/v1.0.6-beta |
| 官网 | http://136.suzushikaze274.com/ |
| 全架构包 | `OldBiliRetro-1.0.6-beta-full.apk`（15,212,860 字节，MD5 `DFF17AF0A82ACF68590EE050D31A0D93`） |
| V7 精简包 | `OldBiliRetro-1.0.6-beta-armv7-lite.apk`（3,216,028 字节，MD5 `40204C1E1CCFBC11912E88CD1A040E7F`） |

## 2026-11-08 追加修复

- 动态（图文动态）评论区打不开、报 `-400 invalid oid`：评论类型原来写死 `type=1`（视频），
  现在 `CommentListActivity` 支持 `type` 参数（动态先用 17，失败自动换 11 重试）
- 首次登录消息列表要几分钟：`getSessions` 原来对每个会话**逐条**请求名片，
  现在改成 `account/v1/user/cards` **批量补**（每次 50 个，最多 2 批），并优先读本地名片缓存

## 官网 2026-11-08 更新

- 更新日志：最新在上，分页 10 条/页（可切 20/40）
- 公告系统：后台新增「公告」页，支持**定时发布**（发布时间未到不显示）、过期时间、隐藏、编辑/删除
- 首页显示当前生效的公告，按发布时间倒序

## 1.0.6 修了什么

- 老安卓（2.3 / 4.0 / 4.1）点「查看回复」闪退：`Builder.setOnDismissListener` 是 API 17，改成 `AlertDialog.setOnDismissListener`
- 私信 JSON 分享卡片 → 显示 `[视频] 标题 + UP 主`，点击打开视频
- 私信里的 URL 可点：弹「应用内浏览器 / 系统浏览器 / 复制链接」
- 私信与消息支持长按复制
- 会话列表预览改为 `[视频] 标题`，不再显示 JSON
- 消息页翻页无限「加载中」：先 finishRefresh 再判 tab；去重后 0 新增即到底
- 下载链接改签名令牌，不再依赖 Cookie（老浏览器不再拿到 555B 错误页）
- GitHub Release 中文乱码修复（改用 UTF-8 API 写 body）

## 已知问题（1.0.6 遗留）

- 消息部分仍不够稳：点进评论偶尔失败 / 慢
- 通知是 15 分钟轮询，实时性差，点击跳转偶尔不准
- 2.3 设备上暂未完整复测（无 `input tap`，只能拍屏/日志）

## 服务器现状

- kangle + easypanel 共享主机；网站根 = FTP 的 `OldbiliRetro_website/`
- 数据：`/obr_data`（site/versions/changelog/announce/stats/admin，全部 JSON）
- 安装包：`/obr_files`（不在网站根，直链 404，只能走 `dl.php` 的 15 秒流程）
- 后台：`/oldbiliretro_admin_2026.php`，账号 `admin`（密码只放在用户本地记录，不写这里）
- HTTPS：Let's Encrypt 有效至 **2027-01-30**，`build-tools/acme_issue.py` 可重签
- 续期提醒：已用 schedule 记在 2027-01-23
- HTTP 未强制跳 HTTPS（老安卓 1.6/2.3 只支持 TLS 1.0，强跳会让它们打不开）

## 待办 / 下次可做

- [ ] 老设备实测 1.0.6（2.3 评论回复、私信卡片、长按复制）
- [ ] 通知实时性（后续考虑长连接或更短轮询）
- [ ] 官网 HTTPS 强制跳转按 UA 分流（需要 kangle 支持，暂时不做）
- [ ] 证书到期前重签（2027-01-23 提醒）

## 开发环境路径

- 项目根：`C:\Users\Administrator\Documents\deepseek-harness\default-workspace\OldBiliRetro`
- Gradle：`...\default-workspace\gradle\gradle-3.3\bin\gradle.bat`
- GRADLE_USER_HOME：`...\default-workspace\gradle-home`
- JDK：`C:\Program Files (x86)\Java\jre1.8.0_491`（JAVA_HOME），javac 用 `jdk1.7.0_80`
- 本机 PHP（测试网站用）：winget 装的 PHP 8.3，路径在 `%LOCALAPPDATA%\Microsoft\WinGet\Packages\PHP.PHP.8.3_*`
- 测试设备：Pixel 9 `47250DLAQ0GSGJ`、安卓 2.3 `0123456789ABCDEF`、4.1.2 `MSM8625Y321C`

## 发版检查清单（复制用）

1. `app/build.gradle` 改 versionCode/versionName
2. 写 `docs/RELEASE_NOTES_vX.md`
3. `make_changelog.py` 加 META → 运行
4. 构建 full + lite，只留两个 APK
5. `check_net.ps1` 全绿（否则提醒开 VPN TUN）
6. `git push`；Python 脚本建 Release（UTF-8 body）；`gh release upload`
7. FTP 传 APK 到 `/obr_files/`；更新 `/obr_data/versions.json`、`changelog.json`
8. 线上验证：下载字节数 + MD5、后台能登录、更新日志显示新版本
