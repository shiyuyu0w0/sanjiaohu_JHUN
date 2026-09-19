const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync('app/src/main/assets/electricity-payment.js','utf8');
let checks=0;
function setup(overrides={}) {
    let clicks=0,received;
    const fields={payProId:{value:'7033'},hostCustomerCode:{value:'1862'},thirdAccount:{value:''},txcode:{value:'2'},interurl:{value:'substituted_pay'},amountToInt:{value:'0'},payaccname:{value:''},roomNum:{textContent:''},'self-pay':{click(){clicks++;}}};
    const amount={value:'',dispatchEvent(){}};
    const context={location:{origin:'https://h5cloud.17wanxiao.com:18443',pathname:'/CloudPayment/bill/selectPayProject.do'},document:{getElementById:id=>fields[id],querySelector:s=>s==='.payAmt'?amount:null},Event:function(){},window:{roomNum:'',epaySdk:{callPay(payload){received=payload;}}}};
    Object.assign(context,overrides);const adapter=vm.runInNewContext(script,context);
    return {fields,amount,context,call:(a,b)=>JSON.parse(JSON.stringify(adapter(a,b))),clicks:()=>clicks,received:()=>received};
}
const payload={room:'1-7--31-205',description:'合成样本楼--2层（空调）-205',amount:'20.00'};
function check(f){f();checks++;}
check(()=>{const s=setup();assert.equal(s.call('inspect').ready,true);assert.equal(s.clicks(),0);assert.equal(s.fields.thirdAccount.value,'');});
check(()=>{const s=setup();assert.equal(s.call('pay',payload).submitted,true);assert.equal(s.clicks(),1);assert.equal(s.fields.thirdAccount.value,payload.room);assert.equal(s.amount.value,'20.00');assert.equal(s.context.window.roomNum,payload.description);assert.equal(s.call('pay',payload).submitted,false);assert.equal(s.clicks(),1);});
for(const origin of ['http://h5cloud.17wanxiao.com:18443','https://h5cloud.17wanxiao.com','https://evil.test','https://h5cloud.17wanxiao.com.evil.test:18443'])check(()=>{const s=setup({location:{origin,pathname:'/CloudPayment/bill/selectPayProject.do'}});assert.equal(s.call('pay',payload).ready,false);assert.equal(s.clicks(),0);});
for(const id of ['payProId','hostCustomerCode','txcode','interurl'])check(()=>{const s=setup();s.fields[id].value='wrong';assert.equal(s.call('pay',payload).submitted,false);assert.equal(s.clicks(),0);});
for(const value of ['0.00','-1.00','0.001','100000.00','1e2','NaN','1.00;'])check(()=>{const s=setup();assert.equal(s.call('pay',{...payload,amount:value}).submitted,false);assert.equal(s.clicks(),0);});
check(()=>{const s=setup();s.fields.amountToInt.value='1';assert.equal(s.call('inspect').integerOnly,true);assert.equal(s.call('pay',{...payload,amount:'20.01'}).submitted,false);assert.equal(s.call('pay',payload).submitted,true);});
check(()=>{const s=setup();assert.equal(s.call('pay',{...payload,room:'abc\";alert(1)'}).submitted,false);assert.equal(s.clicks(),0);});
check(()=>{const s=setup();s.call('pay',payload);const p={orderInfo:{journo:'synthetic'},projectPaywayList:[{paywayname:'支付宝',paywayid:'fake-ali',accountid:'fake-account'},{paywayname:'银行卡',paywayid:'fake-bank'}]};s.context.window.epaySdk.callPay(JSON.stringify(p));assert.equal(s.received().callPaywayid,'fake-ali');assert.equal(s.received().callAccountid,'fake-account');assert.equal(s.received().orderInfo.journo,'synthetic');});
for(const list of [[],[{paywayname:'支付宝代扣',paywayid:'fake'}],[{paywayname:'支付宝A',paywayid:'a'},{paywayname:'支付宝B',paywayid:'b'}]])check(()=>{const s=setup();s.call('pay',payload);const p=JSON.stringify({orderInfo:{journo:'synthetic'},projectPaywayList:list});s.context.window.epaySdk.callPay(p);assert.equal(s.received(),p);});
console.log(`Electricity payment: ${checks} checks passed`);
