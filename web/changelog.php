<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');

/* 最新在前：按 versionCode 倒序，code 相同再按日期倒序 */
$list = $CHANGELOG;
usort($list, 'obr_cmp_log');

function obr_cmp_log($a, $b)
{
    $ca = intval(isset($a['code']) ? $a['code'] : 0);
    $cb = intval(isset($b['code']) ? $b['code'] : 0);
    if ($ca === $cb) {
        return strcmp(isset($b['date']) ? $b['date'] : '',
                      isset($a['date']) ? $a['date'] : '');
    }
    return ($ca < $cb) ? 1 : -1;
}

/* 每页条数：10（默认）/ 20 / 40 */
$per = isset($_GET['per']) ? intval($_GET['per']) : 10;
if ($per !== 20 && $per !== 40) {
    $per = 10;
}
$total = count($list);
$pages = $total > 0 ? (int) ceil($total / $per) : 1;
$page = isset($_GET['page']) ? intval($_GET['page']) : 1;
if ($page < 1) {
    $page = 1;
}
if ($page > $pages) {
    $page = $pages;
}
$slice = array_slice($list, ($page - 1) * $per, $per);

obr_page_top('更新日志', 'changelog');
?>
<div class="box">
    <div class="box-title">更新日志
        <span>共 <?php echo intval($total); ?> 条 · 第 <?php echo intval($page); ?>/<?php echo intval($pages); ?> 页</span>
    </div>
    <div class="box-body">
        <p class="small" style="margin-top:0;">
            每页条数：
            <?php foreach (array(10, 20, 40) as $opt): ?>
                <?php if ($opt === $per): ?>
                    <b>[<?php echo $opt; ?>]</b>
                <?php else: ?>
                    <a href="changelog.php?per=<?php echo $opt; ?>&amp;page=1"><?php echo $opt; ?></a>
                <?php endif; ?>
            <?php endforeach; ?>
        </p>

        <?php if (count($slice) === 0): ?>
            <p>暂无更新日志。</p>
        <?php endif; ?>

        <?php foreach ($slice as $e): ?>
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

        <?php if ($pages > 1): ?>
            <p style="margin-top:14px;">
                <?php if ($page > 1): ?>
                    <a class="btn btn-gray" href="changelog.php?per=<?php echo $per; ?>&amp;page=<?php echo $page - 1; ?>">&lt;&lt; 上一页</a>
                <?php endif; ?>
                &nbsp; 第 <?php echo intval($page); ?> / <?php echo intval($pages); ?> 页 &nbsp;
                <?php if ($page < $pages): ?>
                    <a class="btn btn-gray" href="changelog.php?per=<?php echo $per; ?>&amp;page=<?php echo $page + 1; ?>">下一页 &gt;&gt;</a>
                <?php endif; ?>
            </p>
        <?php endif; ?>

        <p class="small">
            完整发布说明与安装包见
            <a href="https://github.com/4786925/OldBiliRetro/releases" target="_blank">GitHub Releases</a>。
        </p>
    </div>
</div>
<?php
obr_page_bottom();
