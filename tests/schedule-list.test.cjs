const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const src=fs.readFileSync('app/src/main/assets/schedule-list.js','utf8'),controller=fs.readFileSync('app/src/main/assets/schedule-portal.js','utf8');
const context={URL,Set,Event:class {constructor(type){this.type=type;}}};
const p=vm.runInNewContext(src,context);let checks=0;function check(value,why){checks++;assert.ok(value,why);}
const term=p.term('2026-2027学年第一学期');
const cell=(s,rowSpan=1,colSpan=1)=>({innerText:s,textContent:s,rowSpan,colSpan});
const row=values=>({cells:values.map(v=>typeof v==='string'?cell(v):v)});
const header=['课程','学分','任课教师','上课时间及地点'];
function report(rows,extra='',tables){return {URL:'https://jwxt.jhun.edu.cn/changed/report',readyState:'complete',body:cell(term.label+'\n'+extra),querySelector:()=>null,querySelectorAll:s=>s==='table'?(tables||[{rows:rows.map(row)}]):[]};}
function parse(time){return p.parse(report([header,['测试课程','2','教师',time]]),term,true);}
for(const w of ['1-16周','第1—16周','1-16(周)','1-16周(单)','1-16(双)周','1-8,10-16周'])for(const day of ['星期一','周日','星期7'])for(const period of ['第1-2节','[1-2节]','(1-2)']){
  let result=parse(w+' '+day+' '+period+' A101');check(result.courses?.length===1,w+day+period);check(result.courses[0].room==='A101','room isolated');check(result.courses[0].day===(day==='星期一'?1:7),'weekday correct');
}
let result=parse('星期二[3-4节] 1-8周 B202\n星期四[5-6节] 9-16周 C303');check(result.courses.length===2,'multiple arrangement lines');
result=parse('1-4,6-17周 二[5-6] J32A203(60)');check(result.courses?.length===1&&result.courses[0].day===2,'live abbreviated weekday');
check(result.courses[0].room==='J32A203','room capacity is not part of location');
result=parse('1周 三[9-10] J05A101(179)；2-4,6-14周 三[9-10] J15A530(140)；15-17周 三[9-10] J09-405(15)');check(result.courses?.length===3&&result.courses.every(c=>c.day===3),'live room changes across week ranges');
result=parse('1-4周 ；1-4,6-17周 二[5-6] J32A203(60)');check(result.courses?.length===1&&result.unscheduled.length===1&&!result.requiresReview,'week-only fragment retained alongside weekly class');
for(const raw of ['5-12周，超星（app为学习通）','5-12周，雨课堂（app也叫雨课堂）']){result=parse(raw);check(result.unscheduled?.length===1&&result.unscheduled[0].note===raw&&!result.requiresReview,'live online course retained');}
const hiddenCell=s=>({...cell(s),style:{display:'none'}});
const realHeader=['上课班级\n代码','上课班级\n名称','课程','总\n学时','学分','修读\n性质','任课教师','选课\n状态','外年级/专业\n选课','教材','上课时间地点','备注'];
const liveTimes=['5-12周，超星（app为学习通）','5-12周，雨课堂（app也叫雨课堂）','1-3,5-13周 四[1-2] J02B203(57)','1周 三[9-10] J05A101(179)；2-4,6-14周 三[9-10] J15A530(140)；15-17周 三[9-10] J09-405(15)','1-4,6-9周 二[3-4] J05B204(57)；1-3,5-9周 四[3-4] J05B204(57)','1-4,6-13周 二[7-8] J32A102(60)；1-4,6-13周 三[1-2] J04B207(57)；12周 二[9-12] J07-202☆(48)；14周 二[1-4] J07-202☆(48)','1-4,6-13周 一[3-4] J05A212(179)；1-4,6-13周 二[1-2] J05A212(179)','1-4,6-11周 三[5-6] J04B307(94)；6-11周 五[3-4] J08-207☆(79)','1-4,6-10周 三[7-8] J06B203(57)；1-4,6-16周 一[1-2] J06B203(57)','1-2,5-16周 五[5-6] J08-201☆(48)；2周 日[5-6] J08-201☆(48)；5周 六[5-6] J08-201☆(48)','9周 五[1-2] J05B202(57)；10-17周 四[3-4] J05B202(57)；11-17周 三[7-8] J05B202(57)；14-17周 一[3-4] J05B202(57)','14-16周 五[1-4] J08-402☆(25)；16周 日[1-4] J08-402☆(25)','1-4,6-13周 一[7-8] 北田39(60)；14周 六[1-4] 北田39(60)；14周 六[5-8] 北田39(60)','1-4周 ；1-4,6-17周 二[5-6] J32A203(60)','1-4,6-9周 一[5-6] J02A212(179)；1-4,6-9周 三[3-4] J02A212(179)','10-17周 一[5-6] J02A212(179)；10-17周 三[3-4] J02A212(179)','14周 二[9-12] J04A108(179)'];
const liveRows=liveTimes.map((time,i)=>['class-'+i,'','[course-'+i+']测试课程'+i,'32','2','初修','[teacher]测试教师','选中','否','是',time,'',...Array.from({length:4},()=>hiddenCell('internal-id'))]);
const liveTables=[liveRows.slice(0,8),liveRows.slice(8,15),liveRows.slice(15)].map(rows=>({rows:[realHeader,...rows].map(row)}));
liveTables.push({rows:[['环节','学分','周数','类别','组次','周次','地点','指导教师'],['[practice]测试实习','3','3','实习','1','10-12','教学楼','']].map(row)});
result=p.parse(report([],'课程门数：17 第 1 页 共 3 页 第 2 页 共 3 页 第 3 页 共 3 页',liveTables),term,true);
check(result.complete&&result.reportRows===17&&result.issues.length===0,'three-page live layout ignores four hidden fields');
check(result.courses.length===34&&new Set(result.courses.map(c=>c.name)).size===15,'all regular courses and distinct arrangements imported');
check(result.unscheduled.length===4&&result.unscheduled.some(c=>c.name==='测试实习'&&c.note.includes('10-12')),'online courses, incomplete fragment and practice preserved');
check(result.courses.every(c=>!c.name.startsWith('[')&&c.teacher==='测试教师'),'internal code prefixes removed');
const hiddenByCss={...cell('internal-id'),ownerDocument:{defaultView:{getComputedStyle:()=>({display:'none'})}}};
result=p.parse(report([[...header.slice(0,2),hiddenByCss,...header.slice(2)],['测试课','2',hiddenByCss,'教师','1-16周 二[1-2] A101']]),term,true);
check(result.courses?.length===1&&result.courses[0].teacher==='教师','computed hidden columns removed without shifting fields');
result=p.parse(report([header,['测试课','2','教师','1-16周 二[1-2] A101','unexpected visible field']]),term,true);
check(result.error==='structure','unexpected visible columns still fail safely');
result=parse('1-8周 星期一[1-2节] A101 9-16周 星期三[3-4节] B202');check(result.courses.length===2&&result.courses[0].weekText!==result.courses[1].weekText,'different prefixes retain distinct weeks');
result=parse('1-16周 星期一[1-2节]\n教学楼 A101');check(result.courses[0].room==='教学楼 A101','room line continuation');
result=parse('1-16周 星期一第1,3节 A101');check(result.courses.length===2,'noncontiguous periods stay separate');
result=parse('0周 星期一1-2节 A101');check(result.ignoredZero===1&&result.courses.length===0,'zero week ignored explicitly');
result=parse('待定');check(result.unscheduled.length===1&&result.courses.length===0,'unfixed arrangements retained');
result=parse('未知时间');check(result.error==='structure','unreadable-only report never becomes empty');
let d=report([header,['甲','2','老师','1-16周 星期一1-2节 A'],['乙','2','老师','无法识别']]);result=p.parse(d,term,true);check(result.requiresReview&&result.issues.length===1&&result.courses.length===1,'partial rows produce candidate warning');
d=report([header,['甲','2','老师','1-8周 星期一1-2节 A'],['甲','2','老师','9-16周 星期一1-2节 A'],['甲','2','老师','1-8周 星期一1-2节 A']]);result=p.parse(d,term,true);check(result.courses.length===1&&result.merged===2&&result.courses[0].weekText.split(',').length===16,'duplicate/split rows merge weeks');
d=report([['课程名称','学分','授课教师','星期','节次','起止周','教室'],['甲','2','教师','二','3-4','1-16','A101']]);result=p.parse(d,term,true);check(result.courses[0].day===2&&result.courses[0].room==='A101','separate columns and aliases');
d=report([header,[cell('甲',2),cell('2',2),cell('教师',2),'1-8周 星期一1-2节 A'],['9-16周 星期二3-4节 B']]);result=p.parse(d,term,true);check(result.courses.length===2&&result.courses.every(c=>c.name==='甲'),'rowspan expanded');
d=report([header,['甲','2','教师']], '');check(p.parse(d,term,true).error==='structure','truncated row rejected instead of unscheduled');
d=report([header],'课程门数：0');check(p.parse(d,term,true).complete&&p.parse(d,term,true).courses.length===0,'explicit zero count accepted');
d=report([],'没有检索到记录!',[]);check(p.parse(d,term,true).complete,'explicit empty report accepted');check(p.parse(d,term,false).error==='waiting','empty requires query provenance');
d=report([header]);check(p.parse(d,term,true).error==='waiting','header alone does not erase timetable');
d=report([header,['甲','2','师','1-16周 星期一1-2节 A']],'课程门数：2');check(p.parse(d,term,true).stage==='report-pagination','summary is minimum count');
d.body=cell(term.label+'\n第 1 页 共 2 页');check(p.parse(d,term,true).error==='incomplete','missing print page');
d.body=cell(term.label+'\n第 1 页 共 2 页 第 2 页 共 2 页');check(p.parse(d,term,true).complete,'all print pages accepted');
d.URL='https://example.com/report';check(p.parse(d,term,true).error==='waiting','foreign report rejected');
d=report([header],'2025-2026学年第一学期');d.body=cell('2025-2026学年第一学期');check(p.parse(d,term,true).stage==='term-mismatch','wrong semester rejected');
let leaf=report([header],'课程门数：0');let nested=leaf;for(let i=0;i<15;i++){const child=nested;nested={URL:'https://jwxt.jhun.edu.cn/frame/'+i,querySelectorAll:()=>[{contentDocument:child}]};}check(p.documents(nested).length===16,'deep frames supported');
let cyclic={URL:'https://jwxt.jhun.edu.cn/frame',querySelectorAll:()=>[{contentDocument:cyclic}]};check(p.documents(cyclic).length===1,'cycle guarded');
let children=[],menuClicks=0,tabClicks=0,listClicks=0;
const selector={value:'a',selectedIndex:0,options:[{textContent:term.label,value:'a'},{textContent:'2025-2026学年第二学期',value:'b'}],dispatchEvent(){this.selectedIndex=this.value==='a'?0:1;}};
const frame={contentDocument:report([header], '课程门数：0'),addEventListener(name,fn){this.listener=fn;}};
const list={checked:false,click(){listClicks++;this.checked=true;}};
const form={URL:'https://jwxt.jhun.edu.cn/student/renamed',readyState:'complete',querySelector:s=>({'#xnxq':selector,'#cxfs_lb':list,'#frmReport':frame}[s]||null),querySelectorAll:s=>s==='iframe,frame'?[frame]:[]};
const tab={textContent:'个人课表',click(){tabClicks++;children=[form];}};
const shell={URL:'https://jwxt.jhun.edu.cn/shell',querySelector:()=>null,querySelectorAll:s=>s==='a'?[tab]:[]};
const root={URL:'https://jwxt.jhun.edu.cn/frame/homes.action',body:cell(term.label),querySelector:s=>s.includes('S203')?{click(){menuClicks++;children=[shell];}}:null,querySelectorAll:s=>s==='iframe,frame'?children.map(contentDocument=>({contentDocument})):[]};
const ctx={...context,window:{},document:root};const run=vm.runInNewContext(controller,ctx);
run(1,term.label,p);run(1,term.label,p);run(1,term.label,p);run(1,term.label,p);check(menuClicks===1&&tabClicks===1&&listClicks===1,'menu, subtab and list query each executed');
check(run(1,term.label,p).stage==='report-loading','old report ignored');frame.contentDocument=report([header,['甲','2','师','1-16周 星期一1-2节 A']]);check(run(1,term.label,p).stage==='report-settling','first fresh snapshot settles');check(run(1,term.label,p).complete,'stable fresh snapshot accepted');check(listClicks===1,'poll never repeats submission');
selector.value='b';check(run(1,term.label,p).stage==='selection-changed','changed selected term cannot overwrite');
console.log(checks+' list schedule checks passed');
