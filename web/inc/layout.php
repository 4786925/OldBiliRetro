<?php
/**
 * 页面框架：2010 年代风格的表格布局（兼容 Android 1.6 / WinCE 浏览器）
 */

function obr_nav_items()
{
    return array(
        'index'     => array('首页', 'index.php'),
        'download'  => array('下载', 'download.php'),
        'changelog' => array('更新日志', 'changelog.php'),
        'faq'       => array('常见问题', 'faq.php'),
        'about'     => array('关于与声明', 'about.php'),
    );
}

function obr_page_top($title, $active, $extraHead = '')
{
    $site = $GLOBALS['SITE'];
    $full = $title === '' ? $site['title'] : $title . ' - ' . $site['title'];
    echo '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" ' .
         '"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">' . "\n";
    echo '<html xmlns="http://www.w3.org/1999/xhtml"><head>' . "\n";
    echo '<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />' . "\n";
    echo '<meta name="keywords" content="' . h($site['keywords']) . '" />' . "\n";
    echo '<meta name="description" content="' . h($site['description']) . '" />' . "\n";
    echo '<meta name="author" content="OldBiliRetro" />' . "\n";
    echo '<meta name="robots" content="index,follow" />' . "\n";
    echo '<title>' . h($full) . '</title>' . "\n";
    echo '<link rel="stylesheet" type="text/css" href="assets/style.css" />' . "\n";
    echo '<script type="text/javascript" src="assets/site.js"></script>' . "\n";
    if ($extraHead !== '') {
        echo $extraHead . "\n";
    }
    echo '</head><body>' . "\n";

    echo '<div id="wrap">' . "\n";
    /* 顶部 */
    echo '<table width="100%" cellpadding="0" cellspacing="0" border="0" class="topbar">' .
         '<tr><td class="topbar-l">&nbsp;</td>' .
         '<td class="topbar-r">' . h(date('Y-m-d')) . ' | 面向古董设备 | ' .
         '<a href="https://github.com/4786925/OldBiliRetro" target="_blank">GitHub</a>' .
         '</td></tr></table>' . "\n";

    echo '<div id="header">' . "\n";
    echo '<table width="100%" cellpadding="0" cellspacing="0" border="0"><tr>' . "\n";
    echo '<td class="logo"><a href="index.php">Old<span>Bili</span>Retro</a>' .
         '<div class="logo-sub">' . h($site['subtitle']) . '</div></td>' . "\n";
    echo '<td align="right" class="banner-cell">' . "\n";
    echo '<div class="banner">' . "\n";
    echo '<b>本站已运行 ' . obr_days_since($site['start_date']) . ' 天</b><br />' . "\n";
    echo '访问 ' . intval($GLOBALS['STATS']['pv']) . ' 次 · 下载 ' . intval($GLOBALS['STATS']['downloads']) . " 次\n";
    echo '</div></td></tr></table>' . "\n";
    echo '</div>' . "\n";

    /* 导航 */
    echo '<div id="nav"><table cellpadding="0" cellspacing="0" border="0"><tr>' . "\n";
    foreach (obr_nav_items() as $key => $item) {
        $cls = ($key === $active) ? ' class="nav-on"' : '';
        echo '<td' . $cls . '><a href="' . h($item[1]) . '">' . h($item[0]) . '</a></td>' . "\n";
    }
    echo '</tr></table></div>' . "\n";

    echo '<table width="100%" cellpadding="0" cellspacing="0" border="0" id="main"><tr>' .
         '<td id="content">' . "\n";
}

function obr_page_bottom()
{
    $site = $GLOBALS['SITE'];
    echo '</td><td id="sidebar">' . "\n";
    obr_sidebar();
    echo '</td></tr></table>' . "\n";

    echo '<div id="footer">' . "\n";
    echo '<p>' . h($site['footer']) . '</p>' . "\n";
    echo '<p>本项目以 <a href="https://www.gnu.org/licenses/gpl-3.0.html" target="_blank">GPL-3.0</a> ' .
         '协议开源 · 完全由 AI 开发 · 与哔哩哔哩官方无关</p>' . "\n";
    echo '<p><a href="about.php">关于与声明</a> | <a href="about.php#legal">法律信息</a> | ' .
         '<a href="faq.php">常见问题</a> | <a href="download.php">下载</a></p>' . "\n";
    if (!empty($site['icp'])) {
        echo '<p>' . h($site['icp']) . '</p>' . "\n";
    }
    echo '<p>Copyright &copy; 2026 OldBiliRetro</p>' . "\n";
    echo '</div>' . "\n";
    echo '</div></body></html>';
}

function obr_sidebar()
{
    $site = $GLOBALS['SITE'];
    $ann = $GLOBALS['ANNOUNCE'];
    $latest = $GLOBALS['LATEST_FULL'];
    $stats = $GLOBALS['STATS'];

    echo '<div class="side-box">' . "\n";
    echo '<div class="side-title">公 告</div>' . "\n";
    echo '<div class="side-body">' . "\n";
    if (is_array($ann) && !empty($ann['text'])) {
        echo '<div class="announce">' . nl2br(h($ann['text'])) . '</div>';
    } else {
        echo '<div class="announce">' . nl2br(h($site['announce'])) . '</div>';
    }
    echo '</div></div>' . "\n";

    echo '<div class="side-box">' . "\n";
    echo '<div class="side-title">最新版本</div>' . "\n";
    echo '<div class="side-body">' . "\n";
    if (is_array($latest)) {
        echo '<p><b>' . h($latest['version']) . '</b><br />' . "\n";
        echo '发布于 ' . h($latest['date']) . '<br />' . "\n";
        echo '安装包 ' . obr_fmt_size(intval($latest['size'])) . '</p>' . "\n";
    } else {
        echo '<p>暂无安装包</p>' . "\n";
    }
    echo '<p><a href="download.php" class="btn">前往下载 &gt;&gt;</a></p>' . "\n";
    echo '</div></div>' . "\n";

    echo '<div class="side-box">' . "\n";
    echo '<div class="side-title">标 签</div>' . "\n";
    echo '<div class="side-body tags">' . "\n";
    $tags = obr_tags($site['tags']);
    foreach ($tags as $t) {
        echo '<a href="faq.php">' . h($t) . '</a> ';
    }
    echo '</div></div>' . "\n";

    echo '<div class="side-box">' . "\n";
    echo '<div class="side-title">友情链接</div>' . "\n";
    echo '<div class="side-body">' . "\n";
    if (!empty($site['links']) && is_array($site['links'])) {
        echo '<ul class="links">';
        foreach ($site['links'] as $l) {
            echo '<li><a href="' . h($l['url']) . '" target="_blank">' .
                 h($l['name']) . '</a></li>';
        }
        echo '</ul>';
    }
    echo '</div></div>' . "\n";

    echo '<div class="side-box">' . "\n";
    echo '<div class="side-title">站点统计</div>' . "\n";
    echo '<div class="side-body small">' . "\n";
    echo '总访问：' . intval($stats['pv']) . ' 次<br />' . "\n";
    echo '总下载：' . intval($stats['downloads']) . ' 次<br />' . "\n";
    echo '运行：' . obr_days_since($site['start_date']) . ' 天<br />' . "\n";
    echo 'PHP ' . PHP_VERSION;
    echo '</div></div>' . "\n";
}
