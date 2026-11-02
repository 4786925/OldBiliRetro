<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');
obr_page_top('更新日志', 'changelog');

$list = $CHANGELOG;
usort($list, 'obr_cmp_log');

function obr_cmp_log($a, $b)
{
    $ca = intval(isset($a['code']) ? $a['code'] : 0);
    $cb = intval(isset($b['code']) ? $b['code'] : 0);
    if ($ca === $cb) {
        return strcmp($b['date'], $a['date']);
    }
    return ($ca < $cb) ? 1 : -1;
}
?>
<div class="box">
    <div class="box-title">更新日志 <span>与 GitHub Release 同步</span></div>
    <div class="box-body">
        <?php if (count($list) === 0): ?>
            <p>暂无更新日志。</p>
        <?php endif; ?>

        <?php foreach ($list as $e): ?>
            <div class="version-item">
                <span class="v-name"><?php echo h($e['version']); ?></span>
                <span class="v-date"><?php echo h($e['date']); ?>
                    <?php if (!empty($e['code'])): ?>· versionCode <?php echo intval($e['code']); ?><?php endif; ?>
                </span>
                <div class="v-body">
                    <?php if (!empty($e['items']) && is_array($e['items'])): ?>
                        <ul>
                            <?php foreach ($e['items'] as $it): ?>
                                <li><?php echo h($it); ?></li>
                            <?php endforeach; ?>
                        </ul>
                    <?php endif; ?>
                    <?php if (!empty($e['known']) && is_array($e['known'])): ?>
                        <p class="small" style="color:#a05000;"><b>已知问题：</b></p>
                        <ul class="small">
                            <?php foreach ($e['known'] as $k): ?>
                                <li><?php echo h($k); ?></li>
                            <?php endforeach; ?>
                        </ul>
                    <?php endif; ?>
                </div>
            </div>
        <?php endforeach; ?>

        <p class="small">
            完整发布说明与安装包见
            <a href="https://github.com/4786925/OldBiliRetro/releases" target="_blank">GitHub Releases</a>。
        </p>
    </div>
</div>
<?php
obr_page_bottom();
