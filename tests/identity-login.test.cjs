const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/identity-login.js'),'utf8');
const LOGIN='https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fehall.jhun.edu.cn%2Flogin%3Fservice%3Dhttp%3A%2F%2Fehall.jhun.edu.cn%2Fnew%2Findex.html';
function run(options={}){
  let clicks=0;class Input{constructor(name,type){this.name=name;this.id=name;this.type=type;this.disabled=false;this.events=[];this._value='';}getClientRects(){return this.hidden?[]:[{}];}getAttribute(){return '';}dispatchEvent(event){this.events.push(event.type);}}
  Object.defineProperty(Input.prototype,'value',{get(){return this._value;},set(value){this._value=value;}});
  const user=new Input(options.otherName?'schoolAccount':'username','text'),pass=new Input('password','password'),captcha=new Input('captchaResponse','text'),remember=new Input('rememberMe','checkbox');remember.checked=true;
  const encrypted=new Input('password','text');encrypted.id='passwordEncrypt';encrypted.hidden=true;encrypted.value=options.noEncryption?'':options.plaintextEncryption?'synthetic-plaintext':'school-managed';if(options.actualForm)pass.name='';
  const button=new Input('submit','submit');button.innerText='登 录';button.click=()=>{clicks++;if(options.noSubmit)return;form.schoolReceived=pass.value;if(options.inPlace)pass.value='school-transformed';const event={defaultPrevented:!!options.rejected,preventDefault(){this.defaultPrevented=true;}};if(options.plaintextEncryption)encrypted.value=pass.value;if(options.mutateAction)form.action=options.mutateAction;for(const listener of [...form.listeners])listener(event);form.posted=!event.defaultPrevented;};
  const form={listeners:[],method:options.method||'post',addEventListener(type,listener){this.listeners.push(listener);},removeEventListener(type,listener){this.listeners=this.listeners.filter(l=>l!==listener);},action:options.action||LOGIN,querySelectorAll(selector){if(selector==='input')return options.captcha?[encrypted,user,pass,captcha].filter(e=>!options.missingEncrypted||e!==encrypted):[encrypted,user,pass].filter(e=>!options.missingEncrypted||e!==encrypted);if(selector==='button,input[type="submit"],input[type="button"]')return [button];if(selector==='input[type="checkbox"]')return [remember];return [];}};user.form=pass.form=form;
  const current=new URL(options.url||LOGIN);
  const context={location:{origin:current.origin,pathname:current.pathname,href:current.href},URL,HTMLInputElement:Input,Event:class{constructor(type){this.type=type;}},getComputedStyle:()=>({visibility:'visible'}),document:{readyState:options.loading?'loading':'interactive',querySelectorAll(){return options.missing?[]:[pass];}}};
  const secret='quote"\\\n);throw new Error("must stay data");';
  const args=[options.prepare?'prepare':options.inspect?'inspect':'submit','synthetic-student',secret,options.answer||''];
  const result=vm.runInNewContext(code+'('+args.map(x=>JSON.stringify(x)).join(',')+')',context);
  return {result,user,pass,remember,clicks,secret,captcha,encrypted,form};
}
let checks=0;function check(fn){fn();checks++;}
for(const options of [{},{actualForm:true},{otherName:true},{captcha:true,answer:'ABCD'}]){const r=run(options);check(()=>assert.equal(r.result.state,'submitted'));check(()=>assert.equal(r.clicks,1));check(()=>assert.equal(r.form.posted,true));check(()=>assert.equal(r.encrypted.value,'school-managed'));check(()=>assert.equal(r.user.value,'synthetic-student'));check(()=>assert.equal(r.pass.value,r.secret));check(()=>assert.equal(r.remember.checked,false));check(()=>assert.deepEqual(r.pass.events,['input','keyup','change','blur']));}
for(const options of [
  {url:'http://authserver.jhun.edu.cn:8080/authserver/login'},
  {url:'https://authserver.jhun.edu.cn.evil.invalid/authserver/login'},
  {url:'https://authserver.jhun.edu.cn/authserver/resetPassword'},
  {url:'https://authserver.jhun.edu.cn/authserver/login?service=https%3A%2F%2Fevil.invalid'},
  {url:'https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fehall.jhun.edu.cn%2Flogin%3Fservice%3Dhttps%253A%252F%252Fevil.invalid'},
  {action:'http://authserver.jhun.edu.cn:8080/authserver/login'},
  {action:'http://authserver.jhun.edu.cn/authserver/login?service=https%3A%2F%2Fevil.invalid'},
  {method:'get'},
  {action:'https://evil.invalid/post'},
  {action:'https://authserver.jhun.edu.cn/authserver/resetPassword'},
  {action:'https://authserver.jhun.edu.cn/authserver/login?service=https%3A%2F%2Fevil.invalid'},
  {loading:true},{missing:true},{captcha:true}
]){const r=run(options);check(()=>assert.notEqual(r.result.state,'submitted'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.user.value,''));check(()=>assert.equal(r.pass.value,''));}
for(const action of [LOGIN.replace('https:','http:'),LOGIN.replace('/login?','/login;jsessionid=SYNTHETIC.route?')]){const r=run({action});check(()=>assert.equal(r.result.state,'submitted'));check(()=>assert.equal(r.form.action,action));check(()=>assert.equal(r.form.posted,true));}
for(const url of [LOGIN.replace('/login?','/login;jsessionid=SYNTHETIC.route?')]){const r=run({url,action:url});check(()=>assert.equal(r.result.state,'submitted'));}
const prepared=run({prepare:true,action:LOGIN.replace('https:','http:')});check(()=>assert.equal(prepared.result.state,'prepared'));check(()=>assert.equal(prepared.form.action,LOGIN.replace('https:','http:')));check(()=>assert.equal(prepared.clicks,0));check(()=>assert.equal(prepared.user.value,''));check(()=>assert.equal(prepared.pass.value,''));
const altered=run({mutateAction:'https://evil.invalid/post'});check(()=>assert.equal(altered.form.posted,false));check(()=>assert.equal(altered.result.state,'rejected'));
for(const options of [{noEncryption:true},{plaintextEncryption:true},{noSubmit:true},{rejected:true}]){const r=run(options);check(()=>assert.notEqual(r.result.state,'submitted'));check(()=>assert.notEqual(r.form.posted,true));check(()=>assert.equal(r.form.listeners.length,1));}
const direct='https://authserver.jhun.edu.cn/authserver/login?service=http://hqfw.jhun.edu.cn/wsbx/login/cas%23%2Fwybx';
for(const action of [direct,direct.replace(/^https:/,'http:')]){const r=run({url:direct,action});check(()=>assert.equal(r.result.state,'submitted'));check(()=>assert.equal(new URL(r.form.action).searchParams.get('service'),'http://hqfw.jhun.edu.cn/wsbx/login/cas#/wybx'));check(()=>assert.equal(new URL(r.form.action).hash,''));check(()=>assert.equal(new URL(r.form.action).protocol,new URL(action).protocol));}
const httpLogin=direct.replace(/^https:/,'http:');
const http=run({url:httpLogin,action:httpLogin});check(()=>assert.equal(http.result.state,'submitted'));check(()=>assert.equal(http.form.action,httpLogin));check(()=>assert.equal(http.form.posted,true));
for(const options of [{noEncryption:true},{plaintextEncryption:true}]){const r=run({...options,url:httpLogin,action:httpLogin});check(()=>assert.notEqual(r.result.state,'submitted'));check(()=>assert.equal(r.form.posted,false));}
// HTTP school template with no separate encryption field: let its own handler run.
for(const inPlace of [false,true]){
 const r=run({url:httpLogin,action:httpLogin,missingEncrypted:true,inPlace});
 check(()=>assert.equal(r.result.state,'submitted'));
 check(()=>assert.equal(r.clicks,1));check(()=>assert.equal(r.form.posted,true));
 check(()=>assert.equal(r.form.schoolReceived,r.secret));
 check(()=>assert.equal(r.pass.value,inPlace?'school-transformed':r.secret));
 check(()=>assert.equal(r.form.action,httpLogin));
}
const missingInspect=run({url:httpLogin,action:httpLogin,missingEncrypted:true,inspect:true});
check(()=>assert.equal(missingInspect.result.state,'ready'));check(()=>assert.equal(missingInspect.clicks,0));check(()=>assert.equal(missingInspect.pass.value,''));
for(const options of [{captcha:true},{method:'get'},{action:'https://evil.invalid/post'}]){
 const r=run({url:httpLogin,action:httpLogin,missingEncrypted:true,...options});
 check(()=>assert.notEqual(r.result.state,'submitted'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.pass.value,''));
}

const electricityService='https://hub.17wanxiao.com/bsacs/light.action?flag=cassso30_jhdxjrxyjf&ecardFunc=index';
const electricityLogin='http://authserver.jhun.edu.cn/authserver/login?service='+encodeURIComponent(electricityService);
for(const url of [electricityLogin,electricityLogin.replace(/^http:/,'https:')]){
 const r=run({url,action:url});check(()=>assert.equal(r.result.state,'submitted'));check(()=>assert.equal(r.form.posted,true));
 check(()=>assert.equal(new URL(r.form.action).searchParams.get('service'),electricityService));
 check(()=>assert.equal(new URL(r.form.action).searchParams.has('ecardFunc'),false));
}
for(const service of [electricityService.replace('https:','http:'),electricityService.replace('hub.17wanxiao.com','hub.17wanxiao.com.evil.invalid'),electricityService.replace('hub.17wanxiao.com','other.17wanxiao.com'),electricityService.replace('hub.17wanxiao.com','user@hub.17wanxiao.com'),electricityService.replace('hub.17wanxiao.com','hub.17wanxiao.com:8443'),electricityService+'&service='+encodeURIComponent('https://evil.invalid/')]){
 const url='http://authserver.jhun.edu.cn/authserver/login?service='+encodeURIComponent(service);
 for(const opts of [{url,action:electricityLogin},{url:electricityLogin,action:url}]){
  const r=run(opts);check(()=>assert.equal(r.result.state,'unsupported'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.pass.value,''));
 }
}
for(const opts of [{url:electricityService,action:electricityService},{url:electricityLogin,action:electricityService},{url:electricityLogin,action:electricityLogin,captcha:true}]){
 const r=run(opts);check(()=>assert.notEqual(r.result.state,'submitted'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.pass.value,''));
}
const cloudPayment='https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do';
for(const opts of [{url:cloudPayment,action:cloudPayment},{url:electricityLogin,action:cloudPayment},{url:'http://authserver.jhun.edu.cn/authserver/login?service='+encodeURIComponent(cloudPayment),action:electricityLogin}]){
 const r=run(opts);check(()=>assert.equal(r.result.state,'unsupported'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.pass.value,''));
}
const electricityRelay='https://open.17wanxiao.com/';
for(const opts of [{url:electricityRelay,action:electricityRelay},{url:electricityLogin,action:electricityRelay},{url:'http://authserver.jhun.edu.cn/authserver/login?service='+encodeURIComponent(electricityRelay),action:electricityLogin}]){
 const r=run(opts);check(()=>assert.equal(r.result.state,'unsupported'));check(()=>assert.equal(r.clicks,0));check(()=>assert.equal(r.pass.value,''));
}
const inspect=run({inspect:true,captcha:true});check(()=>assert.equal(inspect.result.captcha,true));check(()=>assert.equal(inspect.clicks,0));check(()=>assert.equal(inspect.pass.value,''));
console.log('Identity login adapter: '+checks+' checks passed (synthetic credentials; observed school form structure).');
