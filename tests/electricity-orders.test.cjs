const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const adapter=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/electricity-orders.js'),'utf8');
function run(options={}){
  let added=0,style=null;
  const context={location:{origin:options.origin||'https://h5cloud.17wanxiao.com:18443',pathname:options.path||'/CloudPayment/bill/record.do'},
    document:{querySelector:()=>options.missing?null:{},getElementById:()=>style,createElement:()=>({}),head:{appendChild:s=>{style=s;added++;}}}};
  const theme=options.theme||{surface:'#f2fcff',card:'#eafaff',text:'#262626',muted:'#666666',accent:'#176680',outline:'#619cae'};
  const scope=vm.createContext(context);const result=vm.runInContext(adapter+'('+JSON.stringify(theme)+')',scope);
  if(result)vm.runInContext(adapter+'('+JSON.stringify(theme)+')',scope);
  return {result,added};
}
assert.deepEqual(run(),{result:true,added:1});
assert.deepEqual(run({path:'/CloudPayment/bill/detail.do'}),{result:true,added:1});
for(const options of [{origin:'https://evil.test'},{origin:'http://h5cloud.17wanxiao.com:18443'},{path:'/CloudPayment/bill/selectPayProject.do'},{path:'/CloudPayment/bill/type.do'},{missing:true},{theme:{surface:'red;display:none'}}])assert.deepEqual(run(options),{result:false,added:0});
console.log('PASS: order theme restricted to verified order pages; no styling of login/payment pages');
