<?php
/**
 * OldBiliRetro 管理面板（独立单文件）
 * 文件名：oldbiliretro_admin_2026.php
 * 默认密码：oldbili2026（首次登录后请立刻修改）
 *
 * 兼容 PHP 5.3，无数据库，数据存 data/*.json
 */

define('ADM_ROOT', dirname(__FILE__));
require ADM_ROOT . '/inc/functions.php';
require ADM_ROOT . '/inc/config.php';
obr_bootstrap();

@ini_set('session.use_only_cookies', '1');
@ini_set('session.cookie_httponly', '1');
session_name('OBRADM');
session_set_cookie_params(0, '/', '', false, true);
@session_start();

header('Content-Type: text/html; charset=utf-8');
header('X-Frame-Options: SAMEORIGIN');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');

$ADMIN_FILE = OBR_DATA . '/admin.json';
$ADMIN = obr_read_json($ADMIN_FILE);
if (!is_array($ADMIN)) {
    $ADMIN = array('user' => 'admin', 'salt' => obr_rand(16), 'pass' => '', 'updated' => time());
}

$IP = obr_client_ip();
$now = time();
$msg = '';
$err = '';

/* ---------------- 登录限流 ---------------- */
function adm_fail_count($ip)
{
    $f = OBR_DATA . '/login.json';
    $d = obr_read_json($f);
    if (!is_array($d)) {
        $d = array();
    }
    if (!isset($d[$ip])) {
        $d[$ip] = array('n' => 0, 't' => 0);
    }
    return $d;
}

function adm_fail_save($ip, $n)
{
    $f = OBR_DATA . '/login.json';
    $d = obr_read_json($f);
    if (!is_array($d)) {
        $d = array();
    }
    $d[$ip] = array('n' => $n, 't' => time());
    obr_write_json($f, $d);
}

function adm_is_locked($ip)
{
    $d = adm_fail_count($ip);
    if (isset($d[$ip]) && intval($d[$ip]['n']) >= 5 &&
            time() - intval($d[$ip]['t']) < 900) {
        return true;
    }
    return false;
}

function adm_fails($ip)
{
    $d = adm_fail_count($ip);
    return isset($d[$ip]) ? intval($d[$ip]['n']) : 0;
}

/* ---------------- 登录 / 退出 ---------------- */
$logged = !empty($_SESSION['adm_ok']) && $_SESSION['adm_ok'] === 1;
$csrf = obr_csrf_token();

if (isset($_GET['logout'])) {
    $_SESSION = array();
    @session_destroy();
    header('Location: ' . basename(__FILE__));
    exit;
}

if (!$logged && isset($_POST['do_login'])) {
    if (adm_is_locked($IP)) {
        $err = '尝试次数过多，请 15 分钟后再试。';
    } else {
        $user = isset($_POST['user']) ? trim($_POST['user']) : '';
        $pass = isset($_POST['pass']) ? $_POST['pass'] : '';
        if (adm_fails($IP) >= 3) {
            $a = isset($_POST['captcha_a']) ? intval($_POST['captcha_a']) : 0;
            $b = isset($_POST['captcha_b']) ? intval($_POST['captcha_b']) : 0;
            $c = isset($_POST['captcha']) ? intval($_POST['captcha']) : -1;
            if ($a + $b !== $c) {
                $err = '验证码错误。';
                adm_fail_save($IP, adm_fails($IP) + 1);
            }
        }
        if ($err === '') {
            if ($user === $ADMIN['user'] &&
                    obr_hash_pass($pass, $ADMIN['salt']) === $ADMIN['pass']) {
                $_SESSION['adm_ok'] = 1;
                $_SESSION['adm_user'] = $ADMIN['user'];
                adm_fail_save($IP, 0);
                obr_log('admin', 'login ok');
                header('Location: ' . basename(__FILE__));
                exit;
            } else {
                adm_fail_save($IP, adm_fails($IP) + 1);
                obr_log('admin', 'login fail');
                $err = '账号或密码错误。';
            }
        }
    }
}

/* ---------------- API（供自动化脚本使用） ---------------- */
if (isset($_GET['api']) && isset($_POST['key'])) {
    header('Content-Type: application/json; charset=utf-8');
    $data = obr_read_json(OBR_DATA . '/site.json');
    $apiKey = isset($ADMIN['api_key']) ? $ADMIN['api_key'] : '';
    if ($apiKey === '' || $_POST['key'] !== $apiKey) {
        echo json_encode(array('ok' => false, 'msg' => 'bad key'));
        exit;
    }
    $act = isset($_POST['action']) ? $_POST['action'] : '';
    $out = adm_api($act, $_POST);
    echo json_encode($out);
    exit;
}


/** 读取更新日志（兼容 {app,entries:[...]} 与纯数组） */
function adm_read_logs()
{
    $logs = obr_read_json(OBR_DATA . '/changelog.json');
    if (is_array($logs) && isset($logs['entries']) && is_array($logs['entries'])) {
        $logs = $logs['entries'];
    }
    if (!is_array($logs)) {
        $logs = array();
    }
    return array_values($logs);
}

function adm_write_logs($logs)
{
    obr_write_json(OBR_DATA . '/changelog.json', array_values($logs));
}

function adm_api($act, $in)
{
    $versions = obr_read_json(OBR_DATA . '/versions.json');
    $changelog = adm_read_logs();
    if (!is_array($versions)) {
        $versions = array();
    }
    if ($act === 'add_version') {
        $v = array(
            'id'     => adm_next_id($versions),
            'version' => isset($in['version']) ? $in['version'] : '',
            'code'   => intval(isset($in['code']) ? $in['code'] : 0),
            'abi'    => isset($in['abi']) ? $in['abi'] : 'full',
            'date'   => isset($in['date']) ? $in['date'] : date('Y-m-d'),
            'notes'  => isset($in['notes']) ? $in['notes'] : '',
            'size'   => intval(isset($in['size']) ? $in['size'] : 0),
            'file'   => isset($in['file']) ? $in['file'] : '',
            'hidden' => 0,
            'hits'   => 0,
        );
        $versions[] = $v;
        obr_write_json(OBR_DATA . '/versions.json', $versions);
        return array('ok' => true, 'id' => $v['id']);
    }
    if ($act === 'add_log') {
        $changelog[] = array(
            'version' => isset($in['version']) ? $in['version'] : '',
            'code'    => intval(isset($in['code']) ? $in['code'] : 0),
            'date'    => isset($in['date']) ? $in['date'] : date('Y-m-d'),
            'items'   => isset($in['items']) ? explode("\n", $in['items']) : array(),
            'known'   => isset($in['known']) ? explode("\n", $in['known']) : array(),
        );
        adm_write_logs($changelog);
        return array('ok' => true);
    }
    if ($act === 'list') {
        return array('ok' => true, 'versions' => $versions, 'changelog' => $changelog);
    }
    return array('ok' => false, 'msg' => 'unknown action');
}

function adm_next_id($list)
{
    $max = 0;
    foreach ($list as $v) {
        if (intval($v['id']) > $max) {
            $max = intval($v['id']);
        }
    }
    return $max + 1;
}

/* ================= 以下为已登录操作 ================= */
if ($logged) {

    /* 保存站点信息 */
    if (isset($_POST['save_site'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            $site = obr_read_json(OBR_DATA . '/site.json');
            $keys = array('title', 'subtitle', 'keywords', 'description',
                    'intro', 'tags', 'footer', 'start_date', 'icp', 'announce');
            foreach ($keys as $k) {
                if (isset($_POST[$k])) {
                    $site[$k] = trim($_POST[$k]);
                }
            }
            if (isset($_POST['links'])) {
                $lines = explode("\n", str_replace("\r", '', $_POST['links']));
                $links = array();
                foreach ($lines as $ln) {
                    $ln = trim($ln);
                    if ($ln === '') {
                        continue;
                    }
                    $parts = explode('|', $ln);
                    if (count($parts) >= 2) {
                        $links[] = array('name' => trim($parts[0]), 'url' => trim($parts[1]));
                    }
                }
                $site['links'] = $links;
            }
            obr_write_json(OBR_DATA . '/site.json', $site);
            $msg = '站点信息已保存。';
            obr_log('admin', 'save site');
        }
    }

    /* 保存公告 */
    if (isset($_POST['save_announce'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            obr_write_json(OBR_DATA . '/announce.json', array(
                'text' => trim($_POST['announce_text']),
                'date' => date('Y-m-d H:i'),
            ));
            $msg = '公告已更新。';
            obr_log('admin', 'save announce');
        }
    }

    /* 公告（支持定时发布） */
    if (isset($_POST['save_notice'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            $list = obr_read_json(OBR_DATA . '/announcements.json');
            if (!is_array($list)) {
                $list = array();
            }
            $id = intval(isset($_POST['notice_id']) ? $_POST['notice_id'] : 0);
            $item = array(
                'id'         => $id > 0 ? $id : (count($list) + 1),
                'title'      => trim($_POST['notice_title']),
                'text'       => trim($_POST['notice_text']),
                'publish_at' => trim($_POST['notice_publish']),
                'expire_at'  => trim($_POST['notice_expire']),
                'hidden'     => isset($_POST['notice_hidden']) ? 1 : 0,
            );
            $found = false;
            foreach ($list as $i => $old) {
                if (intval($old['id']) === $item['id']) {
                    $list[$i] = $item;
                    $found = true;
                    break;
                }
            }
            if (!$found) {
                $item['id'] = 1;
                foreach ($list as $old) {
                    if (intval($old['id']) >= $item['id']) {
                        $item['id'] = intval($old['id']) + 1;
                    }
                }
                $list[] = $item;
            }
            obr_write_json(OBR_DATA . '/announcements.json', array_values($list));
            $msg = '公告已保存。';
            obr_log('admin', 'save notice ' . $item['title']);
        }
    }
    if (isset($_GET['noticedel'])) {
        $id = intval($_GET['noticedel']);
        $list = obr_read_json(OBR_DATA . '/announcements.json');
        if (is_array($list)) {
            $out = array();
            foreach ($list as $an) {
                if (intval($an['id']) !== $id) {
                    $out[] = $an;
                }
            }
            obr_write_json(OBR_DATA . '/announcements.json', array_values($out));
            $msg = '公告已删除。';
        }
    }

    /* 上传安装包 */
    if (isset($_POST['do_upload'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } elseif (!obr_rate_limit('upload', 10, 3600)) {
            $err = '上传过于频繁，请稍后再试。';
        } elseif (!isset($_FILES['apk']) || $_FILES['apk']['error'] !== 0) {
            $err = '上传失败（错误码 ' .
                   (isset($_FILES['apk']) ? intval($_FILES['apk']['error']) : -1) . '）。';
        } else {
            $f = $_FILES['apk'];
            if ($f['size'] <= 0 || $f['size'] > 120 * 1024 * 1024) {
                $err = '文件大小不合法（上限 120MB）。';
            } else {
                $fh = @fopen($f['tmp_name'], 'rb');
                $magic = $fh ? fread($fh, 2) : '';
                if ($fh) {
                    fclose($fh);
                }
                if ($magic !== 'PK') {
                    $err = '不是有效的 APK（ZIP）文件。';
                } else {
                    $version = isset($_POST['version']) ? trim($_POST['version']) : '';
                    $code = intval(isset($_POST['code']) ? $_POST['code'] : 0);
                    $abi = isset($_POST['abi']) ? $_POST['abi'] : 'full';
                    $notes = isset($_POST['notes']) ? trim($_POST['notes']) : '';
                    if ($version === '') {
                        $err = '请填写版本号。';
                    } else {
                        $safe = 'OldBiliRetro-' . preg_replace('/[^0-9a-zA-Z\.]/', '', $version) .
                                '-' . ($abi === 'armeabi-v7a' ? 'armv7-lite' : 'full') .
                                '-' . date('YmdHis') . '.apk';
                        if (!@move_uploaded_file($f['tmp_name'], OBR_FILES . '/' . $safe)) {
                            $err = '保存文件失败，请检查 files/ 目录权限。';
                        } else {
                            $versions = obr_read_json(OBR_DATA . '/versions.json');
                            if (!is_array($versions)) {
                                $versions = array();
                            }
                            $versions[] = array(
                                'id'     => adm_next_id($versions),
                                'version' => $version,
                                'code'   => $code,
                                'abi'    => $abi,
                                'date'   => date('Y-m-d'),
                                'notes'  => $notes,
                                'size'   => intval($f['size']),
                                'file'   => $safe,
                                'hidden' => 0,
                                'hits'   => 0,
                            );
                            obr_write_json(OBR_DATA . '/versions.json', $versions);
                            $msg = '安装包已上传：' . h($safe);
                            obr_log('admin', 'upload ' . $safe);
                        }
                    }
                }
            }
        }
    }

    /* 登记 files/ 里已存在的安装包（FTP 上传后用） */
    if (isset($_POST['do_register'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            $file = basename(isset($_POST['file_name']) ? $_POST['file_name'] : '');
            $path = OBR_FILES . '/' . $file;
            $version = trim(isset($_POST['version']) ? $_POST['version'] : '');
            if ($file === '' || !is_file($path)) {
                $err = 'files/ 目录里没有这个文件。';
            } elseif ($version === '') {
                $err = '请填写版本号。';
            } elseif (substr(strtolower($file), -4) !== '.apk') {
                $err = '只能登记 .apk 文件。';
            } else {
                $versions = obr_read_json(OBR_DATA . '/versions.json');
                if (!is_array($versions)) {
                    $versions = array();
                }
                $versions[] = array(
                    'id'     => adm_next_id($versions),
                    'version' => $version,
                    'code'   => intval(isset($_POST['code']) ? $_POST['code'] : 0),
                    'abi'    => isset($_POST['abi']) ? $_POST['abi'] : 'full',
                    'date'   => date('Y-m-d'),
                    'notes'  => trim(isset($_POST['notes']) ? $_POST['notes'] : ''),
                    'size'   => filesize($path),
                    'file'   => $file,
                    'hidden' => 0,
                    'hits'   => 0,
                );
                obr_write_json(OBR_DATA . '/versions.json', $versions);
                $msg = '已登记：' . h($file);
                obr_log('admin', 'register ' . $file);
            }
        }
    }

    /* 版本操作 */
    if (isset($_GET['vop'], $_GET['id'])) {
        $id = intval($_GET['id']);
        $op = $_GET['vop'];
        $versions = obr_read_json(OBR_DATA . '/versions.json');
        if (is_array($versions)) {
            foreach ($versions as $i => $v) {
                if (intval($v['id']) !== $id) {
                    continue;
                }
                if ($op === 'hide') {
                    $versions[$i]['hidden'] = empty($v['hidden']) ? 1 : 0;
                } elseif ($op === 'delete') {
                    if (!empty($v['file']) && is_file(OBR_FILES . '/' . basename($v['file']))) {
                        @unlink(OBR_FILES . '/' . basename($v['file']));
                    }
                    unset($versions[$i]);
                    $versions = array_values($versions);
                } elseif ($op === 'latest') {
                    $versions[$i]['latest'] = 1;
                    foreach ($versions as $j => $x) {
                        if ($j !== $i) {
                            $versions[$j]['latest'] = 0;
                        }
                    }
                }
                break;
            }
            obr_write_json(OBR_DATA . '/versions.json', $versions);
            $msg = '版本操作完成。';
            obr_log('admin', 'version ' . $op . ' ' . $id);
        }
    }

    /* 更新日志保存 */
    if (isset($_POST['save_log'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            $idx = isset($_POST['log_index']) ? intval($_POST['log_index']) : -1;
            $entry = array(
                'version' => trim($_POST['log_version']),
                'code'    => intval($_POST['log_code']),
                'date'    => trim($_POST['log_date']),
                'items'   => adm_lines($_POST['log_items']),
                'known'   => adm_lines($_POST['log_known']),
            );
            $logs = adm_read_logs();
            if ($idx >= 0 && isset($logs[$idx])) {
                $logs[$idx] = $entry;
            } else {
                $logs[] = $entry;
            }
            adm_write_logs($logs);
            $msg = '更新日志已保存。';
            obr_log('admin', 'save log ' . $entry['version']);
        }
    }

    if (isset($_GET['logdel'])) {
        $idx = intval($_GET['logdel']);
        $logs = adm_read_logs();
        if (isset($logs[$idx])) {
            unset($logs[$idx]);
            adm_write_logs($logs);
            $msg = '已删除一条更新日志。';
        }
    }

    /* 修改密码 */
    if (isset($_POST['change_pass'])) {
        if (!obr_csrf_check(isset($_POST['csrf']) ? $_POST['csrf'] : '')) {
            $err = '会话已过期，请重试。';
        } else {
            $old = isset($_POST['old_pass']) ? $_POST['old_pass'] : '';
            $new1 = isset($_POST['new_pass']) ? $_POST['new_pass'] : '';
            $new2 = isset($_POST['new_pass2']) ? $_POST['new_pass2'] : '';
            if (obr_hash_pass($old, $ADMIN['salt']) !== $ADMIN['pass']) {
                $err = '原密码不正确。';
            } elseif (strlen($new1) < 8) {
                $err = '新密码至少 8 位。';
            } elseif ($new1 !== $new2) {
                $err = '两次输入的新密码不一致。';
            } else {
                $ADMIN['salt'] = obr_rand(16);
                $ADMIN['pass'] = obr_hash_pass($new1, $ADMIN['salt']);
                $ADMIN['updated'] = time();
                obr_write_json($ADMIN_FILE, $ADMIN);
                $msg = '密码已修改，下次请用新密码登录。';
                obr_log('admin', 'change password');
            }
        }
    }

    /* 生成 / 更新 API 密钥 */
    if (isset($_POST['gen_api'])) {
        $ADMIN['api_key'] = obr_rand(40);
        obr_write_json($ADMIN_FILE, $ADMIN);
        $msg = 'API 密钥已更新。';
    }

    /* IP 封禁 */
    if (isset($_POST['ban_ip'])) {
        $ip = trim($_POST['ban_ip']);
        if (filter_var($ip, FILTER_VALIDATE_IP) === false) {
            $err = 'IP 格式不正确。';
        } else {
            $bans = obr_read_json(OBR_DATA . '/bans.json');
            if (!is_array($bans)) {
                $bans = array();
            }
            if (!in_array($ip, $bans)) {
                $bans[] = $ip;
                obr_write_json(OBR_DATA . '/bans.json', $bans);
            }
            $msg = '已封禁 ' . h($ip);
        }
    }
    if (isset($_GET['unban'])) {
        $ip = trim($_GET['unban']);
        $bans = obr_read_json(OBR_DATA . '/bans.json');
        if (is_array($bans)) {
            $out = array();
            foreach ($bans as $b) {
                if ($b !== $ip) {
                    $out[] = $b;
                }
            }
            obr_write_json(OBR_DATA . '/bans.json', $out);
        }
        $msg = '已解除封禁。';
    }
}

function adm_cmp_notice($a, $b)
{
    $ta = isset($a['publish_at']) ? strtotime($a['publish_at']) : 0;
    $tb = isset($b['publish_at']) ? strtotime($b['publish_at']) : 0;
    if ($ta == $tb) {
        return 0;
    }
    return ($ta > $tb) ? -1 : 1;
}

function adm_lines($text)
{
    $out = array();
    $lines = explode("\n", str_replace("\r", '', (string) $text));
    foreach ($lines as $ln) {
        $ln = trim($ln);
        if ($ln !== '') {
            $out[] = $ln;
        }
    }
    return $out;
}

/* ================= 渲染 ================= */
?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
 "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="robots" content="noindex,nofollow" />
<title>OldBiliRetro 管理面板</title>
<link rel="stylesheet" type="text/css" href="assets/style.css" />
<style type="text/css">
body { background:#eef1f5; }
.adm { width: 980px; margin: 14px auto; }
.adm-head { background:#00a1d6; color:#fff; padding:10px 14px; font:bold 16px Tahoma,"宋体"; }
.adm-head a { color:#fff; float:right; font-size:12px; font-weight:normal; }
.adm-body { background:#fff; border:1px solid #c9d3dd; padding:14px; }
.adm-tabs { margin:0 0 12px; padding:0; border-bottom:1px solid #c9d3dd; }
.adm-tabs a { display:inline-block; padding:6px 14px; border:1px solid #c9d3dd;
  border-bottom:0; margin-right:4px; background:#f6f8fa; color:#22608a; }
.adm-tabs a.on { background:#fff; font-weight:bold; color:#00a1d6; }
.field { margin:8px 0; }
.field label { display:block; font-weight:bold; color:#22608a; margin-bottom:3px; }
.field input.txt, .field textarea { width:96%; padding:4px; border:1px solid #bbb; font:12px Tahoma,"宋体"; }
.field textarea { height:80px; }
.field input.small { width:120px; }
.ok { border:1px solid #9c9; background:#eefaf0; padding:8px; margin:8px 0; color:#060; }
.bad { border:1px solid #e99; background:#fdf0f0; padding:8px; margin:8px 0; color:#900; }
table.adm-list { width:100%; border-collapse:collapse; }
table.adm-list th, table.adm-list td { border:1px solid #c9d3dd; padding:4px 6px; font-size:12px; }
table.adm-list th { background:#f3f6fa; }
.loginbox { width:360px; margin:60px auto; }
.loginbox .adm-body { padding:20px; }
</style>
</head>
<body>

<?php if (!$logged): ?>
<div class="loginbox">
    <div class="adm-head">OldBiliRetro 管理面板</div>
    <div class="adm-body">
        <?php if (!empty($err)): ?><div class="bad"><?php echo h($err); ?></div><?php endif; ?>
        <form method="post" action="<?php echo h(basename(__FILE__)); ?>">
            <div class="field">
                <label>账号</label>
                <input class="txt" type="text" name="user" value="admin" />
            </div>
            <div class="field">
                <label>密码</label>
                <input class="txt" type="password" name="pass" />
            </div>
            <?php if (adm_fails($IP) >= 3): ?>
            <div class="field">
                <label>验证码：<?php $a = mt_rand(2, 9); $b = mt_rand(2, 9); ?>
                    <?php echo $a; ?> + <?php echo $b; ?> = ?</label>
                <input type="hidden" name="captcha_a" value="<?php echo $a; ?>" />
                <input type="hidden" name="captcha_b" value="<?php echo $b; ?>" />
                <input class="txt small" type="text" name="captcha" />
            </div>
            <?php endif; ?>
            <div class="field">
                <input type="submit" name="do_login" value="登 录" class="btn" />
            </div>
        </form>
        <p class="small">首次登录默认密码：oldbili2026，登录后请立即修改。</p>
    </div>
</div>
<?php else: ?>
<?php
$tab = isset($_GET['tab']) ? $_GET['tab'] : 'home';
$versions = obr_read_json(OBR_DATA . '/versions.json');
$changelog = adm_read_logs();
$logs = obr_read_json(OBR_DATA . '/site.json');
$announce = obr_read_json(OBR_DATA . '/announce.json');
$stats = obr_read_json(OBR_DATA . '/stats.json');
$bans = obr_read_json(OBR_DATA . '/bans.json');
if (!is_array($versions)) { $versions = array(); }
if (!is_array($changelog)) { $changelog = array(); }
if (!is_array($logs)) { $logs = $GLOBALS['OBR_DEFAULT_SITE']; }
if (!is_array($announce)) { $announce = array('text' => '', 'date' => ''); }
if (!is_array($stats)) { $stats = array('pv' => 0, 'downloads' => 0); }
if (!is_array($bans)) { $bans = array(); }
$self = h(basename(__FILE__));
?>
<div class="adm">
    <div class="adm-head">OldBiliRetro 管理面板
        <a href="<?php echo $self; ?>?logout=1">退出登录</a>
    </div>
    <div class="adm-body">
        <?php if (obr_hash_pass('oldbili2026', $ADMIN['salt']) === $ADMIN['pass']): ?>
            <div class="bad"><b>安全提醒：</b>你仍在使用默认密码 oldbili2026，
                请立刻到 <a href="<?php echo $self; ?>?tab=pass">修改密码</a>。</div>
        <?php endif; ?>
        <?php if ($msg !== ''): ?><div class="ok"><?php echo $msg; ?></div><?php endif; ?>
        <?php if ($err !== ''): ?><div class="bad"><?php echo h($err); ?></div><?php endif; ?>

        <div class="adm-tabs">
            <a href="<?php echo $self; ?>?tab=home" class="<?php echo $tab === 'home' ? 'on' : ''; ?>">概览</a>
            <a href="<?php echo $self; ?>?tab=site" class="<?php echo $tab === 'site' ? 'on' : ''; ?>">站点内容</a>
            <a href="<?php echo $self; ?>?tab=notice" class="<?php echo $tab === 'notice' ? 'on' : ''; ?>">公告</a>
            <a href="<?php echo $self; ?>?tab=log" class="<?php echo $tab === 'log' ? 'on' : ''; ?>">更新日志</a>
            <a href="<?php echo $self; ?>?tab=version" class="<?php echo $tab === 'version' ? 'on' : ''; ?>">版本与安装包</a>
            <a href="<?php echo $self; ?>?tab=pass" class="<?php echo $tab === 'pass' ? 'on' : ''; ?>">修改密码</a>
            <a href="<?php echo $self; ?>?tab=safe" class="<?php echo $tab === 'safe' ? 'on' : ''; ?>">安全</a>
        </div>

        <?php if ($tab === 'home'): ?>
            <h2>运行概览</h2>
            <table class="adm-list">
                <tr><th width="30%">项目</th><th>数值</th></tr>
                <tr><td>页面访问量</td><td><?php echo intval($stats['pv']); ?></td></tr>
                <tr><td>下载次数</td><td><?php echo intval($stats['downloads']); ?></td></tr>
                <tr><td>安装包数量</td><td><?php echo count($versions); ?></td></tr>
                <tr><td>更新日志条目</td><td><?php echo count($changelog); ?></td></tr>
                <tr><td>PHP 版本</td><td><?php echo PHP_VERSION; ?></td></tr>
                <tr><td>数据目录可写</td><td><?php echo is_writable(OBR_DATA) ? '是' : '<b style="color:#900">否</b>'; ?></td></tr>
                <tr><td>files 目录可写</td><td><?php echo is_writable(OBR_FILES) ? '是' : '<b style="color:#900">否</b>'; ?></td></tr>
                <tr><td>PHP 上传上限</td><td><?php echo h(ini_get('upload_max_filesize')); ?>
                    （post_max_size: <?php echo h(ini_get('post_max_size')); ?>）</td></tr>
            </table>
            <h2>快捷操作</h2>
            <ul>
                <li><a href="<?php echo $self; ?>?tab=version">上传新的安装包</a></li>
                <li><a href="<?php echo $self; ?>?tab=log">添加更新日志</a></li>
                <li><a href="index.php" target="_blank">查看站点首页</a></li>
            </ul>

        <?php elseif ($tab === 'site'): ?>
            <h2>站点内容</h2>
            <form method="post" action="<?php echo $self; ?>?tab=site">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field"><label>站点标题</label>
                    <input class="txt" type="text" name="title" value="<?php echo h($logs['title']); ?>" /></div>
                <div class="field"><label>副标题</label>
                    <input class="txt" type="text" name="subtitle" value="<?php echo h($logs['subtitle']); ?>" /></div>
                <div class="field"><label>SEO 关键词（逗号分隔）</label>
                    <input class="txt" type="text" name="keywords" value="<?php echo h($logs['keywords']); ?>" /></div>
                <div class="field"><label>SEO 描述</label>
                    <textarea name="description"><?php echo h($logs['description']); ?></textarea></div>
                <div class="field"><label>首页简介</label>
                    <textarea name="intro"><?php echo h($logs['intro']); ?></textarea></div>
                <div class="field"><label>默认公告（未单独设置公告时显示）</label>
                    <textarea name="announce"><?php echo h($logs['announce']); ?></textarea></div>
                <div class="field"><label>标签（逗号分隔）</label>
                    <input class="txt" type="text" name="tags" value="<?php echo h($logs['tags']); ?>" /></div>
                <div class="field"><label>友情链接（每行：名称|网址）</label>
                    <textarea name="links"><?php
                        $lines = array();
                        if (!empty($logs['links']) && is_array($logs['links'])) {
                            foreach ($logs['links'] as $l) {
                                $lines[] = $l['name'] . '|' . $l['url'];
                            }
                        }
                        echo h(implode("\n", $lines));
                    ?></textarea></div>
                <div class="field"><label>页脚声明</label>
                    <textarea name="footer"><?php echo h($logs['footer']); ?></textarea></div>
                <div class="field"><label>建站日期（YYYY-MM-DD）</label>
                    <input class="txt small" type="text" name="start_date" value="<?php echo h($logs['start_date']); ?>" /></div>
                <div class="field"><label>备案号（可留空）</label>
                    <input class="txt" type="text" name="icp" value="<?php echo h($logs['icp']); ?>" /></div>
                <div class="field"><input class="btn" type="submit" name="save_site" value="保存站点信息" /></div>
            </form>

            <h2>公告</h2>
            <form method="post" action="<?php echo $self; ?>?tab=site">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field"><label>当前公告（支持换行）</label>
                    <textarea name="announce_text" style="height:100px"><?php echo h($announce['text']); ?></textarea></div>
                <div class="field small">最后修改：<?php echo h($announce['date']); ?></div>
                <div class="field"><input class="btn" type="submit" name="save_announce" value="更新公告" /></div>
            </form>

        <?php elseif ($tab === 'notice'): ?>
<?php
$notices = obr_read_json(OBR_DATA . '/announcements.json');
if (!is_array($notices)) { $notices = array(); }
$editId = isset($_GET['nid']) ? intval($_GET['nid']) : 0;
$edit = null;
foreach ($notices as $an) { if (intval($an['id']) === $editId) { $edit = $an; break; } }
if ($edit === null) {
    $edit = array('id' => 0, 'title' => '', 'text' => '',
            'publish_at' => date('Y-m-d H:i'), 'expire_at' => '', 'hidden' => 0);
}
usort($notices, 'adm_cmp_notice');
?>
<h2>公告列表（新的在上面）</h2>
<p class="small">发布时间晚于当前时间 = 定时发布；过期时间留空 = 长期有效。</p>
<table class="adm-list">
<tr><th>ID</th><th>标题</th><th>发布时间</th><th>过期时间</th><th>状态</th><th>操作</th></tr>
<?php foreach ($notices as $an): ?>
<?php
$pub = isset($an['publish_at']) ? $an['publish_at'] : '';
$exp = isset($an['expire_at']) ? $an['expire_at'] : '';
$pt = $pub ? strtotime($pub) : 0;
$et = $exp ? strtotime($exp) : 0;
$state = '显示中';
if (!empty($an['hidden'])) { $state = '已隐藏'; }
elseif ($pt && $pt > time()) { $state = '定时发布'; }
elseif ($et && $et < time()) { $state = '已过期'; }
?>
<tr>
<td><?php echo intval($an['id']); ?></td>
<td><?php echo h($an['title']); ?></td>
<td><?php echo h($pub); ?></td>
<td><?php echo h($exp); ?></td>
<td><?php echo h($state); ?></td>
<td><a href="<?php echo $self; ?>?tab=notice&amp;nid=<?php echo intval($an['id']); ?>">编辑</a> |
<a href="<?php echo $self; ?>?tab=notice&amp;noticedel=<?php echo intval($an['id']); ?>"
 onclick="return confirm('delete?');">删除</a></td>
</tr>
<?php endforeach; ?>
</table>
<h2><?php echo $editId > 0 ? '编辑公告' : '新增公告'; ?></h2>
<form method="post" action="<?php echo $self; ?>?tab=notice">
<input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
<input type="hidden" name="notice_id" value="<?php echo intval($edit['id']); ?>" />
<div class="field"><label>标题</label><input class="txt" type="text" name="notice_title" value="<?php echo h($edit['title']); ?>" /></div>
<div class="field"><label>正文</label><textarea name="notice_text" style="height:120px"><?php echo h($edit['text']); ?></textarea></div>
<div class="field"><label>发布时间（如 2026-11-08 22:00，留空=立即）</label><input class="txt small" type="text" name="notice_publish" value="<?php echo h($edit['publish_at']); ?>" /></div>
<div class="field"><label>过期时间（留空=长期）</label><input class="txt small" type="text" name="notice_expire" value="<?php echo h($edit['expire_at']); ?>" /></div>
<div class="field"><label><input type="checkbox" name="notice_hidden" value="1" <?php echo !empty($edit['hidden']) ? 'checked' : ''; ?> /> 隐藏</label></div>
<div class="field"><input class="btn" type="submit" name="save_notice" value="保存公告" /></div>
</form>
<?php elseif ($tab === 'log'): ?>
            <h2>更新日志</h2>
            <table class="adm-list">
                <tr><th width="16%">版本</th><th width="8%">code</th><th width="14%">日期</th>
                    <th>内容条数</th><th width="18%">操作</th></tr>
                <?php foreach ($changelog as $i => $e): ?>
                <tr>
                    <td><?php echo h($e['version']); ?></td>
                    <td><?php echo intval($e['code']); ?></td>
                    <td><?php echo h($e['date']); ?></td>
                    <td><?php echo count(isset($e['items']) ? $e['items'] : array()); ?></td>
                    <td>
                        <a href="<?php echo $self; ?>?tab=log&amp;edit=<?php echo $i; ?>">编辑</a> |
                        <a href="<?php echo $self; ?>?tab=log&amp;logdel=<?php echo $i; ?>"
                           onclick="return confirm('确定删除这条更新日志？');">删除</a>
                    </td>
                </tr>
                <?php endforeach; ?>
            </table>

            <?php
            $editIdx = isset($_GET['edit']) ? intval($_GET['edit']) : -1;
            $e = ($editIdx >= 0 && isset($changelog[$editIdx]))
                    ? $changelog[$editIdx]
                    : array('version' => '', 'code' => 0, 'date' => date('Y-m-d'),
                            'items' => array(), 'known' => array());
            ?>
            <h2><?php echo $editIdx >= 0 ? '编辑更新日志' : '新增更新日志'; ?></h2>
            <form method="post" action="<?php echo $self; ?>?tab=log">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <input type="hidden" name="log_index" value="<?php echo $editIdx; ?>" />
                <div class="field"><label>版本号（如 1.0.5 beta）</label>
                    <input class="txt" type="text" name="log_version" value="<?php echo h($e['version']); ?>" /></div>
                <div class="field"><label>versionCode</label>
                    <input class="txt small" type="text" name="log_code" value="<?php echo intval($e['code']); ?>" /></div>
                <div class="field"><label>日期（YYYY-MM-DD）</label>
                    <input class="txt small" type="text" name="log_date" value="<?php echo h($e['date']); ?>" /></div>
                <div class="field"><label>更新内容（每行一条）</label>
                    <textarea name="log_items" style="height:140px"><?php
                        echo h(implode("\n", isset($e['items']) ? $e['items'] : array()));
                    ?></textarea></div>
                <div class="field"><label>已知问题（每行一条，可留空）</label>
                    <textarea name="log_known" style="height:80px"><?php
                        echo h(implode("\n", isset($e['known']) ? $e['known'] : array()));
                    ?></textarea></div>
                <div class="field"><input class="btn" type="submit" name="save_log" value="保存" /></div>
            </form>

        <?php elseif ($tab === 'version'): ?>
            <h2>安装包列表</h2>
            <table class="adm-list">
                <tr><th>ID</th><th>版本</th><th>code</th><th>ABI</th><th>日期</th>
                    <th>大小</th><th>下载</th><th>状态</th><th>操作</th></tr>
                <?php foreach ($versions as $v): ?>
                <tr>
                    <td><?php echo intval($v['id']); ?></td>
                    <td><?php echo h($v['version']); ?></td>
                    <td><?php echo intval($v['code']); ?></td>
                    <td><?php echo h($v['abi']); ?></td>
                    <td><?php echo h($v['date']); ?></td>
                    <td><?php echo obr_fmt_size(intval($v['size'])); ?></td>
                    <td><?php echo intval(isset($v['hits']) ? $v['hits'] : 0); ?></td>
                    <td><?php echo empty($v['hidden']) ? '正常' : '已隐藏'; ?>
                        <?php echo !empty($v['latest']) ? ' · 最新' : ''; ?></td>
                    <td>
                        <a href="<?php echo $self; ?>?vop=latest&amp;id=<?php echo intval($v['id']); ?>">设为最新</a> |
                        <a href="<?php echo $self; ?>?vop=hide&amp;id=<?php echo intval($v['id']); ?>">隐藏/显示</a> |
                        <a href="<?php echo $self; ?>?vop=delete&amp;id=<?php echo intval($v['id']); ?>"
                           onclick="return confirm('删除该安装包及文件？不可恢复。');">删除</a>
                    </td>
                </tr>
                <?php endforeach; ?>
            </table>

            <h2>登记 files/ 中已有的安装包</h2>
            <p class="small">如果虚拟主机限制了 PHP 上传大小（例如 2MB），可以先用 FTP 把 APK 传到
               <code>files/</code>，再在这里登记。</p>
            <form method="post" action="<?php echo $self; ?>?tab=version">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field"><label>files/ 中的文件名（含 .apk）</label>
                    <input class="txt" type="text" name="file_name" value="" /></div>
                <div class="field"><label>版本号</label>
                    <input class="txt small" type="text" name="version" value="" /></div>
                <div class="field"><label>versionCode</label>
                    <input class="txt small" type="text" name="code" value="" /></div>
                <div class="field"><label>ABI</label>
                    <select name="abi">
                        <option value="full">全架构（full）</option>
                        <option value="armeabi-v7a">V7 精简（armeabi-v7a）</option>
                    </select></div>
                <div class="field"><label>说明</label>
                    <textarea name="notes"></textarea></div>
                <div class="field"><input class="btn" type="submit" name="do_register" value="登记" /></div>
            </form>

            <h2>上传新安装包</h2>
            <form method="post" action="<?php echo $self; ?>?tab=version"
                  enctype="multipart/form-data">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field"><label>APK 文件（最大 120MB）</label>
                    <input type="file" name="apk" /></div>
                <div class="field"><label>版本号</label>
                    <input class="txt small" type="text" name="version" value="" /></div>
                <div class="field"><label>versionCode</label>
                    <input class="txt small" type="text" name="code" value="" /></div>
                <div class="field"><label>ABI</label>
                    <select name="abi">
                        <option value="full">全架构（full）</option>
                        <option value="armeabi-v7a">V7 精简（armeabi-v7a）</option>
                    </select></div>
                <div class="field"><label>说明（可留空）</label>
                    <textarea name="notes"></textarea></div>
                <div class="field"><input class="btn" type="submit" name="do_upload" value="上传" /></div>
            </form>

        <?php elseif ($tab === 'pass'): ?>
            <h2>修改密码</h2>
            <form method="post" action="<?php echo $self; ?>?tab=pass">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field"><label>原密码</label>
                    <input class="txt small" type="password" name="old_pass" /></div>
                <div class="field"><label>新密码（至少 8 位）</label>
                    <input class="txt small" type="password" name="new_pass" /></div>
                <div class="field"><label>再输一次</label>
                    <input class="txt small" type="password" name="new_pass2" /></div>
                <div class="field"><input class="btn" type="submit" name="change_pass" value="修改密码" /></div>
            </form>

            <h2>自动化 API</h2>
            <p class="small">用于脚本上传版本 / 写更新日志，POST 到本文件并带 api=1 与 key。</p>
            <form method="post" action="<?php echo $self; ?>?tab=pass">
                <input type="hidden" name="csrf" value="<?php echo h($csrf); ?>" />
                <div class="field">当前密钥：
                    <code><?php echo h(isset($ADMIN['api_key']) ? $ADMIN['api_key'] : '（未生成）'); ?></code></div>
                <div class="field"><input class="btn" type="submit" name="gen_api" value="生成/重置密钥" /></div>
            </form>

        <?php elseif ($tab === 'safe'): ?>
            <h2>IP 封禁</h2>
            <form method="post" action="<?php echo $self; ?>?tab=safe">
                <div class="field"><label>要封禁的 IP</label>
                    <input class="txt small" type="text" name="ban_ip" /></div>
                <div class="field"><input class="btn" type="submit" value="封禁" /></div>
            </form>
            <table class="adm-list">
                <tr><th>IP</th><th width="20%">操作</th></tr>
                <?php foreach ($bans as $b): ?>
                <tr><td><?php echo h($b); ?></td>
                    <td><a href="<?php echo $self; ?>?tab=safe&amp;unban=<?php echo h($b); ?>">解除</a></td></tr>
                <?php endforeach; ?>
            </table>

            <h2>最近日志</h2>
            <pre style="background:#f7f9fb;border:1px solid #c9d3dd;padding:8px;
                        max-height:260px;overflow:auto;font-size:11px;"><?php
                $logFile = OBR_DATA . '/logs/site-' . date('Ym') . '.log';
                if (is_file($logFile)) {
                    $all = file($logFile);
                    $tail = array_slice($all, -60);
                    echo h(implode('', $tail));
                } else {
                    echo '暂无日志';
                }
            ?></pre>
        <?php endif; ?>
    </div>
</div>
<?php endif; ?>
</body>
</html>
