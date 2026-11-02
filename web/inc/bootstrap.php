<?php
/**
 * 统一入口：函数、配置、数据、会话
 */

if (!defined('OBR_ROOT')) {
    define('OBR_ROOT', dirname(dirname(__FILE__)));
}

require_once dirname(__FILE__) . '/functions.php';
require_once dirname(__FILE__) . '/config.php';

obr_bootstrap();

/* ---- 会话（只用于下载令牌与 CSRF，老浏览器也支持 Cookie） ---- */
if (session_id() === '') {
    $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
    @ini_set('session.use_only_cookies', '1');
    @ini_set('session.cookie_httponly', '1');
    session_name('OBRSID');
    session_set_cookie_params(0, '/', '', $secure, true);
    @session_start();
}

/* ---- 安全响应头 ---- */
@header('X-Content-Type-Options: nosniff');
@header('X-Frame-Options: SAMEORIGIN');
@header('Referrer-Policy: same-origin');
@header('Content-Type: text/html; charset=utf-8');

/* ---- 封禁检查（管理面板写入） ---- */
$bans = obr_read_json(OBR_DATA . '/bans.json');
if (is_array($bans) && in_array(obr_client_ip(), $bans)) {
    header('HTTP/1.1 403 Forbidden');
    echo '<!DOCTYPE html><html><head><meta charset="utf-8"><title>403</title></head>' .
         '<body style="font:12px Tahoma;background:#fff"><h1>403 Forbidden</h1>' .
         '<p>您的 IP 已被限制访问。</p></body></html>';
    exit;
}

/* ---- 读取站点数据 ---- */
$SITE = obr_read_json(OBR_DATA . '/site.json');
if (!is_array($SITE)) {
    $SITE = $GLOBALS['OBR_DEFAULT_SITE'];
}
$VERSIONS  = obr_read_json(OBR_DATA . '/versions.json');
$CHANGELOG = obr_read_json(OBR_DATA . '/changelog.json');
$ANNOUNCE  = obr_read_json(OBR_DATA . '/announce.json');
$STATS     = obr_read_json(OBR_DATA . '/stats.json');
if (!is_array($VERSIONS)) {
    $VERSIONS = array();
}
if (is_array($CHANGELOG) && isset($CHANGELOG['entries']) && is_array($CHANGELOG['entries'])) {
    $CHANGELOG = $CHANGELOG['entries'];
}
if (!is_array($CHANGELOG)) {
    $CHANGELOG = array();
}
if (!is_array($STATS)) {
    $STATS = array('pv' => 0, 'downloads' => 0);
}

/* ---- 简单的全站限流：同一 IP 每分钟 180 次页面访问 ---- */
if (!obr_rate_limit('page', 180, 60)) {
    header('HTTP/1.1 429 Too Many Requests');
    echo '<!DOCTYPE html><html><head><meta charset="utf-8"><title>访问太快</title></head>' .
         '<body style="font:12px Tahoma"><h1>访问太快了</h1>' .
         '<p>请稍后再试。本站对异常高频访问做了限制。</p></body></html>';
    exit;
}

/* ---- 最新版本 ---- */
$LATEST_FULL = obr_pick_package($VERSIONS, 'full');
$LATEST_LITE = obr_pick_package($VERSIONS, 'armeabi-v7a');
if ($LATEST_FULL === null) {
    $LATEST_FULL = obr_pick_package($VERSIONS, '');
}
if ($LATEST_LITE === null) {
    $LATEST_LITE = $LATEST_FULL;
}
