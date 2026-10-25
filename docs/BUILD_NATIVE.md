# 原生播放器库编译说明（可选 / 进阶）

> 普通用户不需要看这份文档——仓库里 `app/src/main/jniLibs/` 已包含 5 个 ABI 编译好的 `.so`。
> 本文给想自己复现编译、或需要替换 FFmpeg 的人参考。

---

## 1. 目标产物

每个 ABI 三个文件：

```
libijkffmpeg.so   # FFmpeg 4.0 + OpenSSL 1.0.2u（静态链入）
libijkplayer.so   # IJKPlayer 核心（含 ijkio/ijkavformat 补丁）
libijksdl.so      # 音视频输出（AudioTrack + Surface/Overlay）
```

---

## 2. 环境

| 项目 | 版本 | 说明 |
|---|---|---|
| 系统 | Ubuntu 22.04（**WSL2** 可用） | Windows 原生编译 ijkplayer 脚本不可用（依赖 bash 工具链） |
| NDK | **r14b**（armeabi / armeabi-v7a / x86，API 9）<br>**r16b**（arm64-v8a / x86_64，API 21） | API 9 必须有 `platforms/android-9`，只有 r14b 及更早提供 |
| 工具 | `make`、`perl`、`makedepend`（xutils-dev）、`yasm` | `apt install -y make perl xutils-dev yasm` |

---

## 3. 源码

| 源码 | 版本 / 分支 |
|---|---|
| ijkplayer | `master`（或 0.8.8 tag） |
| FFmpeg（ijk fork） | commit `ff4.0--ijk0.8.8--20210426--001`<br>`https://github.com/Bilibili/FFmpeg` |
| OpenSSL | **1.0.2u**（`OpenSSL_1_0_2u` tag） |
| SoundTouch | `ijk-r0.1.2-dev`（`Bilibili/soundtouch`） |
| libyuv | `ijk-r0.2.1-dev`（`Bilibili/libyuv`） |

放置位置：

```
ijkplayer-master/
├── extra/
│   ├── openssl/     ← 1.0.2u
│   └── ffmpeg/      ← ijk fork
├── android/contrib/openssl-<arch>/   ← 每次编译前从 extra/openssl 复制
├── android/contrib/ffmpeg-<arch>/    ← 每次编译前从 extra/ffmpeg 复制
└── android/ijkplayer/ijkplayer-<arch>/src/main/jni/ijkmedia/{ijksoundtouch,ijkyuv}/  ← 复制进来
```

---

## 4. 必须修改的脚本（否则 100% 失败）

### 4.1 跳过 `make-standalone-toolchain`

`make-standalone-toolchain.sh` 在 r16b 上本身有 bug、在 r14b 上会拒绝已存在的目录，直接绕过：

```bash
# android/contrib/tools/do-compile-ffmpeg.sh 与 do-compile-openssl.sh
# 1) 工具链路径指向 NDK 内置 GCC 的上一级（注意：不是 /bin）
sed -i 's|FF_TOOLCHAIN_PATH=.*toolchain|FF_TOOLCHAIN_PATH=$ANDROID_NDK/toolchains/$FF_TOOLCHAIN_NAME/prebuilt/linux-x86_64|' do-compile-*.sh
# 2) 跳过 toolchain 创建分支
sed -i 's|^if [ ! -f "\$FF_TOOLCHAIN_TOUCH" ]; then|if false; then|' do-compile-*.sh
# 3) NDK 版本判断（原脚本只认 11~14）
sed -i 's|11*|12*|13*|14*)|1[1-9]*|2[0-9]*)|' tools/do-detect-env.sh
```

### 4.2 sysroot 必须指向「平台架构目录」

```
--sysroot=$ANDROID_NDK/platforms/android-<API>/arch-<ARCH>
```

不能指向 `$NDK/sysroot`（那里只有头文件，没有 `crtbegin_dynamic.o` / `libc.so`）。

### 4.3 openssl 只编静态库 + 头文件解引用复制

```bash
sed -i 's|^make \$FF_MAKE_FLAGS$|make $FF_MAKE_FLAGS build_libs|' do-compile-openssl.sh
# 编译完成后手动安装（install_sw 会去装命令行工具而失败）
cp openssl-<arch>/libcrypto.a openssl-<arch>/libssl.a build/openssl-<arch>/output/lib/
cp -rL openssl-<arch>/include build/openssl-<arch>/output/include   # -L 必须，否则软链断裂
```

---

## 5. 编译顺序（每个 ABI 重复一遍）

```bash
export ANDROID_NDK=/path/to/ndk
export APP_ALLOW_MISSING_DEPS=true

cd android/contrib
sh compile-openssl.sh <arch>     # armv5 / armv7a / arm64 / x86 / x86_64
sh compile-ffmpeg.sh  <arch>
cd ..
sh compile-ijk.sh <arch>
```

产物：

```
android/ijkplayer/ijkplayer-<arch>/src/main/libs/<abi>/libijk*.so
```

| arch | abi 目录 | API |
|---|---|---|
| armv5 | `armeabi` | 9 |
| armv7a | `armeabi-v7a` | 9 |
| arm64 | `arm64-v8a` | 21 |
| x86 | `x86` | 9 |
| x86_64 | `x86_64` | 21 |

---

## 6. 验证

```bash
# 必须能看到 openssl 符号（否则 https 播放会失败）
strings libijkffmpeg.so | grep -c SSL_CTX_new     # > 0

# 架构
readelf -h libijkffmpeg.so | grep Machine

# 体积参考（未 strip / strip 后）
# armeabi      5.8MB / 4.3MB
# armeabi-v7a  4.6MB / 3.4MB
# arm64-v8a    5.7MB / 4.1MB
# x86          6.4MB / 4.8MB
# x86_64       8.3MB / 5.9MB
```

---

## 7. 踩坑记录（照抄可避开）

| 现象 | 原因 | 解决 |
|---|---|---|
| `Failed to create toolchain` | standalone toolchain 在 r14b/r16b 上不可用 | 见 4.1，直接跳过 |
| `cannot find crtbegin_dynamic.o` / `-lc` | sysroot 指向了 `$NDK/sysroot` | 改为 `platforms/android-<API>/arch-<ARCH>` |
| `asm/types.h: No such file` | unified sysroot 不含架构子目录 | 加 `-I$NDK/sysroot/usr/include/<cross-prefix>` |
| `makedepend: Permission denied` | openssl 1.0.2 需要 makedepend | `apt install xutils-dev` |
| `libncurses.so.5: cannot open` | NDK 的 `nm` 依赖旧 ncurses | `ln -sf libncurses.so.6 libncurses.so.5` 并 `ldconfig` |
| `nasm/yasm not found`（x86_64） | ffmpeg 需要汇编器 | `apt install yasm` |
| `openssl/ssl.h: No such file` | 头文件是相对软链，普通 `cp -r` 后断裂 | 必须 `cp -rL` |
| `undefined modules: ijksoundtouch` | 缺少 soundtouch jni 模块 | 把 `Bilibili/soundtouch` 复制到 `jni/ijkmedia/ijksoundtouch` |
| `libyuv.h: No such file` | 缺少 libyuv | 复制到 `jni/ijkmedia/ijkyuv` |
| 音频无声 / 倍速无效 | 未启用 soundtouch | 确认 `ijkmedia/ijksoundtouch` 存在且 `APP_ALLOW_MISSING_DEPS` 未吞掉模块 |

---

## 8. 许可提醒

- FFmpeg 以 `--disable-gpl` 编译 → **LGPL-2.1+**，静态链接进 `libijkffmpeg.so`。
  按 LGPL 要求，本项目公开**全部源码与上述编译脚本**，允许任何人重新编译并替换该库。
- OpenSSL 1.0.2 采用 OpenSSL/SSLeay 许可（BSD 风格），与本项目 GPL-3.0 兼容。
- 如果你修改了 FFmpeg 源码并分发，请遵守 LGPL 的源码公开义务。
