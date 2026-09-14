const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const code=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/login-state.js'),'utf8');
function inspect(options={}){
 const fields={username:{},password:{},login:{},randnumber:{disabled:!!options.disabled},randpic:{complete:true,naturalWidth:80,naturalHeight:30}};
 if(options.missing)delete fields.password;
 const document={getElementById:id=>fields[id],createElement:()=>({getContext:()=>({drawImage(){}}),toDataURL:()=>{if(options.tainted)throw Error('tainted canvas');return 'data:image/png;base64,TEST';}})};
 return vm.runInNewContext(code,{location:{origin:options.external?'https://outside.invalid':'https://jwxt.jhun.edu.cn',pathname:options.success?'/frame/homes.action':'/cas/login.action'},document,getComputedStyle:()=>({display:options.captcha?'block':'none'})});
}
assert.equal(inspect().state,'ready');assert.equal(inspect().captcha,false);
assert.equal(inspect({success:true}).state,'success');assert.equal(inspect({success:true,external:true}).state,'unsupported');
assert.equal(inspect({missing:true}).state,'waiting');assert.equal(inspect({captcha:true}).captcha,true);
assert.equal(inspect({captcha:true}).image,'data:image/png;base64,TEST');assert.equal(inspect({captcha:true,disabled:true}).captcha,false);
assert.equal(inspect({captcha:true,tainted:true}).captcha,true);assert.equal(inspect({captcha:true,tainted:true}).image,'');
console.log('PASS: session detection, origin guard, optional and unreadable captcha states');
