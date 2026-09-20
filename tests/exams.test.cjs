// Synthetic exams only, using the verified school table/selector structure.
const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const sandbox={URL,URLSearchParams,Set,Event:class {constructor(type){this.type=type;}}};
const parser=vm.runInNewContext(fs.readFileSync('app/src/main/assets/portal-parser.js','utf8'),sandbox);
let checks=0;function check(v,m){checks++;assert.ok(v,m);}
const target=parser.term('2026-2027学年第一学期'),round=target.label+'第2-17周';
const cell=text=>({innerText:text,textContent:text,colSpan:1,rowSpan:1});
const row=values=>({cells:values.map(cell)});
function report(empty=false){const rows=[row(['课程','学分','考试时间','考试地点','座位号','备注']),row(['[TEST01]测试课程','2','2026-11-12(10周 星期四)13:40-15:40','教室待定','**',''])];return {URL:'https://jwxt.jhun.edu.cn/student/ksap.ksapb_date.jsp',readyState:'complete',body:cell('江汉大学考试安排表\n'+target.label+'\n'+(empty?'没有检索到记录!':'考试轮次：'+round)+'\n第  1  页    共  1  页'),rows,querySelector:()=>null,querySelectorAll:q=>q==='table'&&!empty?[{rows}]:[]};}
let d=report();check(parser.exams(d,target,round,true).entries.length===1,'school columns parsed');check(parser.exams(d,target,round,true).entries[0].seat==='**','unpublished seat retained');
d=report(true);check(parser.exams(d,target,round,true).entries.length===0,'fresh verified empty accepted');check(parser.exams(d,target,round,false).error==='waiting','unverified empty rejected');
d=report();check(parser.exams(d,target,'其他轮次',true).error==='waiting','wrong round rejected');check(parser.exams(d,parser.term('2025-2026学年第一学期'),round,true).error==='waiting','wrong term rejected');
d.URL=d.URL.replace('jwxt.jhun.edu.cn','example.com');check(parser.exams(d,target,round,true).error==='waiting','foreign origin rejected');
for(const mutation of [d=>d.rows[1].cells.pop(),d=>d.rows[1].cells[0].colSpan=2,d=>d.rows[1].cells[0]=cell(''),d=>d.rows.splice(1)]){d=report();mutation(d);check(parser.exams(d,target,round,true).error==='structure','incomplete rows rejected');}
d=report();d.body=cell(d.body.innerText.replace('共  1','共  2'));check(parser.exams(d,target,round,true).error==='incomplete','partial pagination rejected');
d=report();d.body=cell(d.body.innerText.replace(/第  1.*$/,''));check(parser.exams(d,target,round,true).error==='structure','missing pagination rejected');
d=report();d.body=cell(d.body.innerText.replace('共  1','共  2')+'\n第 2 页 共 2 页');check(parser.exams(d,target,round,true).entries.length===1,'all printed pages accepted');
const options=[{value:'20260',textContent:target.label}],rounds=[{value:'1',textContent:target.label+'第1周'},{value:'3',textContent:round}];
let clicks=0,activeReport=report(true),pending=null;
const termSelect={value:'20260',selectedIndex:0,options,dispatchEvent(){throw Error('unneeded change');}},roundSelect={value:'3',options:rounds};
const form={URL:'https://jwxt.jhun.edu.cn/student/ksap.ksapb.html',readyState:'complete',querySelector:s=>({'#xnxq':termSelect,'#kslc':roundSelect,'#btnQry':{click(){clicks++;pending=roundSelect.value==='1'?report(true):report();}}}[s]||null),querySelectorAll:()=>[]};
const desk={URL:'https://jwxt.jhun.edu.cn/frame/jw/teacherstudentmenu.jsp?menucode=S204',querySelector:()=>null,querySelectorAll:s=>s==='a'?[{textContent:'考试安排表',getAttribute:()=> '#content_3',click(){}}]:[]};
const root={URL:'https://jwxt.jhun.edu.cn/frame/homes.action',querySelector:s=>s==='.menu-item[data-code="S204"]'?{click(){}}:null,querySelectorAll:s=>s==='iframe,frame'?[desk,form,activeReport].map(contentDocument=>({contentDocument})):[]};
const ctx={...sandbox,document:root,window:{}};const run=vm.runInNewContext(fs.readFileSync('app/src/main/assets/exam-portal.js','utf8'),ctx);
let out;for(let i=0;i<12;i++){out=run(1,'',parser);if(pending){let stale=run(1,'',parser);check(stale.error==='waiting','old report rejected while next round loads');activeReport=pending;pending=null;}if(out.complete)break;}
check(out.complete&&out.entries.length===1,'all rounds merged including empty round');check(clicks===2,'one query per round');check(out.terms.length===1&&out.selectedTerm===target.label,'school-selected term learned');
ctx.window={};clicks=0;out=run(2,'2025-2026学年第一学期',parser);out=run(2,'2025-2026学年第一学期',parser);out=run(2,'2025-2026学年第一学期',parser);check(out.error==='unavailable'&&clicks===0,'unavailable semester never queries');
ctx.window={};options.unshift({value:'',textContent:''});termSelect.value='';let changed=0;termSelect.dispatchEvent=()=>{changed++;termSelect.selectedIndex=1;};
out=run(4,'',parser);out=run(4,'',parser);out=run(4,'',parser);check(changed===1&&termSelect.value==='20260'&&out.selectedTerm===target.label,'blank initial semester selects latest published term');
root.querySelector=()=>({});out=run(3,'',parser);check(out.error==='login','login form recognized');
console.log(checks+' exam parsing and query checks passed');
