<?php
require dirname(__FILE__) . '/inc/bootstrap.php';
require dirname(__FILE__) . '/inc/layout.php';

obr_bump_stat('pv');
obr_page_top('关于与声明', 'about');
?>
<div class="box">
    <div class="box-title">关于 OldBiliRetro</div>
    <div class="box-body">
        <p><?php echo nl2br(h($SITE['intro'])); ?></p>
        <p>
            项目地址：<a href="https://github.com/4786925/OldBiliRetro" target="_blank">
            https://github.com/4786925/OldBiliRetro</a><br />
            开源协议：<b>GPL-3.0</b><br />
            开发方式：<b>100% AI 开发</b>（DeepSeek 模型 + DeepSeek Harness 智能体框架），
            人类仅负责提出需求、实机测试与最终决策。
        </p>
    </div>
</div>

<div class="box">
    <div class="box-title">重要声明 <span>请先阅读</span></div>
    <div class="box-body">
        <ol>
            <li><b>本项目完全由 AI 开发。</b>全部代码、图标、文档、编译脚本均由 AI 生成；
                人类仅负责提出需求、实机测试与最终决策。</li>
            <li><b>本项目与哔哩哔哩（bilibili，上海宽娱数码科技有限公司）没有任何关联</b>，
                不是官方客户端，也未获得官方授权或认可。</li>
            <li>应用内的视频、弹幕、评论、番剧等内容，<b>版权均归原作者及哔哩哔哩所有</b>；
                本应用只是访问官方公开接口的第三方客户端。</li>
            <li>请勿将本项目用于<b>商业用途、批量抓取、绕过付费/会员限制、二次分发付费内容</b>等行为。</li>
            <li>使用第三方客户端存在<b>账号被风控、限流甚至封禁</b>的风险，请自行评估并承担。</li>
            <li>本项目按「现状」提供，不对可用性、合法性作任何明示或默示担保。</li>
        </ol>
    </div>
</div>

<div class="box" id="legal">
    <div class="box-title">法律信息</div>
    <div class="box-body">
        <p>本软件为第三方开源客户端，与哔哩哔哩官方无任何关联。开发者仅提供播放/浏览工具，
           不存储、不传播任何视频内容，所有内容版权归原权利人所有。
           用户应遵守下列法律法规及平台协议，因使用本软件产生的任何后果由用户自行承担。</p>
        <h3>涉及的主要法律法规</h3>
        <ul>
            <li>《中华人民共和国网络安全法》</li>
            <li>《中华人民共和国个人信息保护法》</li>
            <li>《中华人民共和国数据安全法》</li>
            <li>《中华人民共和国著作权法》</li>
            <li>《计算机软件保护条例》</li>
            <li>《信息网络传播权保护条例》</li>
            <li>《中华人民共和国民法典》（人格权编、侵权责任编）</li>
            <li>《中华人民共和国未成年人保护法》</li>
            <li>《网络信息内容生态治理规定》</li>
            <li>《互联网信息服务管理办法》</li>
            <li>《移动互联网应用程序信息服务管理规定》</li>
            <li>《App 违法违规收集使用个人信息行为认定方法》</li>
        </ul>
        <h3>免责声明</h3>
        <ol>
            <li>本软件不以任何形式提供或传播受版权保护的内容；</li>
            <li>所有视频、弹幕、评论等数据均来自哔哩哔哩公开接口，版权归原作者所有；</li>
            <li>使用者需自行确保其行为符合当地法律法规；</li>
            <li>本软件按「现状」提供，不对可用性、合法性作任何明示或默示担保；</li>
            <li>如权利人认为存在侵权，请通过 GitHub Issue 告知，开发者将及时处理。</li>
        </ol>
    </div>
</div>

<div class="box">
    <div class="box-title">第三方组件</div>
    <div class="box-body">
        <ul>
            <li>IJKPlayer（LGPL-2.1）—— 自编译播放器内核</li>
            <li>DanmakuFlameMaster（Apache-2.0）—— 弹幕渲染</li>
            <li>ZXing core（Apache-2.0）—— 二维码登录</li>
            <li>Android SDK / Gradle 等构建工具</li>
        </ul>
        <p class="small">完整许可见仓库中的 THIRD_PARTY.md。</p>
    </div>
</div>
<?php
obr_page_bottom();
