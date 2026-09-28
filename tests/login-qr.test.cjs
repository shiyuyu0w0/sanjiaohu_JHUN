const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const code=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/login-qr.js'),'utf8');
function fixture(options={}) {
  let now=1000,id=0;const timers=new Map(),requests=[],redirects=[],draws=[];
  const rows=Array.from({length:29},()=>({cells:Array.from({length:29},()=>({}))}));
  const window={uuid16:options.nonce||'smdljwxt0123456789ABCDEF'};
  const location={origin:options.origin||'https://jwxt.jhun.edu.cn',pathname:options.pathname||'/cas/login.action',href:'https://jwxt.jhun.edu.cn/cas/login.action',assign:u=>redirects.push(u)};
  class XHR {
    constructor(){this.headers={'X-Requested-With':'cn.jhun.sanjiaohu'};}
    open(method,url){this.method=method;this.url=url;}
    setRequestHeader(name,value){this.headers[name]=value;}
    send(body){this.body=body;requests.push(this);}
    abort(){this.aborted=true;}
    respond(text,status=200){this.status=status;this.responseText=text;this.onload();}
  }
  const context=vm.createContext({window,location,URL,Date:{now:()=>now},XMLHttpRequest:XHR,
    setTimeout:f=>{timers.set(++id,f);return id;},clearTimeout:id=>timers.delete(id),getComputedStyle:()=>({backgroundColor:'#000'}),
    document:{querySelector:()=>options.missing?null:{rows},createElement:()=>({getContext:()=>({fillRect:(...args)=>draws.push(args)}),toDataURL:()=> 'data:image/png;base64,TEST'})}
  });
  function run(action){return vm.runInContext(code+'('+JSON.stringify(action)+')',context);}
  function tick(){const first=timers.entries().next().value;assert(first,'Expected polling timer');timers.delete(first[0]);first[1]();}
  return {run,tick,requests,redirects,timers,draws,expire:()=>{now+=120001;},advance:ms=>{now+=ms;}};
}
let f=fixture();assert.equal(f.run('start').image,'data:image/png;base64,TEST');assert.equal(f.draws.length,842);
f.run('start');assert.equal(f.timers.size,1,'Repeated start must not duplicate polling');
f.tick();assert.equal(f.requests.length,1);assert.equal(f.requests[0].url,'/frame/LoginBar.jsp');
assert.equal(f.requests[0].body,'operate=query&qrCode=smdljwxt0123456789ABCDEF');
assert.equal(f.requests[0].headers['X-Requested-With'],'XMLHttpRequest');assert.equal(f.requests[0].headers.Accept,'*/*');assert.equal(f.requests[0].timeout,12000);
f.requests[0].respond('');f.tick();f.requests[1].respond('test-account');
assert.equal(f.run('status').state,'verifying');assert.equal(f.requests[2].url,'/cas/logon.action');
assert.equal(new URLSearchParams(f.requests[2].body).get('loginmethod'),'xiqueer');
assert.equal(new URLSearchParams(f.requests[2].body).get('password'),'smdljwxt0123456789ABCDEF');
assert.equal(f.requests[2].headers['X-Requested-With'],'XMLHttpRequest');assert.equal(f.requests[2].headers.Accept,'text/plain, */*; q=0.01');assert.equal(f.requests[2].timeout,45000);
f.requests[2].respond(JSON.stringify({status:'200',result:'/frame/homes.action?v=fixture'}));
assert.deepEqual(f.redirects,['https://jwxt.jhun.edu.cn/frame/homes.action?v=fixture']);assert.equal(f.run('status').state,'success');
for (const result of ['https://outside.invalid/frame/homes.action','http://jwxt.jhun.edu.cn/frame/homes.action','/cas/login.action','javascript:alert(1)']) {
  f=fixture();f.run('start');f.tick();f.requests[0].respond('test');f.requests[1].respond(JSON.stringify({status:200,result}));
  assert.equal(f.run('status').state,'error');assert.equal(f.redirects.length,0);
}
f=fixture();f.run('start');f.tick();f.run('stop');assert(f.requests[0].aborted);f.requests[0].respond('late-user');assert.equal(f.requests.length,1);assert.equal(f.timers.size,0);
f=fixture();f.run('start');f.tick();f.expire();assert.equal(f.run('status').state,'expired');f.requests[0].respond('late-user');assert.equal(f.requests.length,1);
f=fixture();f.run('start');f.tick();f.requests[0].ontimeout({type:'timeout'});assert.equal(f.run('status').state,'waiting');assert(f.run('status').message.includes('重新确认'));assert.equal(f.timers.size,1);
f.requests[0].onerror({type:'error'});assert.equal(f.timers.size,1,'Error and timeout must not create duplicate retries');
f.tick();f.requests[1].respond('');assert.equal(f.run('status').message,'');f.tick();f.requests[2].respond('account');f.requests[3].respond(JSON.stringify({status:200,result:'/frame/homes.action'}));assert.equal(f.redirects.length,1);
f=fixture();f.run('start');for(let n=0;n<3;n++){f.tick();f.requests[n].onerror({type:'error'});}assert.equal(f.run('status').state,'error');assert.equal(f.requests.length,3);assert.equal(f.timers.size,0);
f=fixture();f.run('start');f.tick();f.requests[0].ontimeout({type:'timeout'});f.run('stop');assert.equal(f.timers.size,0,'Cancel also cancels queued retry');
f=fixture();f.run('start');f.tick();f.requests[0].respond('account');f.requests[1].ontimeout({type:'timeout'});assert.equal(f.run('status').state,'error');assert(f.run('status').message.includes('教务登录未完成'));assert(f.run('status').message.includes('请求超时'));assert.equal(f.requests.length,2);assert.equal(f.timers.size,0,'A one-time logon must not be resubmitted automatically');
f=fixture();f.run('start');f.tick();f.advance(119500);f.requests[0].respond('account');f.advance(30000);assert.equal(f.run('status').state,'verifying','Confirm near QR expiry still has time for logon');f.requests[1].respond(JSON.stringify({status:200,result:'/frame/homes.action'}));assert.equal(f.redirects.length,1);
f=fixture();f.run('start');f.tick();f.requests[0].respond('account');f.advance(60001);assert.equal(f.run('status').state,'expired');f.requests[1].respond(JSON.stringify({status:200,result:'/frame/homes.action'}));assert.equal(f.redirects.length,0);
f=fixture();f.run('start');f.tick();f.requests[0].respond('<html>error</html>');assert.equal(f.run('status').state,'error');assert.equal(f.requests.length,1);
f=fixture();f.run('start');f.tick();f.requests[0].respond('account');f.requests[1].respond('{broken');assert.equal(f.run('status').state,'error');
f=fixture();f.run('start');f.tick();f.requests[0].respond('account');f.requests[1].respond(JSON.stringify({status:401,message:'扫码失效'}));assert.equal(f.run('status').message,'扫码失效');
for(const options of [{origin:'https://outside.invalid'},{pathname:'/other'},{nonce:'bad-token'}]){f=fixture(options);assert.equal(f.run('start').state,options.nonce?'waiting':'unsupported');assert.equal(f.requests.length,0);assert.equal(f.timers.size,0);}
f=fixture({missing:true});assert.equal(f.run('start').state,'waiting');assert.equal(f.timers.size,0);
console.log('PASS: school AJAX headers, bounded query retries, separate logon timeout, near-expiry confirmation, cancellation and secure redirects');
