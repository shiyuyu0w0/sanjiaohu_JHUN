(function () {
  'use strict';
  function clean(s) { return String(s || '').replace(/\u00a0/g, ' ').trim(); }
  function term(s) {
    var m = clean(s).match(/(20\d{2})\s*[-－—]\s*(20\d{2})\s*学年?\s*第?([一二12])学期/);
    if (!m || +m[2] !== +m[1] + 1) return null;
    var half = /[一1]/.test(m[3]) ? 1 : 2;
    return {year:+m[1], half:half, label:m[1]+'-'+m[2]+'学年第'+(half===1?'一':'二')+'学期'};
  }
  function text(node) {return clean(node.innerText || node.textContent);}
  function cells(row) {return Array.from(row.cells || []).map(text);}
  function reportMatches(url, target) {
    try {
      var parsed = new URL(url), params = new URLSearchParams(atob(parsed.searchParams.get('params') || ''));
      return parsed.pathname === '/student/wsxk.xskcb10319.jsp' && +params.get('xn') === target.year && params.get('xq') === String(target.half - 1);
    } catch (ignored) {return false;}
  }
  function schedule(doc, target) {
    if (!reportMatches(doc.URL, target)) return {error:'waiting'};
    var tables = Array.from(doc.querySelectorAll('table'));
    var table = tables.find(function(t){return Array.from(t.rows).some(function(r){return cells(r).slice(-7).join('|')==='星期一|星期二|星期三|星期四|星期五|星期六|星期日';});});
    if (!table) return {error:'waiting'};
    var courses=[], seen={}, blocks={}, malformed=false;
    Array.from(table.rows).forEach(function(row){
      var cs=Array.from(row.cells), prefix=cs.slice(0,-7).map(text), block=prefix.find(function(s){return /^[一二三四五六]$/.test(s);});
      if (!block) return;
      blocks[block]=true;
      if(cs.length<8 || cs.slice(-7).some(function(c){return c.colSpan!==1 || c.rowSpan!==1;})){malformed=true;return;}
      cs.slice(-7).forEach(function(cell,day){
        var lines=text(cell).split(/\r?\n/).map(clean).filter(Boolean);
        if(!lines.length || lines.join('')==='—' || lines.join('')==='-')return;
        // The school's two-period grid repeats a four-line group per course.
        var offset=0;
        while(offset<lines.length){
          var marker=-1, match=null;
          for(var j=offset;j<lines.length;j++){
            var m=lines[j].match(/^([\d\s,，、\-－~～单双周()（）]+)\s*\[\s*(\d{1,2})\s*(?:[-－~～]\s*(\d{1,2}))?\s*\]$/);
            if(m){marker=j;match=m;break;}
          }
          if(marker<offset+2 || marker+1>=lines.length){malformed=true;break;}
          var item={name:lines.slice(offset,marker-1).join(' '),teacher:lines[marker-1],weekText:clean(match[1]),room:lines[marker+1],day:day+1,start:+match[2],end:+(match[3]||match[2])};
          if(item.start<1 || item.end>12 || item.start>item.end){malformed=true;break;}
          var key=JSON.stringify(item);if(!seen[key]){seen[key]=true;courses.push(item);}offset=marker+2;
        }
      });
    });
    if(malformed || Object.keys(blocks).length!==6) return {error:'structure',detail:'课表行列或课程字段不完整'};
    var maxWeek=20;courses.forEach(function(c){(c.weekText.match(/\d+/g)||[]).forEach(function(w){maxWeek=Math.max(maxWeek,+w);});});
    return {version:2,complete:true,term:target.label,maxWeek:maxWeek,courses:courses};
  }
  function grades(doc,target,emptyQueryVerified) {
    if(!/\/student\/xscj\.stuckcj_data\.jsp(?:[?#]|$)/.test(doc.URL))return {error:'waiting'};
    var body=text(doc.body), scope=body.match(/学年学期\s*[：:]\s*([^\r\n]+)/), actual=scope&&term(scope[1]);
    if(!/学生成绩明细\s*[\[【（(]\s*原始/.test(body))return {error:'waiting'};
    var empty=/没有检索到记录[!！]?/.test(body);
    if(actual?actual.label!==target.label:!(empty && emptyQueryVerified))return {error:'waiting'};
    var pages=[],pagePattern=/第\s*(\d+)\s*页\s*共\s*(\d+)\s*页/g,pageMatch;
    while((pageMatch=pagePattern.exec(body))!==null)pages.push(pageMatch);
    if(!pages.length)return {error:'structure',detail:'成绩分页信息缺失'};
    var total=+pages[0][2], available=new Set(pages.map(function(p){return +p[1];}));
    if(total<1 || pages.some(function(p){return +p[2]!==total;}) || available.size!==total)return {error:'incomplete',detail:'成绩尚有页面未加载'};
    for(var p=1;p<=total;p++)if(!available.has(p))return {error:'incomplete'};
    if(empty)return {version:1,complete:true,term:target.label,entries:[]};
    var columns=['序号','课程/环节','学分','总学时','类别','修读性质','考核方式','取得方式','成绩','备注'];
    var keys=['number','name','credits','hours','category','nature','assessment','method','score','note'];
    var entries=[], seen={}, found=false, malformed=false;
    Array.from(doc.querySelectorAll('table')).forEach(function(table){
      var rows=Array.from(table.rows), header=rows.find(function(r){var values=cells(r);return columns.every(function(c){return values.indexOf(c)>=0;});});
      if(!header)return;found=true;var headings=cells(header), indexes=columns.map(function(c){return headings.indexOf(c);});
      rows.forEach(function(row){
        var values=cells(row), ordinal=values[indexes[0]];
        if(!/^\d+$/.test(ordinal||''))return;
        if(values.length!==headings.length){malformed=true;return;}
        var entry={};keys.forEach(function(key,i){entry[key]=values[indexes[i]];});
        if(!entry.name){malformed=true;return;}
        if(seen[ordinal] && JSON.stringify(seen[ordinal])!==JSON.stringify(entry)){malformed=true;return;}
        if(!seen[ordinal]){seen[ordinal]=entry;entries.push(entry);}
      });
    });
    entries.sort(function(a,b){return +a.number-(+b.number);});
    if(!found || malformed || entries.some(function(e,i){return +e.number!==i+1;}))return {error:'structure',detail:'成绩表格不完整'};
    return {version:1,complete:true,term:target.label,entries:entries};
  }
  function effective(doc,target,queryVerified,all) {
    if(!/\/student\/xscj\.stuckcj_data\.jsp(?:[?#]|$)/.test(doc.URL))return {error:'waiting'};
    var body=text(doc.body);
    if(!/学生成绩明细\s*[\[【（(]\s*有效/.test(body))return {error:'waiting'};
    if(!queryVerified)return {error:'waiting'};
    var empty=/没有检索到记录[!！]?/.test(body),scopes=[],scopePattern=/学年学期\s*[：:]\s*([^\r\n]+)/g,m;
    while((m=scopePattern.exec(body))!==null){var value=term(m[1]);if(value)scopes.push(value.label);}
    if(!all && !empty && (!scopes.length || scopes.some(function(s){return s!==target.label;})))return {error:'waiting'};
    var pages=[],pagePattern=/第\s*(\d+)\s*页\s*共\s*(\d+)\s*页/g;
    while((m=pagePattern.exec(body))!==null)pages.push(m);
    if(!pages.length)return {error:'structure'};
    var total=+pages[0][2],available=new Set(pages.map(function(p){return +p[1];}));
    if(total<1 || available.size!==total || pages.some(function(p){return +p[2]!==total;}))return {error:'incomplete'};
    for(var p=1;p<=total;p++)if(!available.has(p))return {error:'incomplete'};
    var result={version:2,complete:true,scope:all?'all':'semester',term:all?'入学以来':target.label,mode:'effective',entries:[],summary:{groups:[],total:null}};
    if(empty)return result;
    var tables=Array.from(doc.querySelectorAll('table')),summaryFound=false,malformed=false,gradeFound=false,seen={};
    tables.forEach(function(table){
      var rows=Array.from(table.rows);
      var summaryHeader=rows.find(function(r){var values=cells(r);return ['修读课程环节数','获得学分','获得平均学分绩点','平均成绩','加权平均成绩'].every(function(h){return values.indexOf(h)>=0;});});
      if(summaryHeader){
        summaryFound=true;
        rows.forEach(function(r){
          if(r===summaryHeader)return;var values=cells(r);
          if(values.length!==9){if(values.some(Boolean))malformed=true;return;}
          // Report order: category, count, credits, earned credits, points, credit points, GPA, average, weighted average.
          var item={name:values[0],count:values[1],credits:values[2],earned:values[3],gpa:values[6],average:values[7],weighted:values[8]};
          if(!item.name || !/^\d+$/.test(item.count)){malformed=true;return;}
          if(item.name==='合计')result.summary.total=item;else result.summary.groups.push(item);
        });
        return;
      }
      if(all)return;
      var headings=['序号','课程/环节','学分','总学时','类别','修读性质','考核方式','成绩','获得学分','绩点','学分绩点','备注'];
      var keys=['number','name','credits','hours','category','nature','assessment','score','earned','point','creditPoint','note'];
      var header=rows.find(function(r){var values=cells(r);return headings.every(function(h){return values.indexOf(h)>=0;});});
      if(!header)return;gradeFound=true;var columns=cells(header),indexes=headings.map(function(h){return columns.indexOf(h);});
      rows.forEach(function(r){
        var values=cells(r),ordinal=values[indexes[0]];
        if(!/^\d+$/.test(ordinal||''))return;
        if(values.length!==columns.length){malformed=true;return;}
        var entry={method:''};keys.forEach(function(k,i){entry[k]=values[indexes[i]];});
        if(!entry.name){malformed=true;return;}
        if(seen[ordinal] && JSON.stringify(seen[ordinal])!==JSON.stringify(entry)){malformed=true;return;}
        if(!seen[ordinal]){seen[ordinal]=entry;result.entries.push(entry);}
      });
    });
    if(malformed || !summaryFound || !result.summary.total)return {error:'structure',detail:'有效成绩汇总未加载完整'};
    if(!all){
      result.entries.sort(function(a,b){return +a.number-(+b.number);});
      if(!gradeFound || result.entries.some(function(e,i){return +e.number!==i+1;}) || +result.summary.total.count!==result.entries.length)return {error:'incomplete'};
    }
    return result;
  }
  return {term:term,schedule:schedule,grades:grades,effective:effective,reportMatches:reportMatches};
})()
