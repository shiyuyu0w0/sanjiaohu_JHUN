(function(action, data) {
    'use strict';
    // This adapter never receives credentials and only runs on the exact school form.
    if (location.origin !== 'https://h5cloud.17wanxiao.com:18443' || location.pathname !== '/CloudPayment/bill/selectPayProject.do') return {ready:false};
    function field(id) { return document.getElementById(id); }
    var project=field('payProId'), school=field('hostCustomerCode'), account=field('thirdAccount');
    var amount=document.querySelector('.payAmt'), button=field('self-pay');
    var ready=!!(project && project.value==='7033' && school && school.value==='1862' && account && amount && button && typeof window.roomNum==='string' && field('txcode') && field('txcode').value==='2' && field('interurl') && field('interurl').value==='substituted_pay');
    var integerOnly=!!(field('amountToInt') && field('amountToInt').value==='1');
    if(action==='inspect')return {ready:ready,integerOnly:integerOnly};
    if(action!=='pay'||!ready||!data)return {submitted:false,message:'学校缴费页面未就绪，请重新登录后重试'};
    if(window.__sanjiaohuPaymentSubmitted)return {submitted:false,message:'本次支付已发起，请勿重复提交'};
    if(!/^[A-Za-z0-9-]{1,160}$/.test(data.room)||typeof data.description!=='string'||data.description.length>600||!/^[0-9]{1,5}\.[0-9]{2}$/.test(data.amount))return {submitted:false,message:'房间或金额无效'};
    var money=Number(data.amount);
    if(money<0.01||money>99999||(integerOnly&&(money<10||money>200||money%1!==0)))return {submitted:false,message:'金额不符合学校要求'};
    account.value=data.room;
    window.roomNum=data.description;
    if(field('payaccname'))field('payaccname').value=data.description;
    if(field('roomNum'))field('roomNum').textContent=data.description;
    amount.value=data.amount;
    amount.dispatchEvent(new Event('input',{bubbles:true}));
    amount.dispatchEvent(new Event('change',{bubbles:true}));
    // The current official SDK accepts callPaywayid + callAccountid. Choose only
    // one explicitly named Alipay option returned by the school for THIS order.
    // If the school's response changes, retain its own payment-method chooser.
    if(window.epaySdk && typeof window.epaySdk.callPay==='function') {
        var original=window.epaySdk.callPay;
        window.epaySdk.callPay=function(payload,source) {
            window.epaySdk.callPay=original;
            var prepared=payload;
            try {
                var info=JSON.parse(typeof payload==='string'?payload:JSON.stringify(payload));
                var order=typeof info.orderInfo==='string'?JSON.parse(info.orderInfo):info.orderInfo;
                var list=info.projectPaywayList||(order&&order.projectPaywayList)||[];
                if(typeof list==='string')list=JSON.parse(list);
                var alipay=Array.isArray(list)?list.filter(function(p){return typeof p.paywayname==='string'&&p.paywayname.indexOf('支付宝')!==-1&&!/代扣|签约|自动/.test(p.paywayname)&&p.paywayid;}):[];
                if(alipay.length===1){info.callPaywayid=alipay[0].paywayid;info.callAccountid=alipay[0].accountid;prepared=info;}
            }catch(ignored){}
            return original.call(window.epaySdk,prepared,source);
        };
    }
    window.__sanjiaohuPaymentSubmitted=true;
    // Keep the school's validation, confirmation, current cashier SDK and session.
    button.click();
    return {submitted:true};
})
