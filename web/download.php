<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');

$go = isset($_GET['go']) ? intval($_GET['go']) : 0;
$id = isset($_GET['id']) ? intval($_GET['id']) : 0;

/* 取一个安装包 */
function obr_find_version($versions, $id)
{
    foreach ($versions as $v) {
        if (intval($v['id']) === intval($id)) {
            return $v;
        }
    }
    return null;
}

/* ---- 第二步：等待页 ---- */
if ($go === 1 && $id > 0) {
    $v = obr_find_version($VERSIONS, $id);
    if ($v === null || !empty($v['hidden']) || empty($v['file'])) {
        obr_page_top('下载', 'download');
        echo '<div class="box"><div class="box-title">下载</div><div class="box-body">';
        echo '<p>安装包不存在或已下架。</p><p><a href="download.php">&lt;&lt; 返回下载页</a></p>';
        echo '</div></div>';
        obr_page_bottom();
        exit;
    }

    if (!obr_rate_limit('dl_page', 30, 3600)) {
        obr_page_top('下载', 'download');
        echo '<div class="box"><div class="box-title">下载</div><div class="box-body">';
        echo '<p>请求过于频繁，请稍后再试。</p>';
        echo '</div></div>';
        obr_page_bottom();
        exit;
    }

    $token = obr_make_token(intval($v['id']));
    $dlUrl = 'dl.php?t=' . $token;
    $wait = 15;

    obr_page_top('下载 ' . $v['version'], 'download',
            '<meta http-equiv="refresh" content="17;url=' . h($dlUrl) . '" />');
    ?>
    <div class="box">
        <div class="box-title">正在准备下载</div>
        <div class="box-body">
            <div class="wait-box">
                <h2 id="wait_state">下载将在 <span id="wait_num">15</span> 秒后自动开始</h2>
                <div class="wait-num" id="wait_num_big">15</div>
                <div class="wait-tip">
                    文件：<b><?php echo h($v['version']); ?> ·
                    <?php echo $v['abi'] === 'armeabi-v7a' ? 'V7 精简版' : '全架构版'; ?></b><br />
                    大小：<?php echo obr_fmt_size(intval($v['size'])); ?><br />
                    等待是为了防止恶意刷流量，给你 15 秒确认。
                </div>
                <div class="wait-bar"><div id="wait_bar_inner"></div></div>
                <p style="margin-top:14px;">
                    <a class="btn" id="wait_go" style="display:none"
                       href="<?php echo h($dlUrl); ?>">立即下载</a>
                </p>
                <p class="small">
                    若浏览器不支持自动跳转，请点击
                    <a href="<?php echo h($dlUrl); ?>">这里下载</a>（15 秒后有效）。
                </p>
            </div>
            <p><a href="download.php">&lt;&lt; 返回下载列表</a></p>
        </div>
    </div>
    <noscript>
        <meta http-equiv="refresh" content="16;url=<?php echo h($dlUrl); ?>" />
        <div class="box"><div class="box-body">
            你的浏览器没有启用脚本，请 16 秒后点击
            <a href="<?php echo h($dlUrl); ?>">此处下载</a>。
        </div></div>
    </noscript>
    <script type="text/javascript">
    obrStartCountdown(<?php echo intval($wait); ?>, '<?php echo h($dlUrl); ?>');
    </script>
    <?php
    obr_page_bottom();
    exit;
}

/* ---- 第一步：列表 ---- */
$list = array();
foreach ($VERSIONS as $v) {
    if (!empty($v['hidden']) || empty($v['file'])) {
        continue;
    }
    $list[] = $v;
}
usort($list, 'obr_cmp_version');

function obr_cmp_version($a, $b)
{
    $ca = intval(isset($a['code']) ? $a['code'] : 0);
    $cb = intval(isset($b['code']) ? $b['code'] : 0);
    if ($ca === $cb) {
        return strcmp($b['date'], $a['date']);
    }
    return ($ca < $cb) ? 1 : -1;
}

obr_page_top('下载', 'download');
?>
<div class="box">
    <div class="box-title">下载 OldBiliRetro <span>请选择适合你设备的安装包</span></div>
    <div class="box-body">
        <div class="notice">
            <b>安装提示：</b>
            安卓 2.x 老设备请选 <b>V7 精简版</b>；存储紧张时先卸载旧版再安装。
            如果提示「解析包错误」，说明下载不完整，请重新下载。
        </div>

        <?php if (count($list) === 0): ?>
            <p>暂无可下载的安装包，请前往
               <a href="https://github.com/4786925/OldBiliRetro/releases" target="_blank">GitHub Releases</a>。
            </p>
        <?php endif; ?>

        <?php foreach ($list as $v): ?>
            <div class="dl-file">
                <div class="f-name">
                    <?php echo h($v['version']); ?>
                    · <?php echo $v['abi'] === 'armeabi-v7a' ? 'V7 精简版' : '全架构版'; ?>
                    <?php if (!empty($v['latest'])): ?>
                        <span style="color:#d35400;font-size:11px;">[最新]</span>
                    <?php endif; ?>
                </div>
                <div class="small">
                    发布日期：<?php echo h($v['date']); ?> ·
                    大小：<?php echo obr_fmt_size(intval($v['size'])); ?> ·
                    ABI：<?php echo h($v['abi']); ?> ·
                    下载次数：<?php echo intval(isset($v['hits']) ? $v['hits'] : 0); ?>
                </div>
                <?php if (!empty($v['notes'])): ?>
                    <div style="margin-top:4px;"><?php echo nl2br(h($v['notes'])); ?></div>
                <?php endif; ?>
                <p style="margin:6px 0 0;">
                    <a class="btn" href="download.php?go=1&amp;id=<?php echo intval($v['id']); ?>">下载此版本</a>
                </p>
            </div>
        <?php endforeach; ?>

        <p class="small">
            下载前会显示 15 秒等待提示，这是为了防止恶意批量请求。
            本站不保存任何用户数据，安装包与 GitHub Release 中的完全一致。
        </p>
    </div>
</div>

<div class="box">
    <div class="box-title">其他平台的说明</div>
    <div class="box-body">
        <p>
            本站页面可用于 Android 1.6 及以上、Windows Mobile / WinCE 6.0（IE Mobile）、
            塞班等老浏览器访问与下载，页面不使用现代脚本框架。
        </p>
        <p>
            OldBiliRetro 本身是 Android 应用；WinCE / 塞班等设备可以正常浏览本站，
            但无法安装 APK，请在有 Android 设备时再下载。
        </p>
    </div>
</div>
<?php
obr_page_bottom();
