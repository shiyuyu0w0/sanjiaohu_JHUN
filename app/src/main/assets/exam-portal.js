(function(job,requested,parser){
  'use strict';
  var state=window.__sanjiaohuExamJob;
  if(!state||state.id!==job)state=window.__sanjiaohuExamJob={id:job,phase:'menu',target:parser.term(requested),terms:[],entries:[],seen:{},index:0};
  function documents(d,depth,out){out.push(d);if(depth<6)Array.from(d.querySelectorAll('iframe,frame')).forEach(function(f){try{if(f.contentDocument)documents(f.contentDocument,depth+1,out);}catch(e){}});return out;}
  var docs=documents(document,0,[]);
  function result(data){data.terms=state.terms;if(state.target)data.selectedTerm=state.target.label;return data;}
  function waiting(){return result({error:'waiting'});}
  function path(d,p){try{return new URL(d.URL).origin==='https://jwxt.jhun.edu.cn'&&new URL(d.URL).pathname===p;}catch(e){return false;}}
  if(docs.some(function(d){return d.querySelector('input[type=password]');}))return result({error:'login'});
  if(state.phase==='menu'){
    var menu=document.querySelector('.menu-item[data-code="S204"]');if(menu){menu.click();state.phase='tab';}return waiting();
  }
  if(state.phase==='tab'){
    var desk=docs.find(function(d){return path(d,'/frame/jw/teacherstudentmenu.jsp')&&new URL(d.URL).searchParams.get('menucode')==='S204';});
    if(!desk)return waiting();
    var tab=Array.from(desk.querySelectorAll('a')).find(function(a){return a.textContent.trim()==='考试安排表'&&/#content_3$/.test(a.getAttribute('href')||'');});
    if(!tab)return waiting();tab.click();state.phase='term';return waiting();
  }
  var form=docs.find(function(d){return path(d,'/student/ksap.ksapb.html')&&d.querySelector('#xnxq')&&d.querySelector('#kslc')&&d.querySelector('#btnQry');});
  if(!form||form.readyState!=='complete')return waiting();
  var terms=form.querySelector('#xnxq'),rounds=form.querySelector('#kslc');
  if(state.phase==='term'){
    state.terms=Array.from(terms.options).map(function(o){return parser.term(o.textContent);}).filter(Boolean).map(function(t){return t.label;});
    if(!state.terms.length)return waiting();
    if(!state.target){
      var current=terms.options[terms.selectedIndex];state.target=parser.term(current&&current.textContent)||parser.term(document.body&&(document.body.innerText||document.body.textContent));
      if(!state.target){var published=state.terms.map(parser.term).sort(function(a,b){return b.year-a.year||b.half-a.half;});state.target=published[0];}
    }
    if(!state.target)return waiting();
    var option=Array.from(terms.options).find(function(o){var t=parser.term(o.textContent);return t&&t.label===state.target.label;});
    if(!option)return result({error:'unavailable'});
    if(terms.value!==option.value){terms.value=option.value;terms.dispatchEvent(new Event('change',{bubbles:true}));}
    state.phase='rounds';return waiting();
  }
  if(state.phase==='rounds'){
    var catalog=Array.from(rounds.options).filter(function(o){var t=parser.term(o.textContent);return o.value&&t&&t.label===state.target.label;}).map(function(o){return {value:o.value,label:o.textContent.trim()};});
    // Wait for asynchronous term-dependent options to settle before reading all rounds.
    if(!catalog.length)return waiting();var signature=JSON.stringify(catalog);
    if(state.catalogSignature!==signature){state.catalogSignature=signature;return waiting();}
    state.rounds=catalog;state.phase='query';
  }
  if(state.phase==='query'){
    var round=state.rounds[state.index],chosen=Array.from(rounds.options).find(function(o){return o.value===round.value&&o.textContent.trim()===round.label;});
    var selected=parser.term(terms.options[terms.selectedIndex].textContent);
    if(!chosen||!selected||selected.label!==state.target.label)return result({error:'structure'});
    rounds.value=chosen.value;state.oldReports=docs.filter(function(d){return path(d,'/student/ksap.ksapb_date.jsp');});
    form.querySelector('#btnQry').click();state.phase='read';return waiting();
  }
  if(state.phase==='read'){
    var reports=docs.filter(function(d){return d.readyState==='complete'&&path(d,'/student/ksap.ksapb_date.jsp')&&state.oldReports.indexOf(d)<0;});
    for(var i=0;i<reports.length;i++){
      var parsed=parser.exams(reports[i],state.target,state.rounds[state.index].label,true);
      if(parsed.error){if(parsed.error!=='waiting')return result(parsed);continue;}
      parsed.entries.forEach(function(e){var key=JSON.stringify(e);if(!state.seen[key]){state.seen[key]=true;state.entries.push(e);}});
      state.index++;if(state.index<state.rounds.length){state.phase='query';return waiting();}
      return result({version:1,complete:true,term:state.target.label,entries:state.entries,rounds:state.rounds.map(function(r){return r.label;})});
    }
  }
  return waiting();
})
