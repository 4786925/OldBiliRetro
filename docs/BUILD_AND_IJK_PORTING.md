# OldBiliRetro 打包与 IJK 移植流程（内部执行手册）

> 这份文档记录“由我（AI）执行”的完整流程，不是让你手动操作。
> 涉及密码/密钥的内容不写在这里。

## 一、构建环境（固定）

| 项 | 值 |
|---|---|
| 项目根 | `C:\Users\Administrator\Documents\deepseek-harness\default-workspace\OldBiliRetro` |
| JDK（运行 Gradle） | `C:\Program Files (x86)\Java\jre1.8.0_491` |
| Gradle 缓存 | `...\default-workspace\gradle-home` |
| Gradle 3.3 | `...\default-workspace\gradle\gradle-3.3\bin\gradle.bat` |
| 编译 SDK / BuildTools | 23 / 25.0.3 |
| 源码兼容 | Java 7 |
| minSdk / targetSdk | 6 / 26 |
| 签名 | `local.properties` 里的 KEY_PATH 等（release 配置读取） |
| 混淆 | release：`minifyEnabled true` + `shrinkResources true` + proguard |

Gradle 启动命令模板（PowerShell）：

```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Java\jre1.8.0_491"
$env:GRADLE_USER_HOME = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\gradle-home"
$g = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\gradle\gradle-3.3\bin\gradle.bat"
$p = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\OldBiliRetro"
```

## 二、出包流程（每次发版）

### 1. 清空产物目录

只保留两个包，避免混入 `app-release.apk`：

```powershell
Get-ChildItem "$p\app\build\outputs\apk" -Filter *.apk | Remove-Item -Force
```

### 2. 全架构包（full）

```powershell
cmd /c "`"$g`" -p `"$p`" assembleRelease > `"$p\build-tools\build-full.log`" 2>&1"
```

- 产物：`app\build\outputs\apk\app-release.apk`
- 重命名：`OldBiliRetro-<版本>-full.apk`

### 3. V7 精简包（lite）

```powershell
cmd /c "`"$g`" -p `"$p`" assembleRelease -Pabi=armeabi-v7a > `"$p\build-tools\build-lite.log`" 2>&1"
```

- `-Pabi` 会把 `abiFilters` 改成单一 ABI，体积约 3MB
- 重命名：`OldBiliRetro-<版本>-armv7-lite.apk`
- 同样删掉临时 `app-release.apk`

### 4. 看编译错误

Gradle 输出是 GBK，读日志要按 936 解码：

```powershell
$gbk = [System.Text.Encoding]::GetEncoding(936)
$txt = [System.IO.File]::ReadAllText("$p\build-tools\build-full.log", $gbk)
$txt -split "`r?`n" | Select-String "\.java:\d+:" | Select-Object -First 10
```

### 5. 产物自检

- 目录里**只有 2 个 APK**
- 记录文件大小 + MD5
- 解包确认 ABI：`unzip -l` 或 `System.IO.Compression.ZipFile` 看 `lib/<abi>/*.so`
- 确认 `versionCode/versionName` 与预期一致

### 6. 装机验证（按设备能力）

| 设备 | 连接方式 | 限制 |
|---|---|---|
| Pixel 9 | 无线调试：`adb-47250DLAQ0GSGJ-pXz4dA._adb-tls-connect._tcp` | 正常，可 screencap/input/uiautomator |
| Milestone 2.2.1 | `0403A2AE0300B013` | 无 screencap（permission denied）、无 `am force-stop`、shell 不能读 /sdcard；有 `input keyevent` |
| 安卓 2.1（EG-E600） | `1234567890ABCDEF`（USB 常掉线） | 同上，无 `input` 命令 |
| 安卓 2.3 模拟器 | `0123456789ABCDEF` | 无 uiautomator、无 input tap；screencap 可用 |

常用命令：

```powershell
$adb = "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb -s <serial> install -r "<apk>"
& $adb -s <serial> shell am start -n com.android.oldbili.retro/.MainActivity
& $adb -s <serial> shell logcat -d | Select-String "FATAL|AndroidRuntime|oldbili"
```

老设备无法触摸点击时，用调试入口（如 `DevDownloadActivity`）直接触发功能，
或在代码里加临时日志，用 `adb logcat` 判断。

### 7. 发版前检查

```powershell
powershell -ExecutionPolicy Bypass -File "$p\build-tools\check_net.ps1"
```

- GitHub 必须全绿（api.github.com / github.com）
- 失败通常是没有开 VPN 的 TUN 模式（只开系统代理不算）
- 同时提醒用户：老设备下载请用 HTTP 官网地址

### 8. 版本号与更新日志

1. `app/build.gradle`：`versionCode +1`、`versionName`
2. `docs/RELEASE_NOTES_v<版本>.md`（含 `### 更新内容`、`## 已知问题`）
3. `build-tools/make_changelog.py` 的 META 加新版本 → 生成 `app/src/main/assets/changelog.json`
4. 官网 `/obr_data/changelog.json` 同步（结构 `{app, entries}`，UTF-8 无 BOM）

> 版本号变更和 GitHub 发布**必须**先得到用户明确同意。

### 9. GitHub 发布

```powershell
git -C $p add -A
git -C $p commit -m "..."
git -C $p push origin main
```

Release 说明**不能用** `gh release create --notes-file`（Windows 会按 ANSI 读，中文乱码）。
用 Python + `api.github.com` + UTF-8 JSON（参见 `build-tools/release_106.py`）：

```python
requests.post("https://api.github.com/repos/4786925/OldBiliRetro/releases",
              headers={"Authorization": "Bearer " + token},
              data=json.dumps({"tag_name": tag, "name": name,
                               "body": body, "prerelease": True}).encode("utf-8"))
```

上传 APK：

```powershell
gh release upload <tag> <full> <lite> --repo 4786925/OldBiliRetro --clobber
```

### 10. 官网同步

- APK → FTP `/obr_files/`
- 版本表 `/obr_data/versions.json`（size/date/abi）
- 更新日志 `/obr_data/changelog.json`
- 下载页/公告如需更新：`web/*.php` 上传到网站根 `OldbiliRetro_website/`
- **easypanel 目录绑定不能带前导 `/`**（写成 `OldbiliRetro_website`）
- kangle 不认 `.htaccess`，敏感数据必须放在 docroot 之外（`/obr_data`、`/obr_files`）
- 验证：从官网真实下载一次，比对 MD5

## 三、IJK 移植流程（WSL2）

### 现状（已具备）

| 项 | 路径 |
|---|---|
| WSL2 | Ubuntu-22.04（`wsl -e bash`） |
| IJK 源码 | `/root/ijk/ijkplayer-master` |
| NDK | `/root/ijk/android-ndk-r16b`（r16b） |
| ffmpeg 预编译 | `android/contrib/build/ffmpeg-{armv5,armv7a,arm64,x86,x86_64}/output` |
| 各 ABI 构建目录 | `android/ijkplayer/ijkplayer-<abi>/src/main/jni` |
| APP_PLATFORM | `android-9` |
| Java 包装层 | 项目内 vendor：`app/src/main/java/tv/danmaku/ijk/...`（可直接改） |

### 目标

让 IJK 在 Android 2.1（API 7）/ 2.2（API 8）上能加载并播放，
2.3+ 保持现有行为不变。

### 已完成的改动

1. `ijkmedia/ijksdl/Android.mk`
   - 删掉 `-lOpenSLES`
   - 删掉 `android/ijksdl_aout_android_opensles.c`
   - 加 `android/ijksdl_aout_android_opensles_stub.c`（返回 NULL / false 的空实现）
2. 重新编译后 `readelf -d libijksdl.so` 确认 `NEEDED` 里已无 `libOpenSLES.so`
3. Java 侧（`PlayerActivity`）
   - `SDK<16` 强制 `mediacodec=0`（MediaCodec 是 API 16 才有的）
   - `opensles=0`（固定走 AudioTrack）
   - 待办：`requestAudioFocus/abandonAudioFocus`（API 8）在 2.1 上要跳过

### 编译命令（每个 ABI）

```bash
cd /root/ijk/ijkplayer-master/android/ijkplayer/ijkplayer-armv7a/src/main/jni
/root/ijk/android-ndk-r16b/ndk-build APP_ABI=armeabi-v7a -j4
```

产物：`.../src/main/libs/<abi>/{libijksdl.so,libijkplayer.so,libijkffmpeg.so}`

### 拷回项目

```bash
cp .../libs/armeabi-v7a/libijksdl.so   /mnt/c/.../OldBiliRetro/app/src/main/jniLibs/armeabi-v7a/
cp .../libs/armeabi-v7a/libijkplayer.so /mnt/c/.../OldBiliRetro/app/src/main/jniLibs/armeabi-v7a/
```

（`libijkffmpeg.so` 未改动可复用；其它 ABI 同理。）

### 当前阻塞（在 2.2 实测）

```
Unable to dlopen(libijksdl.so): link_image[1995]: failed to link libijksdl.so
/system/lib/libandroid.so: No such file or directory
```

原因：`libijksdl.so` 依赖 `libandroid.so` 的 **ANativeWindow** 接口，
该 NDK API 是 **Android 2.3（API 9）** 才加入的，2.1/2.2 系统没有这个库。
IJK 的视频输出层 `ijksdl_vout_android_nativewindow.c` 全依赖它。

### 继续移植的三条路线

**路线 A：自己实现 ANativeWindow（native，工作量大）**

- 用 2.x 私有 `libui` / `libsurfaceflinger_client` 从 Java `Surface` 构造原生窗口
- 需要声明并链接私有符号，不同 2.x ROM 符号可能不同
- 预计 3~5 天，兼容性风险高

**路线 B：软渲染（native + Java 配合）**

- IJK 解码出 RGBA 帧 → 通过 JNI 交给 Java → `Bitmap` + `Surface.lockCanvas` 绘制
- 不需要 ANativeWindow，但 CPU 占用高，360P 可能勉强
- 预计 2~4 天，画质/帧率受限

**路线 C：2.3 以下不用 IJK**

- C1：我们自己的 MediaPlayer 引擎 + 现有 UI/弹幕（1~2 天，已验证可播）
- C2：外部播放器（已实现并验证）

### 移植的验证闭环

1. `ndk-build` → 拷 `.so` → `assembleRelease`
2. 安装到 2.1 / 2.2
3. `adb logcat` 看：
   - `Trying to load lib .../libijksdl.so`
   - 是否还有 `link_image` / `No such file`
   - `IjkMediaPlayer` 初始化、`onPrepared`、`first frame`
4. 检查画面（老设备不能截图时，用日志 + 用户目测）
5. 2.3 模拟器 / 4.x 设备回归，确认没破坏原有播放

## 四、调试入口（老设备无法触摸时）

`DevDownloadActivity`（导出）：

```powershell
adb shell am start -n com.android.oldbili.retro/.DevDownloadActivity --es bvid BV1GJ411x7h7 --ei qn 16
```

- 直接入队下载，用于验证下载链路
- 发布前可删除该 Activity 或改为 `exported=false`

## 五、注意事项

1. 老设备 adb 能力有限（见上表），不要假设 `input tap`/`screencap` 可用。
2. 2.x 系统 TLS 很旧：API 请求要强制 IPv4（`java.net.preferIPv4Stack`，仅 <9），
   媒体/下载优先 http 直链；https 由 `TlsSocketFactoryCompat` 兜底。
3. 老设备 `/sdcard` 可能因 USB 大容量存储不可写：下载必须能回退到应用私有目录。
4. 原生库改动后必须同时回归 2.3+，避免“修好 2.x 弄坏新设备”。
5. 每次发布：两个 APK、MD5、Release 说明 UTF-8、官网 MD5 校验，缺一不可。
