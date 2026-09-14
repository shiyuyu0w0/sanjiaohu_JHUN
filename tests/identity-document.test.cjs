const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const code=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/identity-document.js'),'utf8');
let checks=0;
for(const [origin,pathname,form,ready] of [
  ['https://authserver.jhun.edu.cn','/authserver/login',true,'interactive'],
  ['http://authserver.jhun.edu.cn','/authserver/login',true,'complete'],
  ['null','/',false,'complete'],
  ['https://authserver.jhun.edu.cn','/authserver/login;jsessionid=SECRET',true,'complete'],
  ['https://other.example','/SECRET',false,'loading']
]){
 const field={get value(){throw Error('must not read input value');}};
 const result=vm.runInNewContext(code,{location:{origin,pathname},document:{readyState:ready,getElementById:()=>form?field:null}});
 assert.equal(result.secure,origin==='https://authserver.jhun.edu.cn');checks++;
 assert.equal(result.form,form);checks++;
 assert.equal(JSON.stringify(result).includes('SECRET'),false);checks++;
 assert.equal(Object.keys(result).sort().join(','),'form,login,originKind,ready,secure');checks++;
}
console.log('Identity document probe: '+checks+' checks passed');
