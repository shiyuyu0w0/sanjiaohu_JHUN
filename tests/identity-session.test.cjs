const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/identity-session.js'),'utf8');
function detect(options={}){
  const avatar={},logout={textContent:options.wrongLabel?'登录':'安全退出'};
  const tool={textContent:'测试用户',getClientRects:()=>options.guest?[]:[{}],contains:a=>a===avatar};
  const elements={ampHasLoginTool:tool,ampHeaderToolUser:avatar,ampHeaderUserInfoLogoutBtn:logout};
  if(options.missing)delete elements[options.missing];
  return vm.runInNewContext(source,{location:{protocol:'http:',hostname:options.evil?'evil.invalid':'ehall.jhun.edu.cn',pathname:options.login?'/login':'/new/index.html'},document:{getElementById:id=>elements[id]},getComputedStyle:()=>({visibility:options.hidden?'hidden':'visible'})});
}
assert.equal(detect(),true);
for(const options of [{guest:true},{hidden:true},{wrongLabel:true},{evil:true},{login:true},...['ampHasLoginTool','ampHeaderToolUser','ampHeaderUserInfoLogoutBtn'].map(missing=>({missing}))])assert.equal(detect(options),false);
console.log('Identity session: 9 checks passed (synthetic account data, observed school DOM structure)');
