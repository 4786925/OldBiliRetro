<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');
obr_page_top('常见问题', 'faq');

$qa = array(
    array('我的手机是安卓 2.3，能装吗？',
          '可以。请下载 <b>V7 精简版</b>（约 3MB）。安装前建议先卸载旧版本，' .
          '因为老机器的 /data 分区通常只有 100~200MB，覆盖安装可能提示空间不足。'),
    array('安装时提示「解析包错误」或「安装失败」怎么办？',
          '通常是下载不完整或存储空间不足。请确认文件大小与下载页一致，' .
          '清理一些应用后再试；实在不行先卸载旧版，再安装新版。'),
    array('为什么有的视频评论只有几条？',
          '未登录时 B 站接口只返回少量评论。登录后即可看到完整评论。'),
    array('评论区弹 412 / 请求过于频繁？',
          '这是 B 站的风控。客户端已做了节流：连续翻页会暂停，' .
          '底部出现「被B站限流，点击继续加载」，等待一会儿再点即可。'),
    array('消息通知很慢？',
          '当前版本采用 15 分钟一次的后台检查，并且打开应用时会立即检查一次，' .
          '所以不是实时推送，属于已知问题，后续会继续完善。'),
    array('下载的视频保存在哪里？',
          '默认保存在系统「下载」文件夹下的 OldBiliRetro 目录，' .
          '可在「设置 → 下载设置 → 下载目录」里修改，也支持按 UP 主建文件夹。'),
    array('这个网站和 App 是官方的吗？',
          '不是。OldBiliRetro 是第三方开源项目，与哔哩哔哩官方没有任何关联。' .
          '详见 <a href="about.php">关于与声明</a>。'),
    array('可以用 WinCE / 塞班手机访问这个网站吗？',
          '可以浏览和下载页面，但 OldBiliRetro 本身是 Android 应用，' .
          '这些系统无法安装 APK。'),
    array('源码在哪里？',
          'GitHub：<a href="https://github.com/4786925/OldBiliRetro" target="_blank">' .
          'github.com/4786925/OldBiliRetro</a>，协议为 GPL-3.0。'),
);

?>
<div class="box">
    <div class="box-title">常见问题 <span>安装 / 使用 / 报错</span></div>
    <div class="box-body">
        <?php foreach ($qa as $i => $item): ?>
            <div class="version-item">
                <div class="v-name" style="font-size:13px;color:#22608a;">
                    <?php echo ($i + 1) . '. ' . $item[0]; ?>
                </div>
                <div class="v-body"><?php echo $item[1]; ?></div>
            </div>
        <?php endforeach; ?>
    </div>
</div>

<div class="box">
    <div class="box-title">还是解决不了？</div>
    <div class="box-body">
        <p>请到 GitHub 提交 Issue，并附上：手机型号、Android 版本、App 版本、复现步骤。</p>
        <p><a class="btn btn-gray" href="https://github.com/4786925/OldBiliRetro/issues"
              target="_blank">前往 GitHub Issues</a></p>
    </div>
</div>
<?php
obr_page_bottom();
