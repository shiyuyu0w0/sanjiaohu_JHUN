(function(parser){
  'use strict';
  var fields=['周次','节次','地点','教师','授课方式'];
  function text(node){return String(node&&(node.innerText||node.textContent)||'').replace(/\u00a0/g,' ').trim();}
  function compact(value){return value.replace(/\s/g,'');}
  function nameKey(value){return value.trim().replace(/\s+/g,' ');}
  function roomKey(value){return nameKey(value.replace(/[－—–]/g,'-').replace(/[(（]\d+[)）]$/, ''));}
  function changeTable(table){
    return Array.from(table.rows).some(function(row){
      var cells=Array.from(row.cells).map(text);
      return cells.indexOf('调课项目')>=0&&cells.indexOf('调课前')>=0&&cells.indexOf('调课后')>=0;
    });
  }
  function readFields(table){
    var values={};
    Array.from(table.rows).forEach(function(row){
      var cells=Array.from(row.cells),label=compact(text(cells[1]));
      if(cells.length!==4||fields.indexOf(label)<0)return;
      var flag=cells[0].querySelector('input[type=checkbox]');
      if(!flag||values[label])throw Error('调课项目未能确认');
      values[label]={checked:flag.checked,before:text(cells[2]),after:text(cells[3])};
    });
    return values;
  }
  function noticeContext(table,tables){
    var identity=null;
    for(var node=table.parentElement;node;node=node.parentElement){
      if(tables.some(function(other){return other!==table&&node.contains(other);}))break;
      var content=text(node);
      var course=content.match(/课\s*程\s*[:：]\s*(\[[^\]\r\n]+\][^\r\n]+?)(?=\s*上课班号\s*[:：]|\r?\n|$)/);
      var number=content.match(/上课班号\s*[:：]\s*(\d{1,8})(?=\s|$)/);
      var label=course&&parser.codedName(course[1]);
      if(!label||!number)continue;
      identity={classKey:parser.classKey(label.code,number[1]),name:label.name};
      var time=content.match(/调课时间\s*[:：]\s*(20\d{2}-\d{2}-\d{2})\s+(\d{2}:\d{2}(?::\d{2})?)/);
      if(!time)continue;
      var clock=time[2].length===5?time[2]+':00':time[2];
      identity.timestamp=Date.parse(time[1]+'T'+clock+'+08:00');
      identity.cancelOrMakeup=/(?:^|[\t\n])\s*(?:停课|补课)\s*(?=[\t\n]|$)/.test(content);
      return identity;
    }
    return identity;
  }
  function teacher(value){
    var who=parser.codedName(value);
    if(!who||/[\[\]\r\n,，、；;\/／]/.test(who.name))throw Error('任课教师身份无法确认');
    return who;
  }
  function placement(rows,side){
    var weeks=parser.weeks(rows['周次'][side]);
    var slot=compact(rows['节次'][side]).replace(/[（(]/g,'[').replace(/[）)]/g,']').replace(/[－—–~～至]/g,'-')
      .match(/^(?:星期|周)?([一二三四五六日天1-7])\[(\d{1,2})(?:-(\d{1,2}))?节?\]$/);
    if(!weeks.length||!slot)throw Error('调课节次无法确认');
    var day='一二三四五六日'.indexOf(slot[1])+1||(slot[1]==='天'?7:+slot[1]);
    var start=+slot[2],end=+(slot[3]||slot[2]);
    if(start<1||end>12||start>end)throw Error('调课节次超出范围');
    return {weeks:weeks,day:day,start:start,end:end,room:roomKey(rows['地点'][side])};
  }
  function readNotice(table,tables,term){
    var rows=readFields(table),change=rows['教师'];
    if(!change)throw Error('缺少教师栏');
    if(!change.checked)return null;
    if(!fields.every(function(field){return rows[field];}))throw Error('调课范围不完整');
    var notice=noticeContext(table,tables);
    if(!notice||!Number.isFinite(notice.timestamp))throw Error('调课通知身份不完整');
    if(notice.cancelOrMakeup)return null;
    var before=teacher(change.before),after=teacher(change.after);
    if(before.code===after.code)return null;
    // A delivery-method change can accompany a teacher change at the same slot.
    // Let the timetable handle moves. An uncertain move also invalidates older overrides for this class.
    var slot=placement(rows,'before');
    if(JSON.stringify(slot)!==JSON.stringify(placement(rows,'after')))throw Error('排课范围同时变更');
    return Object.assign({term:term,classKey:notice.classKey,name:notice.name,timestamp:notice.timestamp,before:before,after:after},slot);
  }
  function headingTerm(section){
    var heading=section.match(/^\s*[:：（(]?\s*(20\d{2}\s*[-－—]\s*20\d{2}\s*学年?\s*第?[一二12]学期)/);
    return heading&&parser.term(heading[1]);
  }
  function parse(doc,target,diagnostics){
    if(!parser.school(doc)||new URL(doc.URL).pathname!=='/student/jxap.jxaptzxx_rpt.jsp'||doc.readyState!=='complete')return null;
    var body=text(doc.body),term=parser.term(body);
    if((term&&term.label!==target.label)||/正在加载|查询中|请稍候|系统错误|查询失败|服务器异常|登录超时/.test(body)||!parser.pagesComplete(body))return null;
    var tables=Array.from(doc.querySelectorAll('table')).filter(changeTable);
    if(!tables.length)return /没有检索到记录|无调课记录|暂无调课信息/.test(body)?[]:null;
    if(!term||!/调课通知/.test(body))return null;
    // Each printed notice has its own heading. Reject mixed or ambiguous sets before any overlay.
    var headings=body.split(/江汉大学\s*调课通知/).slice(1);
    if(headings.length!==tables.length||headings.some(function(section){
      var label=headingTerm(section);return !label||label.label!==target.label;
    }))return null;
    var notices=[],blocked=new Set(),skipped=0;
    for(var i=0;i<tables.length;i++){
      var table=tables[i];
      try{
        var notice=readNotice(table,tables,target.label);
        if(notice)notices.push(notice);
      }catch(error){
        // An unreadable notice can break the history for its class. Keep other classes usable.
        var scope=noticeContext(table,tables);
        if(!scope)return null;
        blocked.add(scope.classKey);skipped++;
      }
    }
    var usable=notices.filter(function(notice){return !blocked.has(notice.classKey);});
    if(diagnostics)diagnostics.skipped=skipped+notices.length-usable.length;
    return usable;
  }
  function slotKey(course,week,period){
    return JSON.stringify([course.classKey,nameKey(course.name),course.day,roomKey(course.room),week,period]);
  }
  function sameTeacher(a,b){return a.code===b.code&&nameKey(a.name)===nameKey(b.name);}
  function finalTeacher(changes,course,issues){
    var chain=changes.slice().sort(function(a,b){return a.timestamp-b.timestamp;}),previous;
    var original={code:course.teacherCode,name:course.teacher},found=false;
    for(var i=0;i<chain.length;i++){
      var step=chain[i];
      if(previous){
        if(sameTeacher(previous.before,step.before)&&sameTeacher(previous.after,step.after))continue;
        if(previous.timestamp===step.timestamp||!sameTeacher(previous.after,step.before)){
          issues.add('notice-conflict');return null;
        }
      }
      if(sameTeacher(original,step.before)||sameTeacher(original,step.after))found=true;
      previous=step;
    }
    if(!found){issues.add('teacher-mismatch');return null;}
    return previous.after.code!==course.teacherCode?previous.after:null;
  }
  function apply(schedule,changes,diagnostics){
    if(!changes.length)return schedule;
    var slots=new Map(),updated=new Map(),classes=new Set(),issues=new Set();
    function sectionKey(item){return JSON.stringify([item.classKey,nameKey(item.name)]);}
    schedule.courses.forEach(function(course){
      if(!course.classKey)return;
      classes.add(sectionKey(course));
      parser.weeks(course.weekText).forEach(function(week){
        for(var period=course.start;period<=course.end;period++){
          var key=slotKey(course,week,period);
          slots.set(key,slots.has(key)?null:{course:course,week:week,period:period,changes:[]});
        }
      });
    });
    changes.forEach(function(notice){
      if(notice.term!==schedule.term){issues.add('term-mismatch');return;}
      if(!classes.has(sectionKey(notice))){issues.add('class-mismatch');return;}
      notice.weeks.forEach(function(week){
        var matched=[];
        for(var period=notice.start;period<=notice.end;period++){
          var slot=slots.get(slotKey(notice,week,period));
          if(!slot){issues.add(slot===null?'ambiguous-slot':'slot-mismatch');return;}
          matched.push(slot);
        }
        matched.forEach(function(slot){slot.changes.push(notice);});
      });
    });
    slots.forEach(function(slot){
      if(!slot||!slot.changes.length)return;
      var who=finalTeacher(slot.changes,slot.course,issues);
      if(!who)return;
      if(!updated.has(slot.course))updated.set(slot.course,new Map());
      updated.get(slot.course).set(slot.week*100+slot.period,who);
    });
    if(diagnostics)diagnostics.issues=Array.from(issues);
    if(!updated.size)return schedule;
    var courses=[];
    schedule.courses.forEach(function(course){
      var replacements=updated.get(course);
      if(!replacements){courses.push(course);return;}
      var groups=new Map();
      parser.weeks(course.weekText).forEach(function(week){
        for(var start=course.start;start<=course.end;){
          var who=replacements.get(week*100+start),end=start;
          while(end<course.end&&replacements.get(week*100+end+1)===who)end++;
          var key=JSON.stringify([start,end,who&&who.code]);
          if(!groups.has(key))groups.set(key,{start:start,end:end,who:who,weeks:[]});
          groups.get(key).weeks.push(week);start=end+1;
        }
      });
      groups.forEach(function(group){
        var coursePart=Object.assign({},course,{start:group.start,end:group.end,weekText:group.weeks.join(',')});
        if(group.who){coursePart.teacher=group.who.name;coursePart.teacherCode=group.who.code;}
        courses.push(coursePart);
      });
    });
    return Object.assign({},schedule,{courses:courses,teacherUpdated:true});
  }
  return {parse:parse,apply:apply};
})
