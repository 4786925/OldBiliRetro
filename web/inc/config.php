<?php
/**
 * OldBiliRetro 官网 - 基础配置
 * 兼容 PHP 5.3（虚拟主机），不依赖数据库。
 */

if (!defined('OBR_ROOT')) {
    define('OBR_ROOT', dirname(dirname(__FILE__)));
}

/* 数据目录放在网站根目录之外，避免被直接下载（kangle 不支持 .htaccess） */
define('OBR_DATA', dirname(OBR_ROOT) . '/obr_data');
/* 安装包同样放在网站根目录之外，防止绕过 15 秒等待直链下载 */
define('OBR_FILES', dirname(OBR_ROOT) . '/obr_files');
define('OBR_VERSION', '1.0');

/* 站点默认信息（首次运行会写入 data/site.json） */
$OBR_DEFAULT_SITE = array(
    'title'       => 'OldBiliRetro · 老版哔哩',
    'subtitle'    => '面向古董设备的第三方哔哩哔哩客户端',
    'keywords'    => 'OldBiliRetro,哔哩哔哩,第三方客户端,安卓2.3,复古,Holo,弹幕,追番,GPL,开源',
    'description' => 'OldBiliRetro 是面向古董设备的第三方哔哩哔哩客户端，Android 2.3 ~ Android 17 全兼容，严格还原 2013-2014 年 Holo Dark 界面。',
    'announce'    => '本站为 OldBiliRetro 唯一官方发布页。请勿安装来路不明的修改版。',
    'intro'       => 'OldBiliRetro（老版哔哩）是一个完全由 AI 开发的第三方哔哩哔哩客户端，' .
                     '目标设备是那些被官方 App 抛弃的老手机：Android 2.3 甚至更低，' .
                     '内存只有 64MB / 96MB 的古董机。界面严格还原 2013-2014 年的 Holo Dark 风格。',
    'tags'        => '开源,GPL-3.0,Android 2.3,复古UI,弹幕,追番,第三方客户端,AI开发,老设备',
    'links'       => array(
        array('name' => 'GitHub 仓库', 'url' => 'https://github.com/4786925/OldBiliRetro'),
        array('name' => 'Release 下载', 'url' => 'https://github.com/4786925/OldBiliRetro/releases'),
        array('name' => '问题反馈', 'url' => 'https://github.com/4786925/OldBiliRetro/issues'),
    ),
    'footer'      => '本站为第三方爱好者站点，与哔哩哔哩官方无任何关联。所有视频、弹幕、评论版权归原作者及哔哩哔哩所有。',
    'start_date'  => '2026-10-25',
    'icp'         => '',
    'stat_base'   => 0,
);

/* 首次访问：创建数据目录与默认文件 */
function obr_bootstrap()
{
    if (!is_dir(OBR_DATA)) {
        @mkdir(OBR_DATA, 0755, true);
    }
    if (!is_dir(OBR_FILES)) {
        @mkdir(OBR_FILES, 0755, true);
    }
    if (!is_dir(OBR_DATA . '/logs')) {
        @mkdir(OBR_DATA . '/logs', 0755, true);
    }
    $need = array(
        'site.json'      => $GLOBALS['OBR_DEFAULT_SITE'],
        'versions.json'  => array(),
        'changelog.json' => array(),
        'announce.json'  => array('text' => '', 'date' => ''),
        'stats.json'     => array('pv' => 0, 'downloads' => 0),
        'bans.json'      => array(),
        'admin.json'     => array(
            'user'    => 'admin',
            'salt'    => obr_rand(16),
            'pass'    => '',
            'updated' => time(),
        ),
    );
    foreach ($need as $name => $val) {
        $path = OBR_DATA . '/' . $name;
        if (!file_exists($path)) {
            obr_write_json($path, $val);
        }
    }
    $admin = obr_read_json(OBR_DATA . '/admin.json');
    if (empty($admin['pass'])) {
        /* 初始密码：oldbili2026（首次登录后请立刻修改） */
        $admin['pass'] = obr_hash_pass('oldbili2026', $admin['salt']);
        obr_write_json(OBR_DATA . '/admin.json', $admin);
    }
    if (!file_exists(OBR_ROOT . '/.installed')) {
        @file_put_contents(OBR_ROOT . '/.installed', date('Y-m-d H:i:s'));
    }
}
