(function(){
  'use strict';
  function clean(s){return String(s||'').replace(/\u00a0/g,' ').trim();}
  function text(n){return clean(n&&(n.innerText||n.textContent));}
  function compact(s){return clean(s).replace(/\s/g,'');}
  function normalized(s){return clean(s).replace(/[－—–～~至]/g,'-').replace(/[，、]/g,',').replace(/（/g,'(').replace(/）/g,')').replace(/：/g,':');}
  function term(s){var m=clean(s).match(/(20\d{2})\s*[-－—]\s*(20\d{2})\s*学年?\s*第?([一二12])学期/);if(!m||+m[2]!==+m[1]+1)return null;return {year:+m[1],half:/[一1]/.test(m[3])?1:2,label:m[1]+'-'+m[2]+'学年第'+(/[一1]/.test(m[3])?'一':'二')+'学期'};}
  function school(doc){try{return new URL(doc.URL).origin==='https://jwxt.jhun.edu.cn';}catch(e){return false;}}
  // Work-list traversal handles deeply nested reports and repeated frame references.
  function documents(root){var queue=[root],seen=new Set(),out=[];while(queue.length){var d=queue.shift();if(!d||seen.has(d)||!school(d))continue;seen.add(d);out.push(d);Array.from(d.querySelectorAll('iframe,frame')).forEach(function(f){try{if(f.contentDocument)queue.push(f.contentDocument);}catch(e){}});}return out;}
  var aliases={name:['课程','课程名称','课程名','课程/环节','环节'],code:['课程代码','课程编号','课程号','课号'],teacher:['教师','任课教师','授课教师','指导教师'],credits:['学分'],time:['上课时间及地点','上课时间地点','上课时间','时间地点','上课安排'],weeks:['起止周','上课周次','周次'],room:['上课地点','地点','场地','教室'],day:['星期','上课星期'],period:['节次','上课节次']};
  function columns(row){var out={};Object.keys(aliases).forEach(function(k){out[k]=row.findIndex(function(c){return aliases[k].indexOf(compact(c))>=0;});});return out;}
  function courseHeader(c){return c.name>=0&&c.credits>=0&&(c.time>=0||(c.weeks>=0&&c.day>=0&&c.period>=0));}
  function activityHeader(row){var c=columns(row);return row.some(function(v){return compact(v)==='环节';})&&c.name>=0&&c.credits>=0&&c.weeks>=0&&c.room>=0;}
  function hiddenCell(c){var view=c.ownerDocument&&c.ownerDocument.defaultView;return c.hidden||(c.style&&c.style.display==='none')||(view&&view.getComputedStyle(c).display==='none');}
  function displayName(s){return clean(s.replace(/^\[[^\]]+\]\s*/,''));}
  function grid(table){
    var result=[],spans=[];
    Array.from(table.rows).forEach(function(row){var cells=[],index=0;spans.forEach(function(span,i){if(span&&span.left>0){cells[i]=span.value;span.left--;}});
      Array.from(row.cells).forEach(function(c){if(hiddenCell(c))return;while(cells[index]!==undefined)index++;var width=c.colSpan||1,height=c.rowSpan||1;if(width>40||height>500)throw Error('合并单元格异常');for(var i=0;i<width;i++){cells[index]=text(c);if(height>1)spans[index]={value:text(c),left:height-1};index++;}});result.push(cells);
    });return result;
  }
  function weeks(value){
    var s=normalized(value).replace(/第|周|\s/g,'').replace(/[()]/g,''),odd=s.indexOf('单')>=0,even=s.indexOf('双')>=0;s=s.replace(/[单双]/g,'');
    if(odd&&even)throw Error('周次单双冲突');var out=[];
    s.split(',').forEach(function(part){var m=part.match(/^(\d{1,2})(?:-(\d{1,2}))?$/);if(!m)throw Error('无法识别周次');var a=+m[1],b=+(m[2]||m[1]);if(a===0&&b===0)return;if(a<1||a>b||b>60)throw Error('周次超出范围');for(var w=a;w<=b;w++)if((!odd||w%2===1)&&(!even||w%2===0)&&out.indexOf(w)<0)out.push(w);});return out.sort(function(a,b){return a-b;});
  }
  function weekMatch(s){return s.match(/(?:第\s*)?\d{1,2}(?:\s*[-,]\s*\d{1,2})*\s*(?:\([单双]\)|[单双])?\s*\(?周\)?\s*(?:\([单双]\)|[单双])?/);}
  function periods(value){var s=normalized(value).replace(/第|节|[\[\]()\s]/g,'');if(!/^\d{1,2}(?:[-,]\d{1,2})*$/.test(s))throw Error('无法识别节次');var out=[];s.split(',').forEach(function(part){var p=part.split('-'),a=+p[0],b=+(p[1]||p[0]);if(a<1||b>12||a>b)throw Error('节次超出范围');out.push([a,b]);});return out;}
  function arrangements(raw,shared){
    // The school's list abbreviates 星期二[5-6] as 二[5-6]. Only expand
    // a standalone weekday immediately followed by a bracketed period.
    var source=normalized(raw).replace(/(^|[\s,;；])([一二三四五六日天])(?=\s*\[\s*\d)/g,'$1星期$2').replace(/((?:第\s*)?\d{1,2}(?:\s*[-,]\s*\d{1,2})*\s*(?:\([单双]\)|[单双])?\s*\(?周\)?\s*(?:\([单双]\)|[单双])?)(?=\s*(?:星期|周)[一二三四五六日天1-7])/g,';$1');
    var lines=source.split(/[\n;；]+/).map(clean).filter(Boolean),out=[];
    if(shared.day&&!/(?:星期|周)[一二三四五六日天1-7]/.test(source))lines=['星期'+shared.day.replace(/星期|周/g,'')+' '+(raw||'')+' '+shared.period+'节 '+(shared.weeks||'')+'周 '+shared.room];
    var joined=[];lines.forEach(function(line){var wm=weekMatch(line);if(wm&&clean(line.replace(wm[0],''))===''){out.push({unscheduled:true,note:line+'（未公布星期和节次）'});return;}if(joined.length&&!/(?:星期|周)\s*[一二三四五六日天1-7]/.test(line))joined[joined.length-1]+=' '+line;else joined.push(line);});lines=joined;
    lines.forEach(function(line){
      var matcher=/(?:星期|周)\s*([一二三四五六日天1-7])/g,hits=[],m;while((m=matcher.exec(line))!==null)hits.push({index:m.index,end:matcher.lastIndex,day:'一二三四五六日'.indexOf(m[1])+1|| (m[1]==='天'?7:+m[1])});
      if(!hits.length)throw Error('无法识别星期');
      var prefix=line.slice(0,hits[0].index),prefixWeek=weekMatch(prefix);
      hits.forEach(function(hit,i){var tail=line.slice(hit.end,i+1<hits.length?hits[i+1].index:line.length),wm=weekMatch(tail),weekText=shared.weeks||(wm&&wm[0])||(prefixWeek&&prefixWeek[0]);if(!weekText)throw Error('缺少周次');var ws=weeks(weekText);if(!ws.length){out.push({zero:true});return;}
        if(wm)tail=tail.replace(wm[0],'');
        var pm=tail.match(/[\[(]\s*\d{1,2}(?:\s*[-,]\s*\d{1,2})*\s*(?:节)?\s*[\])]/)||tail.match(/(?:第\s*)?\d{1,2}(?:\s*[-,]\s*\d{1,2})*\s*节/);
        var ps=shared.period||pm&&pm[0];if(!ps)throw Error('缺少节次');if(pm)tail=tail.replace(pm[0],'');
        var room=shared.room||clean(tail.replace(/\(\d+\)\s*$/,'').replace(/^[\s,:：\[\]()]+|[\s\[\]()]+$/g,''));
        periods(ps).forEach(function(p){out.push({day:hit.day,start:p[0],end:p[1],weekText:ws.join(','),room:room});});
      });
    });return out;
  }
  function parse(doc,target,verified){
    if(!school(doc)||!target||!verified||doc.readyState!=='complete')return {error:'waiting',stage:'report-loading'};
    var body=text(doc.body),actual=term(body);if(actual&&actual.label!==target.label)return {error:'waiting',stage:'term-mismatch'};
    if(/正在加载|正在查询|查询中|请稍候/.test(body))return {error:'waiting',stage:'report-loading'};
    if(/系统错误|查询失败|服务器异常|登录超时/.test(body))return {error:'structure',stage:'report-error'};
    var pages=[],pattern=/第\s*(\d+)\s*页\s*共\s*(\d+)\s*页/g,match;
    while((match=pattern.exec(body))!==null)pages.push([+match[1],+match[2]]);
    if(pages.length){var total=pages[0][1],seenPages=new Set(pages.map(function(p){return p[0];}));if(total<1||pages.some(function(p){return p[1]!==total;})||seenPages.size!==total)return {error:'incomplete',stage:'report-pagination'};for(var p=1;p<=total;p++)if(!seenPages.has(p))return {error:'incomplete',stage:'report-pagination'};}
    var summary=body.match(/(?:课程门数|课程数量|课程数)\s*[:：]?\s*(\d+)/),expected=summary?+summary[1]:null;
    var result={version:2,complete:true,term:target.label,maxWeek:20,courses:[],issues:[],unscheduled:[],ignoredZero:0,merged:0,reportRows:0},found=false;
    try{Array.from(doc.querySelectorAll('table')).forEach(function(table){var rows=grid(table),header=rows.findIndex(function(r){return courseHeader(columns(r))||activityHeader(r);});if(header<0)return;found=true;var cols=columns(rows[header]),activity=activityHeader(rows[header]);
      rows.slice(header+1).forEach(function(row){
        if(courseHeader(columns(row))||activityHeader(row)||!row.some(function(c){return clean(c);}))return;
        if(/^合计|^总计|^课程门数/.test(clean(row[0])))return;
        if(!activity)result.reportRows++;var get=function(k){return cols[k]<0?'':clean(row[cols[k]]);},name=displayName(get('name')),teacher=displayName(get('teacher')),raw=get('time');
        if(row.length!==rows[header].length){result.issues.push({name:name||'第 '+result.reportRows+' 行',reason:'课程行字段不完整'});return;}
        if(!name){result.issues.push({name:'第 '+result.reportRows+' 行',reason:'缺少课程名称'});return;}
        if(activity){result.unscheduled.push({name:name,teacher:teacher,note:(get('weeks')?'第 '+get('weeks')+' 周 · ':'')+get('room')+'（未公布星期和节次）'});return;}
        if(!get('day')&&!/(?:星期|周)\s*[一二三四五六日天1-7]|[一二三四五六日天]\s*\[/.test(raw)&&/超星|学习通|雨课堂|网络课|线上课|慕课/.test(raw)){result.unscheduled.push({name:name,teacher:teacher,note:raw});return;}
        if((!raw&&!get('day'))||/^(?:无|待定|未安排|另行通知|网络课程|线上课程|慕课|不排课|时间待定)$/.test(raw)){result.unscheduled.push({name:name,teacher:teacher,note:raw||'未公布上课安排'});return;}
        try{var parsed=arrangements(raw,{weeks:get('weeks'),room:get('room'),day:get('day'),period:get('period')});parsed.forEach(function(c){if(c.zero){result.ignoredZero++;return;}if(c.unscheduled){result.unscheduled.push({name:name,teacher:teacher,note:c.note});return;}c.name=name;c.teacher=teacher;result.courses.push(c);});}
        catch(e){result.issues.push({name:name,reason:e.message,arrangement:raw.slice(0,600)});}
      });
    });}catch(e){return {error:'structure',stage:'table-structure'};}
    if(expected!==null&&result.reportRows<expected)return {error:'incomplete',stage:'report-pagination'};
    var explicitEmpty=/没有检索到记录|暂无课程|暂无课表|没有课程|未安排课程/.test(body)||expected===0;
    if(!found&&!explicitEmpty)return {error:'waiting',stage:'report-not-found'};
    if(!result.reportRows&&!explicitEmpty)return {error:'waiting',stage:'empty-unconfirmed'};
    if(result.issues.length&&!result.courses.length)return {error:'structure',stage:'rows-unreadable'};
    var unique={};result.courses.forEach(function(c){var key=JSON.stringify([c.name,c.teacher,c.day,c.start,c.end,c.room]);if(unique[key]){unique[key].weekText=Array.from(new Set(unique[key].weekText.split(',').concat(c.weekText.split(',')))).map(Number).sort(function(a,b){return a-b;}).join(',');result.merged++;}else unique[key]=c;});
    result.courses=Object.keys(unique).sort().map(function(k){return unique[k];});result.courses.forEach(function(c){c.weekText.split(',').forEach(function(w){result.maxWeek=Math.max(result.maxWeek,+w);});});
    result.requiresReview=result.issues.length>0;return result;
  }
  return {term:term,documents:documents,school:school,parse:parse,weeks:weeks,arrangements:arrangements};
})()
