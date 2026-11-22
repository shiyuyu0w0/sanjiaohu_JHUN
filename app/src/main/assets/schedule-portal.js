(function(job,requested,parser,createTeacherChanges,cachedChanges){
  'use strict';
  var teacherChanges=createTeacherChanges?createTeacherChanges(parser):null;
  var state=window.__sanjiaohuListJob;
  if(!state||state.id!==job)state=window.__sanjiaohuListJob={id:job,phase:'menu',target:parser.term(requested),terms:[]};
  var docs=parser.documents(document);
  function result(data){data.terms=state.terms;if(state.target)data.selectedTerm=state.target.label;return data;}
  function wait(stage){return result({error:'waiting',stage:stage});}
  function finishChanges(changes,skipped){
    var base=state.schedule,schedule=base;
    // Only reuse records whose term was explicitly verified by the current parser.
    var cached=Array.isArray(cachedChanges)?cachedChanges.filter(function(change){return change&&change.term===base.term;}):[];
    var known=changes===null?cached:changes,diagnostics={};
    try{
      var merged=teacherChanges.apply(base,known,diagnostics);
      if(!merged||merged.term!==base.term||merged.version!==base.version||!merged.complete||
          !Array.isArray(merged.courses)||(!merged.courses.length&&base.courses.length))throw Error('教师变更结果不完整');
      schedule=merged;
    }catch(error){changes=null;known=cached;}
    var issues=diagnostics.issues||[];
    var teacherSync=changes===null?'unavailable':skipped||issues.length?'partial':'checked';
    return result(Object.assign({},schedule,{teacherChanges:known,teacherSync:teacherSync,teacherMatchIssues:issues}));
  }
  function pollChanges(){
    if(Date.now()>=state.changeDeadline||docs.some(function(d){return d.querySelector('input[type=password]');}))return finishChanges(null);
    var form=docs.find(function(d){return new URL(d.URL).pathname==='/student/jxap.jxaptzxx.html';});
    if(!form||form.readyState!=='complete')return wait('teacher-changes');
    var selector=form.querySelector('#xnxq'),button=form.querySelector('#btnSubmit'),frames=form.querySelectorAll('iframe,frame');
    var frame=frames.length===1?frames[0]:form.querySelector('#frmReport');
    if(!selector||!button||!frame)return wait('teacher-changes');
    var report=frame.contentDocument;
    if(state.phase==='teacher-query'){
      var option=Array.from(selector.options).find(function(o){var term=parser.term(o.textContent);return term&&term.label===state.target.label;});
      if(!option)return wait('teacher-changes');
      selector.value=option.value;
      if(selector.value!==option.value)return finishChanges(null);
      state.changeForm=form;state.changeFrame=frame;state.changeTerm=option.value;state.oldChangeReport=report;
      button.click();state.phase='teacher-read';return wait('teacher-changes');
    }
    if(form!==state.changeForm||frame!==state.changeFrame||selector.value!==state.changeTerm)return finishChanges(null);
    if(!report||report===state.oldChangeReport)return wait('teacher-changes');
    var diagnostics={};
    var changes=teacherChanges.parse(report,state.target,diagnostics);
    if(changes===null){state.changesSignature=null;return wait('teacher-changes');}
    var signature=JSON.stringify([changes,diagnostics.skipped]);
    if(signature!==state.changesSignature){state.changesSignature=signature;return wait('teacher-changes');}
    return finishChanges(changes,diagnostics.skipped);
  }
  if(state.phase==='teacher-query'||state.phase==='teacher-read'){
    try{return pollChanges();}catch(error){return finishChanges(null);}
  }
  if(docs.some(function(d){return d.querySelector('input[type=password]');}))return result({error:'login'});
  if(state.phase==='menu'){
    var menu=document.querySelector('.menu-item[data-code="S203"]');if(menu){menu.click();state.phase='tab';}return wait('schedule-menu');
  }
  if(state.phase==='tab'){
    var tab;docs.some(function(d){tab=Array.from(d.querySelectorAll('a')).find(function(a){return /^个人课表$/.test((a.textContent||'').trim());});return !!tab;});
    if(tab){tab.click();state.phase='term';return wait('schedule-tab');}
    if(docs.some(function(d){return d.querySelector('#xnxq')&&d.querySelector('#cxfs_lb')&&d.querySelector('#frmReport');}))state.phase='term';
    else return wait('schedule-tab');
  }
  var form=docs.find(function(d){return d.querySelector('#xnxq')&&d.querySelector('#cxfs_lb')&&d.querySelector('#frmReport');});
  if(!form||form.readyState!=='complete')return wait('schedule-form');
  var selector=form.querySelector('#xnxq'),list=form.querySelector('#cxfs_lb'),frame=form.querySelector('#frmReport'),report;
  try{report=frame.contentDocument;}catch(e){return wait('report-loading');}
  if(state.phase==='term'){
    state.terms=Array.from(selector.options).map(function(o){return parser.term(o.textContent);}).filter(Boolean).map(function(t){return t.label;});
    if(!state.terms.length)return wait('term-loading');
    if(!state.target){var selected=selector.options[selector.selectedIndex];state.target=parser.term(selected&&selected.textContent)||parser.term(document.body&&(document.body.innerText||document.body.textContent))||parser.term(state.terms[0]);}
    var option=Array.from(selector.options).find(function(o){var t=parser.term(o.textContent);return t&&t.label===state.target.label;});
    if(!option)return result({error:'unavailable'});
    state.form=form;state.termValue=option.value;
    if(selector.value!==option.value){selector.value=option.value;selector.dispatchEvent(new Event('change',{bubbles:true}));}
    state.phase='list';return wait('term-switching');
  }
  if(form!==state.form||selector.value!==state.termValue)return result({error:'structure',stage:'selection-changed'});
  if(state.phase==='list'){
    // Clicking the school's list radio runs its own doQuery(), including on a refresh.
    state.oldReport=report;state.loaded=false;state.loadListener=function(){state.loaded=true;};frame.addEventListener('load',state.loadListener,{once:true});
    list.click();state.phase='read';return wait('list-switching');
  }
  if(!list.checked)return result({error:'structure',stage:'list-switch-failed'});
  if(!report||!parser.school(report)||report.readyState!=='complete'||(report===state.oldReport&&!state.loaded))return wait('report-loading');
  var parsed=parser.parse(report,state.target,true);
  if(parsed.error)return result(parsed);
  // Printed pages can be appended after readyState=complete. Require a stable snapshot.
  var signature=JSON.stringify(parsed);if(signature!==state.signature){state.signature=signature;return wait('report-settling');}
  if(teacherChanges&&parsed.courses.length){
    state.schedule=parsed;
    var changesTab;
    docs.some(function(d){changesTab=Array.from(d.querySelectorAll('a')).find(function(a){return (a.textContent||'').trim()==='调课信息';});return !!changesTab;});
    if(!changesTab)return finishChanges(null);
    state.phase='teacher-query';state.changeDeadline=Date.now()+8000;
    try{changesTab.click();}catch(error){return finishChanges(null);}
    return wait('teacher-changes');
  }
  return result(parsed);
})
