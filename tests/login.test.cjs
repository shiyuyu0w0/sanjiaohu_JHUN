const fs=require('fs'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync(require('path').join(__dirname,'../app/src/main/assets/login-submit.js'),'utf8');
function run(options={}){
 let clicked=0;const form={action:'https://jwxt.jhun.edu.cn/cas/login.action'};
 const fields={username:{form,value:''},password:{type:'password',value:''},login:{click:()=>clicked++},randnumber:{disabled:false,value:''},setCookie:{checked:true}};
 Object.values(fields).forEach(e=>e.dispatchEvent=()=>{});
 if(options.missing)delete fields.password;
 if(options.badAction)form.action='https://outside.invalid/login';
 const context={location:{origin:options.badOrigin?'https://outside.invalid':'https://jwxt.jhun.edu.cn',href:'https://jwxt.jhun.edu.cn/cas/login.action'},document:{getElementById:id=>fields[id]},URL,Event:class{},getComputedStyle:()=>({display:options.captcha?'block':'none'})};
 const username='test-user',password='quote"\\\n);throw new Error("not code");';
 const result=vm.runInNewContext(code+'('+JSON.stringify(username)+','+JSON.stringify(password)+','+JSON.stringify(options.answer||'')+')',context);
 return {result,fields,clicked,password};
}
let normal=run();assert.equal(normal.clicked,1);assert.equal(normal.fields.password.value,normal.password);assert.equal(normal.fields.username.value,'test-user');assert.equal(normal.fields.setCookie.checked,false);
for(const options of [{missing:true},{badOrigin:true},{badAction:true},{captcha:true}]){let r=run(options);assert(r.result.error);assert.equal(r.clicked,0);assert.equal(r.fields.username.value,'');}
let verification=run({captcha:true,answer:'1234'});assert.equal(verification.clicked,1);assert.equal(verification.fields.randnumber.value,'1234');
console.log('PASS: native login submit adapter, hostile string escaping, origin/action guards and conditional captcha');
