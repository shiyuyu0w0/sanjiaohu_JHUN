// Synthetic fixtures only; no student records or authenticated portal snapshots.
const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const assets=path.join(__dirname,'../app/src/main/assets');
const sandbox={URL,URLSearchParams,atob:s=>Buffer.from(s,'base64').toString('binary'),Event:class {constructor(type){this.type=type;}}};
const parser=vm.runInNewContext(fs.readFileSync(path.join(assets,'portal-parser.js'),'utf8'),sandbox);
let checks=0;const check=(v,message)=>{checks++;assert.ok(v,message);};
const target=parser.term('2025-2026学年第二学期');
check(target.year===2025&&target.half===2,'semester label identifies year and half');
check(!parser.term('2025-2028学年第一学期'),'invalid year span rejected');
const url=t=>'https://jwxt.jhun.edu.cn/student/wsxk.xskcb10319.jsp?params='+Buffer.from(`xn=${t.year}&xq=${t.half-1}&xh=synthetic`).toString('base64');
check(parser.reportMatches(url(target),target),'verified report semester');
check(!parser.reportMatches(url({...target,half:1}),target),'stale iframe term rejected');
check(!parser.reportMatches('https://jwxt.jhun.edu.cn/student/other.jsp',target),'wrong report endpoint rejected');
const cell=(value,span=1)=>({innerText:value,textContent:value,colSpan:span,rowSpan:1});
const row=values=>({cells:values.map(v=>typeof v==='string'?cell(v):v)});
const courseText='示例课程\n示例教师\n1-5,7-9,11-12[1-4]\n教学楼 A101';
function scheduleDocument(){
  const rows=[row(['','星期一','星期二','星期三','星期四','星期五','星期六','星期日'])];
  // Build with real grid row shape (prefix then seven cells).
  for(let i=1;i<=6;i++)rows[i]=row([[...'一二三四五六'][i-1],...Array(7).fill('')]);
  rows[1].cells[1]=cell(courseText);rows[2].cells[1]=cell(courseText);
  return {URL:url(target),readyState:'complete',rows,querySelectorAll:selector=>selector==='table'?[{rows}]:[],querySelector:()=>null};
}
let doc=scheduleDocument(),parsed=parser.schedule(doc,target);
check(parsed.complete&&parsed.courses.length===1,'repeated four-period course deduplicated');
check(parsed.courses[0].day===1&&parsed.courses[0].start===1&&parsed.courses[0].end===4,'day and periods retained');
check(parsed.maxWeek===20,'ordinary term uses compact week range');
doc.rows[1].cells[7]=cell('另一课程\n教师乙\n2-18(双)[11-12]\n教室 B202');parsed=parser.schedule(doc,target);
check(parsed.courses.length===2&&parsed.courses[1].day===7,'Sunday evening parsed');
check(parsed.courses[1].weekText==='2-18(双)','alternating-week expression preserved');
doc.rows[3].cells[3]=cell('异常课程，没有周次');check(parser.schedule(doc,target).error==='structure','malformed cells cannot overwrite cache');
doc=scheduleDocument();doc.rows.pop();check(parser.schedule(doc,target).error==='structure','missing row is incomplete');
doc=scheduleDocument();doc.rows[1].cells[1].colSpan=2;check(parser.schedule(doc,target).error==='structure','merged weekday cells rejected');
doc=scheduleDocument();doc.URL=url({...target,year:2026});check(parser.schedule(doc,target).error==='waiting','old semester report remains pending');
doc=scheduleDocument();doc.rows[1].cells[1]=cell('');doc.rows[2].cells[1]=cell('');check(parser.schedule(doc,target).courses.length===0,'verified empty grid accepted');
const headings=['序号','课程/环节','学分','总学时','类别','修读性质','考核方式','取得方式','成绩','备注'];
function gradeDocument(){
  const rows=[row(headings),row(['1','[TEST101]测试课程','2','32','专业课','必修','考试','正常','97.0','']),row(['2','[TEST102]测试课程乙','1','16','实践课','必修','考查','正常','不合格','待补考'])];
  return {URL:'https://jwxt.jhun.edu.cn/student/xscj.stuckcj_data.jsp',readyState:'complete',body:cell('江汉大学学生成绩明细[原始]\n学年学期：2025-2026学年第二学期\n第\u2002 1\u2002页 共\u2002 1\u2002页'),rows,querySelectorAll:q=>q==='table'?[{rows}]:[],querySelector:()=>null};
}
doc=gradeDocument();parsed=parser.grades(doc,target);
check(parsed.complete&&parsed.entries.length===2,'complete semester grades parsed');
check(parsed.entries[0].score==='97.0'&&parsed.entries[1].score==='不合格','numeric and qualitative grades stay verbatim');
check(parsed.entries[1].note==='待补考','remarks retained');
check(!Object.keys(parsed).includes('student'),'result stores only course records');
doc.body=cell('江汉大学学生成绩明细[原始]\n学年学期：2025-2026学年第一学期\n第 1 页 共 1 页');
check(parser.grades(doc,target).error==='waiting','grades for wrong semester rejected');
doc=gradeDocument();doc.body.innerText=doc.body.innerText.replace('共\u2002 1','共\u2002 2');check(parser.grades(doc,target).error==='incomplete','partial pagination rejected');
doc=gradeDocument();doc.rows[2].cells.pop();check(parser.grades(doc,target).error==='structure','truncated grade row rejected');
doc=gradeDocument();doc.rows[2].cells[0]=cell('3');check(parser.grades(doc,target).error==='structure','missing grade row rejected');
doc=gradeDocument();doc.rows.splice(1);check(parser.grades(doc,target).entries.length===0,'scoped empty table accepted');
doc.body=cell('江汉大学学生成绩明细[原始] 没有检索到记录! 第 1 页 共 1 页');
check(parser.grades(doc,target).error==='waiting','unscoped empty result needs query provenance');
check(parser.grades(doc,target,true).entries.length===0,'fresh verified empty query accepted');

function effectiveDocument(){
  const header=['序号','课程/环节','学分','总学时','类别','修读性质','考核方式','成绩','获得学分','绩点','学分绩点','备注'];
  const rows=[row(header),row(['1','[DEMO1]测试课程','2','32','专业课/必修课','初修','考试','90.0','2','4.0','8.0','']),row(['2','[DEMO2]测试实践','1','16','实践课','初修','考查','合格','1','2.0','2.0',''])];
  const summary=[row([cell('修读课程环节数',2),'学分','获得学分','获得绩点','获得学分绩点','获得平均学分绩点','平均成绩','加权平均成绩']),row(['必修课','1','2','2','4.0','8.0','4.0','90.0','']),row(['环节','1','1','1','2.0','2.0','2.0','70.0','']),row(['合计','2','3','3','6.0','10.0','3.33','80.0','83.33'])];
  return {URL:'https://jwxt.jhun.edu.cn/student/xscj.stuckcj_data.jsp',readyState:'complete',body:cell('江汉大学学生成绩明细[有效]\n学年学期：2025-2026学年第二学期\n第 1 页 共 1 页'),rows,summary,querySelectorAll:q=>q==='table'?[{rows},{rows:summary}]:[],querySelector:()=>null};
}
doc=effectiveDocument();parsed=parser.effective(doc,target,true,false);
check(parsed.complete&&parsed.entries.length===2,'effective grades include complete semester entries');
check(parsed.entries[0].point==='4.0'&&parsed.entries[1].score==='合格','grade point read from school, without score conversion');
check(parsed.summary.total.gpa==='3.33'&&parsed.summary.total.weighted==='83.33','official GPA and weighted average preserved verbatim');
check(parsed.summary.groups[1].earned==='1'&&parsed.summary.groups[1].average==='70.0','component credits and averages preserved');
check(parser.effective(doc,target,false,false).error==='waiting','effective grades require verified query');
doc.summary.pop();check(parser.effective(doc,target,true,false).error==='structure','missing total rejects incomplete summary');
doc=effectiveDocument();doc.summary[3].cells[1]=cell('3');check(parser.effective(doc,target,true,false).error==='incomplete','total count detects omitted grade row');
doc=effectiveDocument();doc.body=cell('江汉大学学生成绩明细[有效]\n学年学期：2025-2026学年第一学期\n学年学期：2025-2026学年第二学期\n第 1 页 共 2 页\n第 2 页 共 2 页');
check(parser.effective(doc,target,true,false).error==='waiting','multiple semesters cannot satisfy semester-only query');
parsed=parser.effective(doc,null,true,true);check(parsed.complete&&parsed.scope==='all'&&parsed.term==='入学以来','cumulative summary supports all loaded report pages');
check(parsed.entries.length===0,'cumulative cache stores aggregates without duplicating full transcript');
doc.body=cell('江汉大学学生成绩明细[有效]\n没有检索到记录!\n第 1 页 共 1 页');check(parser.effective(doc,null,true,true).summary.total===null,'empty cumulative summary is distinct from zero averages');

// Exercise controller navigation, selected values, stale documents and job replacement.
const controllerSource=fs.readFileSync(path.join(assets,'portal.js'),'utf8');
let menuClicks=[],queryClicks=0;
const options=['2026-2027学年第一学期','2025-2026学年第二学期'].map((textContent,i)=>({textContent,value:String(i)}));
const selector={options,selectedIndex:0,value:'0',dispatchEvent(){this.selectedIndex=Number(this.value);}};
const twoDim={checked:true,click(){this.checked=true;}};
const controls={querySelector:q=>q==='select#xnxq'||q==='#xnxq'?selector:q==='#cxfs_ewb'?twoDim:null,querySelectorAll:()=>[],readyState:'complete',URL:'https://jwxt.jhun.edu.cn/student/xkjg.wdkb.jsp'};
const fields={xn:{value:'2026',dispatchEvent(){}},xn1:{value:'2027',dispatchEvent(){}},xq:{options:[{textContent:'第一学期',value:'0'},{textContent:'第二学期',value:'1'}],value:'0',dispatchEvent(){}},sjxz3:{checked:false,click(){this.checked=true;}},sjxz1:{checked:false,click(){this.checked=true;}},yxcj:{checked:false,click(){this.checked=true;}},xwtg:{checked:true,click(){this.checked=false;}},btnQry:{click(){queryClicks++;}}};
const gradeControls={URL:'https://jwxt.jhun.edu.cn/student/xscj.stuckcj.jsp',readyState:'complete',querySelector:q=>fields[q.slice(1)]||null,querySelectorAll:()=>[]};
let children=[controls];
const mainDoc={URL:'https://jwxt.jhun.edu.cn/frame/homes.action',readyState:'complete',querySelector:q=>q.startsWith('.menu-item')?{click(){menuClicks.push(q.includes('S403')?'grades':'schedule');}}:null,querySelectorAll:q=>q==='iframe,frame'?children.map(contentDocument=>({contentDocument})):[]};
const context=vm.createContext({...sandbox,window:{},document:mainDoc});const controller=vm.runInContext(controllerSource,context);
let output=controller(1,'schedule',target.label,parser);check(menuClicks.length===1,'navigate through live school menu');
output=controller(1,'schedule',target.label,parser);check(selector.value==='1'&&output.terms.length===2,'select requested semester using option label');
children=[controls,scheduleDocument()];output=controller(1,'schedule',target.label,parser);check(output.complete&&output.term===target.label,'selected schedule returned');
controller(2,'grades',target.label,parser);controller(2,'grades',target.label,parser);check(menuClicks.at(-1)==='grades','grades navigate through school menu');
const stale=effectiveDocument();children=[gradeControls,stale];controller(2,'grades',target.label,parser);
check(fields.xn.value==='2025'&&fields.xn1.value==='2026'&&fields.xq.value==='1'&&queryClicks===1,'grade query uses requested year and half exactly once');
check(!fields.xwtg.checked&&fields.sjxz3.checked&&fields.yxcj.checked,'query uses semester scope and effective grades');
check(controller(2,'grades',target.label,parser).error==='waiting','pre-query report cannot satisfy refresh');
children=[gradeControls,effectiveDocument()];check(controller(2,'grades',target.label,parser).complete,'new report accepted after query');
controller(3,'schedule','2026-2027学年第一学期',parser);check(menuClicks.at(-1)==='schedule','new job replaces prior query state');
check(queryClicks===1,'polling never repeats grade submission');
controller(4,'summary','入学以来',parser);check(menuClicks.at(-1)==='grades','summary opens grade menu directly');
controller(4,'summary','入学以来',parser);check(fields.sjxz1.checked&&queryClicks===2,'summary explicitly selects enrollment-to-date scope');
children=[gradeControls,effectiveDocument()];check(controller(4,'summary','入学以来',parser).scope==='all','new cumulative result returned under distinct scope');
console.log(`${checks} portal parsing and query checks passed`);
