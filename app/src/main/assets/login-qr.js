/* School QR login: keep the nonce in this page and never treat it as a saved password. */
(function (action) {
    'use strict';
    if (location.origin !== 'https://jwxt.jhun.edu.cn') return {state: 'unsupported'};
    if (location.pathname === '/frame/homes.action') return {state: 'success'};
    if (location.pathname !== '/cas/login.action') return {state: 'unsupported'};
    var key = '__sanjiaohuQrLogin', session = window[key];
    function stop() {
        if (!session) return;
        session.active = false;
        clearTimeout(session.timer);
        if (session.request) session.request.abort();
    }
    if (action === 'stop') {stop(); return {state: 'stopped'};}
    if (action === 'status') {
        if (!session) return {state: 'waiting'};
        if (session.active && Date.now() >= session.expiresAt) {stop(); session.state = 'expired';}
        return {state: session.state, message: session.message || ''};
    }
    if (action !== 'start') return {state: 'unsupported'};
    var table = document.querySelector('#qrCode table'), nonce = window.uuid16;
    if (!table || !table.rows.length || typeof nonce !== 'string' || !/^smdljwxt[0-9A-V]{16}$/.test(nonce)) return {state: 'waiting'};
    if (session) return {state: session.state, image: session.image, message: session.message || ''};
    // The school renders a table. Copy its cells, with a four-module quiet zone, into a PNG.
    var rows = table.rows, count = rows.length, unit = 8;
    if (count < 21 || count > 177) return {state: 'unsupported'};
    var canvas = document.createElement('canvas');
    canvas.width = canvas.height = (count + 8) * unit;
    var context = canvas.getContext('2d');
    context.fillStyle = '#fff'; context.fillRect(0, 0, canvas.width, canvas.height);
    for (var y = 0; y < count; y++) {
        if (rows[y].cells.length !== count) return {state: 'unsupported'};
        for (var x = 0; x < count; x++) {
            context.fillStyle = getComputedStyle(rows[y].cells[x]).backgroundColor;
            context.fillRect((x + 4) * unit, (y + 4) * unit, unit, unit);
        }
    }
    session = window[key] = {active: true, state: 'waiting', image: canvas.toDataURL('image/png'), expiresAt: Date.now() + 120000, queryFailures: 0};
    window.scanflag = false; // Only this adapter polls; the school's tab timer is not started.
    function current() {
        return window[key] === session && session.active && Date.now() < session.expiresAt;
    }
    function fail(message) {stop(); session.state = 'error'; session.message = message;}
    function post(path, body, callback, verifying) {
        if (!current()) {stop(); session.state = 'expired'; return;}
        var request = new XMLHttpRequest(), settled = false; session.request = request;
        request.open('POST', path, true); request.timeout = verifying ? 45000 : 12000;
        request.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        // Match the school's jQuery AJAX marker; WebView otherwise can send the app package here.
        request.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        request.setRequestHeader('Accept', verifying ? 'text/plain, */*; q=0.01' : '*/*');
        request.onload = function () {
            if (settled || !current()) return; settled = true;
            if (request.status !== 200) {failed('HTTP ' + request.status); return;}
            callback(request.responseText);
        };
        function failed(reason) {
            if (!current()) return;
            if (!verifying && ++session.queryFailures <= 2) {
                session.message = '扫码连接暂时不稳定，正在重新确认…';
                session.timer = setTimeout(poll, 1500); return;
            }
            fail((verifying ? '扫码已确认，但教务登录未完成' : '查询扫码状态失败') + '（' + reason + '），请刷新重试。');
        }
        request.onerror = request.ontimeout = function (event) {
            if (settled || !current()) return; settled = true;
            failed(event && event.type === 'timeout' ? '请求超时' : '连接中断');
        };
        request.send(body);
    }
    function poll() {
        post('/frame/LoginBar.jsp', 'operate=query&qrCode=' + encodeURIComponent(nonce), function (account) {
            session.queryFailures = 0; session.message = '';
            account = account.trim();
            if (!account) {session.timer = setTimeout(poll, 1500); return;}
            if (account.length > 128 || /[<>\r\n]/.test(account)) {fail('学校返回的扫码结果异常，请刷新重试。'); return;}
            session.state = 'verifying';
            // A scan confirmed near the two-minute limit still gets time to complete the logon request.
            session.expiresAt = Date.now() + 60000;
            // Exact fields used by the school's doBarLogin(); the nonce is a one-time credential.
            post('/cas/logon.action', 'username=' + encodeURIComponent(account) + '&password=' + encodeURIComponent(nonce) + '&loginmethod=xiqueer', function (response) {
                try {
                    var result = JSON.parse(response);
                    if (String(result.status) !== '200') {fail(String(result.message || '扫码登录未完成，请刷新重试。').slice(0, 240)); return;}
                    var target = new URL(result.result, location.href);
                    if (target.origin !== location.origin || target.pathname !== '/frame/homes.action') {fail('学校登录回调发生变化，请使用账号登录。'); return;}
                    stop(); session.state = 'success'; location.assign(target.href);
                } catch (e) {fail('学校返回的登录结果无法读取，请刷新重试。');}
            }, true);
        });
    }
    session.timer = setTimeout(poll, 1500);
    return {state: 'waiting', image: session.image};
})
