/* Restyle the official order reader; its data, pagination and status text remain authoritative. */
(function (theme) {
    'use strict';
    if (location.origin !== 'https://h5cloud.17wanxiao.com:18443' ||
        !/^\/CloudPayment\/bill\/(record|detail)\.do$/.test(location.pathname)) return false;
    if (!document.querySelector('.billRecord, .billDetail')) return false;
    var keys = ['surface', 'card', 'text', 'muted', 'accent', 'outline'];
    if (!theme || keys.some(function (key) {return !/^#[0-9a-fA-F]{6}$/.test(theme[key] || '');})) return false;
    var style = document.getElementById('sanjiaohu-order-theme');
    if (!style) {style = document.createElement('style'); style.id = 'sanjiaohu-order-theme'; document.head.appendChild(style);}
    style.textContent =
        'html,body{background:'+theme.surface+'!important;color:'+theme.text+'!important;font-family:system-ui,sans-serif!important;margin:0!important;}' +
        'body{overflow:auto!important;} .views{background:transparent!important;width:100%!important;min-height:100vh;}' +
        '.views>header{display:none!important;}' +
        '.page-content{position:static!important;top:auto!important;margin:0!important;padding:16px 18px 28px!important;background:transparent!important;box-sizing:border-box!important;}' +
        '#billContent,.bill{background:transparent!important;}' +
        '.bill .month{font-size:14px!important;line-height:22px!important;color:'+theme.muted+'!important;padding:12px 2px 10px!important;}' +
        '.bill ul{margin:0!important;padding:0!important;background:transparent!important;}' +
        '.bill li.two{display:flex!important;align-items:center!important;position:relative!important;min-height:76px;height:auto!important;padding:16px!important;margin:0 0 10px!important;border:0!important;border-radius:18px!important;background:'+theme.card+'!important;box-sizing:border-box!important;}' +
        '.bill .listInfo{display:flex!important;align-items:center!important;flex:1;min-width:0;width:auto!important;}' +
        '.bill .time{float:none!important;flex:0 0 44px!important;width:44px!important;margin:0 14px 0 0!important;color:'+theme.muted+'!important;}' +
        '.bill .time p{font-size:12px!important;line-height:21px!important;color:'+theme.muted+'!important;}' +
        '.bill .listInfo>img{display:none!important;}' +
        '.bill .data{float:none!important;flex:1;min-width:0;width:auto!important;margin:0!important;padding:0!important;}' +
        '.bill .data p{font-size:23px!important;line-height:30px!important;font-weight:600!important;color:'+theme.text+'!important;}' +
        '.bill .data p:empty{display:none!important;}' +
        '.bill .data .type{font-size:12px!important;line-height:20px!important;font-weight:400!important;color:'+theme.muted+'!important;overflow-wrap:anywhere!important;white-space:normal!important;}' +
        '.bill .icon.go{position:static!important;flex:none;width:7px!important;height:7px!important;margin-left:12px!important;background:none!important;border:0!important;border-top:2px solid '+theme.accent+'!important;border-right:2px solid '+theme.accent+'!important;transform:rotate(45deg);}' +
        '.dropno,.dropload-down,.dropload-load,.dropload-noData{font-size:12px!important;color:'+theme.muted+'!important;}' +
        '.billDetail .orders,.billDetail .order{background:transparent!important;margin:0!important;padding:0!important;border:0!important;}' +
        '.billDetail .order-detail,.billDetail .order-status{background:'+theme.card+'!important;border:0!important;border-radius:20px!important;padding:18px!important;margin:0 0 16px!important;}' +
        '.billDetail p{font-size:14px!important;line-height:23px!important;color:'+theme.text+'!important;}' +
        '.billDetail p.title{font-size:18px!important;font-weight:600!important;margin:0 0 14px!important;padding:0!important;}' +
        '.billDetail .order-detail>p:not(.title){gap:12px!important;margin:9px 0!important;align-items:flex-start!important;}' +
        '.billDetail .order-detail>p>span:first-child{color:'+theme.muted+'!important;}' +
        '.billDetail .order-detail>p>span:last-child{min-width:0!important;overflow-wrap:anywhere!important;}' +
        '.billDetail .order-status-list{margin:0!important;padding:0 0 20px 22px!important;border-color:'+theme.outline+'!important;}' +
        '.billDetail .order-status-list p{margin:0 0 4px!important;color:'+theme.muted+'!important;}' +
        '.billDetail .order-status-list p:first-child{font-size:15px!important;font-weight:600!important;color:'+theme.accent+'!important;}' +
        '.billDetail .order-status-list .time{font-size:12px!important;}' +
        '.billDetail .cricle-pass{background:'+theme.accent+'!important;border-color:'+theme.card+'!important;}' +
        '.billDetail a{color:'+theme.accent+'!important;}' +
        '.billDetail>a.btn{display:none!important;}';
    return true;
})
