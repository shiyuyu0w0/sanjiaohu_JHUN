(function(job,requested,parser){
  'use strict';
  var state=window.__sanjiaohuListJob;
  if(!state||state.id!==job)state=window.__sanjiaohuListJob={id:job,phase:'menu',target:parser.term(requested),terms:[]};
  var docs=parser.documents(document);
  function result(data){data.terms=state.terms;if(state.target)data.selectedTerm=state.target.label;return data;}
  function wait(stage){return result({error:'waiting',stage:stage});}
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
  return result(parsed);
})
