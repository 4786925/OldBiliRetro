<?php
/**
 * 安装包下载（令牌校验 + 限流 + 断点续传）
 */
require dirname(__FILE__) . '/inc/bootstrap.php';

obr_bump_stat('pv');

$token = isset($_GET['t']) ? preg_replace('/[^0-9a-f\-]/', '', $_GET['t']) : '';
$tk = obr_check_token($token);
if ($tk === false) {
    obr_dl_error('下载链接无效，请回到下载页重新获取。');
}

/* 15 秒等待（服务端强制，防刷） */
$age = time() - intval($tk['ts']);
if ($age < 15) {
    obr_dl_error('请等待 ' . (15 - $age) . ' 秒后再下载。');
    exit;
}
if ($age > 3600) {
    obr_dl_error('下载链接已过期，请重新获取。');
}
if (!obr_rate_limit('dl_file', 30, 3600)) {
    obr_dl_error('下载过于频繁，请稍后再试。');
}

/* 找到版本 */
$v = null;
foreach ($VERSIONS as $item) {
    if (intval($item['id']) === intval($tk['id'])) {
        $v = $item;
        break;
    }
}
if ($v === null || empty($v['file'])) {
    obr_dl_error('安装包不存在。');
}

$name = basename($v['file']);
$path = OBR_FILES . '/' . $name;
if (!is_file($path)) {
    obr_dl_error('文件已被移除，请联系站长。');
}

/* 统计与日志 */
obr_bump_stat('downloads');
obr_inc_hits($v['id']);
obr_log('download', $v['version'] . ' ' . $name);

/* 输出文件（支持 Range 断点续传） */
$size = filesize($path);
$mime = 'application/vnd.android.package-archive';
$outName = str_replace(array('"', "\r", "\n", ' '), array('', '', '', '_'),
        'OldBiliRetro-' . $v['version'] . '-' . $v['abi'] . '.apk');

@ini_set('zlib.output_compression', 'Off');
@set_time_limit(0);

header('Content-Type: ' . $mime);
header('Content-Disposition: attachment; filename="' . $outName . '"');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');
header('Accept-Ranges: bytes');

$start = 0;
$end = $size - 1;
if (!empty($_SERVER['HTTP_RANGE'])) {
    if (preg_match('/bytes=(\d*)-(\d*)/', $_SERVER['HTTP_RANGE'], $m)) {
        if ($m[1] !== '') {
            $start = intval($m[1]);
        }
        if ($m[2] !== '') {
            $end = intval($m[2]);
        }
        if ($start > $end || $start >= $size) {
            header('HTTP/1.1 416 Range Not Satisfiable');
            header('Content-Range: bytes */' . $size);
            exit;
        }
        header('HTTP/1.1 206 Partial Content');
        header('Content-Range: bytes ' . $start . '-' . $end . '/' . $size);
    }
}
header('Content-Length: ' . ($end - $start + 1));

$fp = @fopen($path, 'rb');
if (!$fp) {
    obr_dl_error('文件读取失败。');
}
fseek($fp, $start);
$left = $end - $start + 1;
$chunk = 65536;
while ($left > 0 && !feof($fp)) {
    $read = ($left > $chunk) ? $chunk : $left;
    echo fread($fp, $read);
    flush();
    $left -= $read;
}
fclose($fp);
exit;

/* ---------------- 辅助 ---------------- */

function obr_inc_hits($id)
{
    $path = OBR_DATA . '/versions.json';
    $fp = @fopen($path, 'c+b');
    if (!$fp) {
        return;
    }
    @flock($fp, LOCK_EX);
    $raw = stream_get_contents($fp);
    $list = json_decode($raw, true);
    if (is_array($list)) {
        foreach ($list as $i => $v) {
            if (intval($v['id']) === intval($id)) {
                $list[$i]['hits'] = intval(isset($v['hits']) ? $v['hits'] : 0) + 1;
                break;
            }
        }
        ftruncate($fp, 0);
        rewind($fp);
        fwrite($fp, json_encode($list));
    }
    @flock($fp, LOCK_UN);
    fclose($fp);
}

function obr_dl_error($msg)
{
    @header('Content-Type: text/html; charset=utf-8');
    echo '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" ' .
         '"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">' .
         '<html><head><meta http-equiv="Content-Type" content="text/html; charset=utf-8" />' .
         '<title>下载提示</title><link rel="stylesheet" href="assets/style.css" /></head>' .
         '<body><div id="wrap" style="padding:20px">' .
         '<div class="box"><div class="box-title">下载提示</div><div class="box-body">' .
         '<p>' . h($msg) . '</p>' .
         '<p><a class="btn" href="download.php">返回下载页</a></p>' .
         '</div></div></div></body></html>';
    exit;
}
