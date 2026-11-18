<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';
obr_bump_stat('pv');
obr_page_top('播放器下载（老设备）', 'player');
?>
<div class="box">
    <div class="box-title">为什么需要外部播放器</div>
    <div class="box-body">
        <p>OldBiliRetro 内置播放器需要 <b>Android 2.3 及以上</b>。</p>
        <p>Android 2.1 / 2.2 的老设备没有内置解码器，需要先安装一个能播放 MP4 的外部播放器，
            装好后再回到 OldBiliRetro 点播放即可。</p>
        <p class="small">安装步骤：下载 → 安装 → 回到 OldBiliRetro → 打开任意视频 → 系统会让你选择播放器。</p>
    </div>
</div>

<div class="box">
    <div class="box-title">推荐的播放器</div>
    <div class="box-body">
        <div class="version-item">
            <span class="v-name">VLC for Android（旧版）</span>
            <div class="v-body">
                开源（GPL），老版本支持 Android 2.1+，MP4/H.264 兼容性好。<br />
                <a class="btn btn-gray" href="https://download.videolan.org/pub/videolan/vlc-android/" target="_blank">官方旧版下载页</a>
            </div>
        </div>
        <div class="version-item">
            <span class="v-name">MX Player（旧版）</span>
            <div class="v-body">
                闭源免费，1.7.x 支持 Android 2.1+。官网只提供新版，旧版可在 APKMirror 等存档站搜索。<br />
                <a class="btn btn-gray" href="https://www.mxplayer.in/" target="_blank">官方网站</a>
            </div>
        </div>
        <div class="version-item">
            <span class="v-name">RockPlayer</span>
            <div class="v-body">
                老牌播放器，ARMv5/ARMv6 设备兼容性好；闭源，需自行寻找安装包。<br />
                <span class="small">（本站不托管闭源安装包）</span>
            </div>
        </div>
        <div class="version-item">
            <span class="v-name">SeeJoPlayer</span>
            <div class="v-body">
                基于 ffmpeg 的老开源播放器，源码在 GitHub，可自行编译。
                <a href="https://github.com/srclib/SeeJoPlayer" target="_blank">项目地址</a>
            </div>
        </div>
    </div>
</div>

<div class="box">
    <div class="box-title">常见问题</div>
    <div class="box-body">
        <p><b>装了播放器还是提示没有可用播放器？</b><br />
            可能是播放器没有注册 MP4 类型，换一个（推荐 VLC 旧版）再试。</p>
        <p><b>播放器打不开视频？</b><br />
            部分视频只有 DASH 流，老播放器不支持。可以换一个视频，或在 OldBiliRetro 里重试。</p>
        <p><b>为什么官网不直接提供 APK？</b><br />
            闭源播放器的再分发涉及版权，我们只托管开源软件的安装包；闭源的只给官方或存档链接。</p>
    </div>
</div>
<?php
obr_page_bottom();
