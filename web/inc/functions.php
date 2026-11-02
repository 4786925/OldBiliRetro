<?php
/**
 * OldBiliRetro 官网 - 公共函数（PHP 5.3 兼容）
 */

function obr_rand($len)
{
    $s = '';
    $chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    for ($i = 0; $i < $len; $i++) {
        $s .= $chars[mt_rand(0, strlen($chars) - 1)];
    }
    return $s;
}

function h($s)
{
    return htmlspecialchars((string) $s, ENT_QUOTES, 'UTF-8');
}

function obr_read_json($path)
{
    if (!is_file($path)) {
        return null;
    }
    $fp = @fopen($path, 'rb');
    if (!$fp) {
        return null;
    }
    @flock($fp, LOCK_SH);
    $raw = stream_get_contents($fp);
    @flock($fp, LOCK_UN);
    fclose($fp);
    $data = json_decode($raw, true);
    return is_array($data) ? $data : null;
}

function obr_write_json($path, $data)
{
    $json = json_encode($data);
    if ($json === false) {
        return false;
    }
    $tmp = $path . '.tmp' . mt_rand(1000, 9999);
    $fp = @fopen($tmp, 'wb');
    if (!$fp) {
        return false;
    }
    @flock($fp, LOCK_EX);
    fwrite($fp, $json);
    fflush($fp);
    @flock($fp, LOCK_UN);
    fclose($fp);
    if (!@rename($tmp, $path)) {
        @copy($tmp, $path);
        @unlink($tmp);
    }
    return true;
}

function obr_hash_pass($plain, $salt)
{
    return hash('sha256', $salt . '|' . $plain . '|oldbiliretro');
}

function obr_client_ip()
{
    $keys = array('REMOTE_ADDR');
    foreach ($keys as $k) {
        if (!empty($_SERVER[$k])) {
            return preg_replace('/[^0-9a-fA-F:\.]/', '', $_SERVER[$k]);
        }
    }
    return '0.0.0.0';
}

function obr_now()
{
    return time();
}

function obr_fmt_size($bytes)
{
    if ($bytes >= 1048576) {
        return round($bytes / 1048576, 2) . ' MB';
    }
    if ($bytes >= 1024) {
        return round($bytes / 1024, 1) . ' KB';
    }
    return $bytes . ' B';
}

/** 文件限流：$bucket 每 IP 在 $window 秒内最多 $max 次 */
function obr_rate_limit($bucket, $max, $window)
{
    $dir = OBR_DATA . '/rate';
    if (!is_dir($dir)) {
        @mkdir($dir, 0755, true);
    }
    $ip = obr_client_ip();
    $file = $dir . '/' . preg_replace('/[^a-zA-Z0-9_]/', '', $bucket) . '_' .
            md5($ip) . '.json';
    $fp = @fopen($file, 'c+b');
    if (!$fp) {
        return true; /* 拿不到锁就放行，避免误伤 */
    }
    @flock($fp, LOCK_EX);
    $raw = stream_get_contents($fp);
    $data = json_decode($raw, true);
    if (!is_array($data)) {
        $data = array('t' => obr_now(), 'n' => 0);
    }
    if (obr_now() - $data['t'] > $window) {
        $data = array('t' => obr_now(), 'n' => 0);
    }
    $data['n']++;
    $ok = ($data['n'] <= $max);
    ftruncate($fp, 0);
    rewind($fp);
    fwrite($fp, json_encode($data));
    @flock($fp, LOCK_UN);
    fclose($fp);
    return $ok;
}

function obr_log($type, $msg)
{
    $dir = OBR_DATA . '/logs';
    if (!is_dir($dir)) {
        @mkdir($dir, 0755, true);
    }
    $line = date('Y-m-d H:i:s') . "\t" . obr_client_ip() . "\t" . $type . "\t" .
            str_replace(array("\r", "\n", "\t"), ' ', $msg) . "\n";
    @file_put_contents($dir . '/site-' . date('Ym') . '.log', $line,
            FILE_APPEND | LOCK_EX);
}

function obr_bump_stat($key)
{
    $path = OBR_DATA . '/stats.json';
    $fp = @fopen($path, 'c+b');
    if (!$fp) {
        return;
    }
    @flock($fp, LOCK_EX);
    $raw = stream_get_contents($fp);
    $d = json_decode($raw, true);
    if (!is_array($d)) {
        $d = array('pv' => 0, 'downloads' => 0);
    }
    if (!isset($d[$key])) {
        $d[$key] = 0;
    }
    $d[$key]++;
    ftruncate($fp, 0);
    rewind($fp);
    fwrite($fp, json_encode($d));
    @flock($fp, LOCK_UN);
    fclose($fp);
}


/** 下载签名密钥（自动生成，存 data/secret.php） */
function obr_secret()
{
    $f = OBR_DATA . '/secret.php';
    if (is_file($f)) {
        $v = @include $f;
        if (is_string($v) && strlen($v) >= 32) {
            return $v;
        }
    }
    $v = bin2hex(obr_rand(32));
    @file_put_contents($f, "<?php return '" . $v . "';");
    return $v;
}

/**
 * 生成不依赖 Cookie 的下载令牌：id-ts-签名
 * 老浏览器（安卓 1.6/2.3）经常不保存 Session Cookie，用签名令牌最稳。
 */
function obr_make_token($id)
{
    $ts = time();
    $sig = substr(hash_hmac('sha256', $id . '|' . $ts, obr_secret()), 0, 32);
    return $id . '-' . $ts . '-' . $sig;
}

/** 校验令牌，返回 id 或 false */
function obr_check_token($token)
{
    if (!preg_match('/^(\d+)-(\d+)-([a-f0-9]{32})$/', $token, $m)) {
        return false;
    }
    $id = intval($m[1]);
    $ts = intval($m[2]);
    $sig = $m[3];
    $want = substr(hash_hmac('sha256', $id . '|' . $ts, obr_secret()), 0, 32);
    if ($sig !== $want) {
        return false;
    }
    return array('id' => $id, 'ts' => $ts);
}

function obr_csrf_token()
{
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = obr_rand(24);
    }
    return $_SESSION['csrf'];
}

function obr_csrf_check($token)
{
    return !empty($_SESSION['csrf']) && $token === $_SESSION['csrf'];
}

/** 读取某平台推荐安装包 */
function obr_pick_package($versions, $abi)
{
    $best = null;
    foreach ($versions as $v) {
        if (empty($v['file'])) {
            continue;
        }
        if (!empty($v['hidden'])) {
            continue;
        }
        if ($abi !== '' && isset($v['abi']) && $v['abi'] !== $abi) {
            continue;
        }
        if ($best === null || intval($v['code']) > intval($best['code'])) {
            $best = $v;
        }
    }
    return $best;
}

function obr_tags($str)
{
    $out = array();
    $parts = explode(',', str_replace('，', ',', $str));
    foreach ($parts as $p) {
        $p = trim($p);
        if ($p !== '') {
            $out[] = $p;
        }
    }
    return $out;
}

function obr_days_since($date)
{
    $t = strtotime($date);
    if (!$t) {
        return 0;
    }
    $d = intval((time() - $t) / 86400);
    return $d > 0 ? $d : 0;
}
