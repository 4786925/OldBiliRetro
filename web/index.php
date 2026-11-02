<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');

/* 最新更新日志 */
$latest_log = null;
if (!empty($CHANGELOG)) {
    $latest_log = $CHANGELOG[0];
}

obr_page_top('', 'index');
?>

<div class="notice">
    <b>公告：</b>
    <?php
    if (is_array($ANNOUNCE) && !empty($ANNOUNCE['text'])) {
        echo nl2br(h($ANNOUNCE['text']));
    } else {
        echo nl2br(h($SITE['announce']));
    }
    ?>
</div>

<div class="box">
    <div class="box-title">关于 OldBiliRetro <span>一个给古董手机用的哔哩哔哩客户端</span></div>
    <div class="box-body">
        <p><?php echo nl2br(h($SITE['intro'])); ?></p>
        <p>
            兼容 <b>Android 1.6 ~ Android 17</b>（推荐 2.3 及以上）；
            同时提供 <b>全架构包</b> 与 <b>V7 精简包</b>，
            后者只有约 3MB，适合 64MB / 96MB 内存、存储紧张的老机器。
        </p>
        <p class="small">
            界面还原 2013-2014 年 Holo Dark 风格：深色列表、蓝色高亮、小字号，没有现代设计。
        </p>
        <p><a class="btn" href="download.php">立即下载最新版 &gt;&gt;</a>
           <a class="btn btn-gray" href="about.php">查看声明</a></p>
    </div>
</div>

<div class="box">
    <div class="box-title">主要特性</div>
    <div class="box-body">
        <table width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
            <td width="50%" valign="top">
                <ul>
                    <li>登录：扫码 / Cookie 导入 / 导出</li>
                    <li>首页：推荐 / 热门 / 番剧 / 影视 / 直播</li>
                    <li>搜索：视频 / 番剧 / 影视 / 直播 / 用户</li>
                    <li>播放器：IJKPlayer 自编译，横屏播放</li>
                    <li>倍速：0.75x ~ 2.0x，长按 2 倍速</li>
                </ul>
            </td>
            <td width="50%" valign="top">
                <ul>
                    <li>弹幕：DanmakuFlameMaster 完整版</li>
                    <li>评论：楼中楼 / 点赞 / 图文评论 / 等级显示</li>
                    <li>下载：分 P / 断点续传 / 按 UP 主建文件夹</li>
                    <li>消息：私信 / 回复 / @我，支持通知提醒</li>
                    <li>设置：无图模式、缓存清理、深色界面</li>
                </ul>
            </td>
        </tr></table>
    </div>
</div>

<?php if (is_array($latest_log)): ?>
<div class="box">
    <div class="box-title">最新更新 <span><?php echo h($latest_log['version']); ?> · <?php echo h($latest_log['date']); ?></span></div>
    <div class="box-body">
        <ul>
            <?php
            $items = isset($latest_log['items']) ? $latest_log['items'] : array();
            $n = 0;
            foreach ($items as $it) {
                if ($n >= 10) {
                    break;
                }
                echo '<li>' . h($it) . '</li>';
                $n++;
            }
            ?>
        </ul>
        <p><a href="changelog.php">查看完整更新日志 &gt;&gt;</a></p>
    </div>
</div>
<?php endif; ?>

<div class="box">
    <div class="box-title">下载</div>
    <div class="box-body">
        <table class="list">
            <tr>
                <th width="30%">安装包</th>
                <th width="15%">适用设备</th>
                <th width="20%">大小</th>
                <th width="20%">版本</th>
                <th width="15%">下载</th>
            </tr>
            <?php if (is_array($LATEST_FULL)): ?>
            <tr>
                <td>全架构版</td>
                <td>现代手机 / 模拟器</td>
                <td><?php echo obr_fmt_size(intval($LATEST_FULL['size'])); ?></td>
                <td><?php echo h($LATEST_FULL['version']); ?></td>
                <td><a href="download.php?go=1&amp;id=<?php echo intval($LATEST_FULL['id']); ?>">下载</a></td>
            </tr>
            <?php endif; ?>
            <?php if (is_array($LATEST_LITE)): ?>
            <tr class="odd">
                <td>V7 精简版</td>
                <td>Android 2.x / 小存储</td>
                <td><?php echo obr_fmt_size(intval($LATEST_LITE['size'])); ?></td>
                <td><?php echo h($LATEST_LITE['version']); ?></td>
                <td><a href="download.php?go=1&amp;id=<?php echo intval($LATEST_LITE['id']); ?>">下载</a></td>
            </tr>
            <?php endif; ?>
            <?php if (!is_array($LATEST_FULL)): ?>
            <tr><td colspan="5">暂无安装包，请到 GitHub Release 下载。</td></tr>
            <?php endif; ?>
        </table>
        <p class="small">下载页有 15 秒等待提示，用于抵御恶意刷流量；历史版本可在下载页查看。</p>
    </div>
</div>

<?php obr_page_bottom(); ?>
