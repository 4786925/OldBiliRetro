# 上传到 GitHub 步骤（含清理清单）

> 你的电脑已通过 TUN 模式连上 VPN，Chrome 已登录 GitHub —— 下面三种方式任选其一。
> **推荐方式 B（命令行）**，因为网页上传对文件数量有限制（单次 ≤ 100 个文件）。

---

## 第 0 步：上传前清理（必做，5 分钟）

### 0.1 绝不能上传的文件

| 文件 | 原因 | 处理 |
|---|---|---|
| `local.properties` | 内含**签名密码**与 SDK 路径 | 已在 `.gitignore` 中排除，确认没被跟踪 |
| `build-tools/oldbili.jks` | **签名私钥**，泄露 = 任何人可签你的包 | 已在 `.gitignore` 中排除 |
| `build/`、`app/build/` | 编译产物，几百 MB | 已排除 |
| `build-tools/*.log` | 构建日志（体积大且含本机路径） | 已排除 |
| `ref/`（在上级目录） | **其他项目的源码，与你项目无关** | 不要复制进仓库 |
| `.gradle/` | Gradle 缓存 | 已排除 |

### 0.2 确认清理结果

```bash
cd OldBiliRetro
git status --ignored --short | head -30   # 看是否有敏感文件被跟踪
```

如果发现 `local.properties` 或 `*.jks` 出现在待提交列表里，执行：

```bash
git rm --cached local.properties
git rm --cached build-tools/oldbili.jks
```

### 0.3 检查仓库体积

```bash
du -sh .                      # 期望 < 60MB（主要是 5 个 ABI 的 so，约 30MB）
```

---

## 方式 A：Chrome 网页上传（适合第一次、文件少）

1. 打开 <https://github.com/new>
2. **Repository name**：`OldBiliRetro`
3. **Description**：
   `老版哔哩 · 面向 Android 2.3~17 古董设备的第三方哔哩哔哩客户端（Holo Dark 风格，完全由 AI 开发）`
4. 选择 **Public**（开源）→ 勾选 **Add a license: GNU General Public License v3.0**
5. 点 **Create repository**
6. 在新页面点 **uploading an existing file**
7. 把项目里**除下列内容外**的文件/文件夹拖进去：
   - ❌ `local.properties`、`*.jks`、`build/`、`app/build/`、`.gradle/`、`build-tools/*.log`
8. Commit message 填 `Initial commit: OldBiliRetro 1.0.0 beta`
9. 点 **Commit changes**

> ⚠️ 网页上传**单次最多 100 个文件**。本项目 `res/` 下文件较多，建议用方式 B。

---

## 方式 B：命令行上传（推荐）

### B.1 安装 Git（若未安装）

```powershell
winget install --id Git.Git -e
```

### B.2 初始化并提交

```powershell
cd C:\Users\Administrator\Documents\deepseek-harness\default-workspace\OldBiliRetro

git init
git branch -M main

# 全局身份（只需一次）
git config --global user.name  "你的名字"
git config --global user.email "你的邮箱@example.com"

# 添加前先确认敏感文件已被忽略
git add .
git status --short | Select-String -Pattern "local.properties|\.jks"   # 应该没有输出

git commit -m "Initial commit: OldBiliRetro 1.0.0 beta (AI-developed)"
```

### B.3 关联远程仓库并推送

先在网页建一个**空仓库**（不要勾选 README/License，否则会冲突），然后：

```powershell
git remote add origin https://github.com/<你的用户名>/OldBiliRetro.git
git push -u origin main
```

推送时会要求登录：
- **推荐**：先在 Chrome 登录 GitHub，再执行 `git push`，Git Credential Manager 会弹窗让你用浏览器授权；
- 或者用 **Personal Access Token**（Settings → Developer settings → Personal access tokens → Tokens (classic) → 勾选 `repo`）作为密码；
- 或者装 GitHub CLI：`winget install --id GitHub.cli -e`，然后 `gh auth login` → `gh repo create OldBiliRetro --public --source=. --push`

### B.4 若推送被拒（文件过大）

```powershell
# 单个文件 > 100MB 会被拒绝；本项目 so 最大 8MB，正常不会触发
# 如果历史里有大文件，先检查：
git rev-list --objects --all | git cat-file --batch-check="%(objectsize) %(rest)" | Sort-Object -Descending | Select-Object -First 5
```

---

## 方式 C：GitHub Desktop（可视化）

1. 下载 <https://desktop.github.com/>
2. `File → Add local repository` → 选择项目目录
3. 左侧会列出改动；确认没有 `local.properties` / `*.jks`
4. 填 Summary → `Commit to main`
5. 点 **Publish repository** → 取消勾选 "Keep this code private"（开源）
6. Publish

---

## 第 2 步：仓库设置（上传后立刻做）

进入仓库 **Settings**：

1. **About（右上角齿轮）**
   - Description：`老版哔哩 · Android 2.3~17 古董设备第三方 B 站客户端（AI 开发）`
   - Website：留空或填你的页面
   - Topics：`android` `bilibili` `legacy-android` `holo` `ijkplayer` `danmaku` `retro` `ai-generated` `android-2-3`
2. **License**：Settings → 会识别根目录 `LICENSE`（GPL-3.0），确认显示为 `GPL-3.0 license`
3. **Issue 模板**（可选）：新建 `.github/ISSUE_TEMPLATE/bug_report.md`
4. **Security → 私密漏洞报告**：开启（可选）

---

## 第 3 步：发布 Release（放 APK）

```powershell
git tag -a v1.0.0-beta -m "OldBiliRetro 1.0.0 beta"
git push origin v1.0.0-beta
```

或在网页：**Releases → Draft a new release**
- Tag：`v1.0.0-beta`
- Title：`OldBiliRetro 1.0.0 beta`
- 说明可粘贴 README 的功能列表 + 兼容性表
- **上传附件**：`app/build/outputs/apk/app-release.apk`（改名 `OldBiliRetro-1.0.0-beta.apk`）
- ⚠️ **不要把 APK 提交进 git 仓库**，只放 Release 附件

---

## 第 4 步：上传后自检清单

- [ ] 仓库首页能正常渲染 README（中文不乱码）
- [ ] `LICENSE` 显示为 GPL-3.0
- [ ] **没有任何** `local.properties`、`*.jks`、`build/`、`ref/`
- [ ] `THIRD_PARTY.md`、`DISCLAIMER.md` 链接可点开
- [ ] `app/src/main/jniLibs/` 下 5 个 ABI 的 so 都在（约 30MB）
- [ ] Release 里有 APK 附件
- [ ] About 里的 Description / Topics 已填
- [ ] 在手机上点开仓库，确认 README 排版正常

---

## 常见问题

**Q：中文在 GitHub 上显示乱码？**
A：确认文件是 UTF-8 无 BOM。本仓库所有 `.md` 均为 UTF-8。

**Q：推送报 `failed to push some refs`？**
A：远程仓库建的时候勾了 README/License，先 `git pull --rebase origin main` 再 push。

**Q：能上传到 Gitee 吗？**
A：可以，`git remote add gitee https://gitee.com/<用户名>/OldBiliRetro.git && git push gitee main`。Gitee 需要实名认证。

**Q：需要 Actions 自动构建吗？**
A：老 AGP 2.3.2 + JDK7 在现代 Runner 上跑不起来，建议**不要**开启自动构建，手动发 Release 即可。
