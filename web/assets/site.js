/*
 * OldBiliRetro 官网脚本
 * 兼容 Android 1.6 / WinCE IE Mobile：只用 ES3 语法与 document.getElementById
 */
var OBRTimer = null;
var OBRLeft = 15;
var OBRTarget = '';

function obrById(id) {
    if (document.getElementById) {
        return document.getElementById(id);
    }
    return document.all ? document.all[id] : null;
}

function obrSetText(id, txt) {
    var el = obrById(id);
    if (el) {
        if (typeof el.innerText != 'undefined') {
            el.innerText = txt;
        } else {
            el.innerHTML = txt;
        }
    }
}

function obrStartCountdown(seconds, url) {
    OBRLeft = seconds;
    OBRTarget = url;
    obrTick();
}

function obrTick() {
    obrSetText('wait_num', '' + OBRLeft);
    var bar = obrById('wait_bar_inner');
    if (bar) {
        try {
            bar.style.width = (Math.round((15 - OBRLeft) / 15 * 100)) + '%';
        } catch (e) {
        }
    }
    if (OBRLeft <= 0) {
        obrSetText('wait_state', '正在开始下载，请稍候……');
        var go = obrById('wait_go');
        if (go) {
            go.style.display = '';
        }
        try {
            window.location.href = OBRTarget;
        } catch (e) {
        }
        return;
    }
    OBRLeft = OBRLeft - 1;
    OBRTimer = setTimeout('obrTick()', 1000);
}

function obrConfirmGo(msg, url) {
    if (window.confirm(msg)) {
        window.location.href = url;
    }
    return false;
}

/* 后台管理用：简易确认 */
function obrAdminConfirm(form, msg) {
    if (window.confirm(msg)) {
        return true;
    }
    return false;
}
