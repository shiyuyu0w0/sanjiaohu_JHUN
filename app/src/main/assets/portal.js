(function (job, kind, requested, parser) {
  'use strict';
  var state=window.__sanjiaohuJob;
  if(!state || state.id!==job)state=window.__sanjiaohuJob={id:job,phase:kind==='summary'?'grades-menu':'schedule-menu',target:parser.term(requested),terms:[]};
  function documents(doc,depth,out){
    out.push(doc);
    if(depth<5)Array.from(doc.querySelectorAll('iframe,frame')).forEach(function(frame){try{if(frame.contentDocument)documents(frame.contentDocument,depth+1,out);}catch(ignored){}});
    return out;
  }
  var docs=documents(document,0,[]);
  function result(data){data.terms=state.terms;if(state.target)data.selectedTerm=state.target.label;return data;}
  if(docs.some(function(doc){return doc.querySelector('input[type=password]');}))return result({error:'login'});
  function navigate(code){
    var menu=document.querySelector('.menu-item[data-code="'+code+'"]');
    if(menu){menu.click();return true;}return false;
  }
  function select(element,label){
    var option=Array.from(element.options).find(function(o){return o.textContent.trim()===label;});
    if(!option)return false;
    element.value=option.value;element.dispatchEvent(new Event('change',{bubbles:true}));return true;
  }
  if(state.phase==='grades-menu'){
    if(navigate('S403'))state.phase='grades-select';
    return result({error:'waiting'});
  }
  if(state.phase==='schedule-menu'){
    if(navigate('S203'))state.phase='schedule-select';
    return result({error:'waiting'});
  }
  if(state.phase==='schedule-select'){
    var scheduleDoc=docs.find(function(doc){return doc.querySelector('select#xnxq');});
    if(!scheduleDoc)return result({error:'waiting'});
    var selector=scheduleDoc.querySelector('#xnxq');
    state.terms=Array.from(selector.options).map(function(o){return parser.term(o.textContent);}).filter(Boolean).map(function(t){return t.label;});
    if(!state.target){var chosen=selector.options[selector.selectedIndex];state.target=chosen&&parser.term(chosen.textContent);}
    if(!state.target || state.terms.indexOf(state.target.label)<0)return result({error:'unavailable'});
    if(kind==='grades'){
      if(navigate('S403'))state.phase='grades-select';
      return result({error:'waiting'});
    }
    var targetOption=Array.from(selector.options).find(function(o){var t=parser.term(o.textContent);return t&&t.label===state.target.label;});
    if(!targetOption)return result({error:'unavailable'});
    var twoDim=scheduleDoc.querySelector('#cxfs_ewb');
    if(twoDim && !twoDim.checked)twoDim.click();
    select(selector,targetOption.textContent.trim());state.phase='schedule-read';
    return result({error:'waiting'});
  }
  if(state.phase==='grades-select'){
    var gradeDoc=docs.find(function(doc){return doc.querySelector('#btnQry') && doc.querySelector('#xn') && doc.querySelector('#xq');});
    if(!gradeDoc)return result({error:'waiting'});
    var year=gradeDoc.querySelector('#xn'), next=gradeDoc.querySelector('#xn1'), half=gradeDoc.querySelector('#xq');
    var semester=gradeDoc.querySelector(kind==='summary'?'#sjxz1':'#sjxz3'), effective=gradeDoc.querySelector('#yxcj');
    if(!semester || !effective)return result({error:'structure'});
    semester.click();effective.click();
    if(kind!=='summary'){
      year.value=String(state.target.year);year.dispatchEvent(new Event('change',{bubbles:true}));
      if(next){next.value=String(state.target.year+1);next.dispatchEvent(new Event('change',{bubbles:true}));}
      if(!select(half,state.target.half===1?'第一学期':'第二学期'))return result({error:'structure'});
    }
    state.queryVerified=semester.checked && effective.checked && (kind==='summary' || (year.value===String(state.target.year) && (!next || next.value===String(state.target.year+1))));
    if(!state.queryVerified)return result({error:'structure'});
    // Use the same main-program scope for semester entries and the cumulative summary.
    var major=gradeDoc.querySelector('#zxC'),minor=gradeDoc.querySelector('#fxC'),micro=gradeDoc.querySelector('#wzC');
    if(major&&!major.checked)major.click();if(minor&&minor.checked)minor.click();if(micro&&micro.checked)micro.click();
    var failedOnly=gradeDoc.querySelector('#xwtg');if(failedOnly&&failedOnly.checked)failedOnly.click();
    state.oldGradeDocs=docs.filter(function(doc){return /xscj\.stuckcj_data\.jsp/.test(doc.URL);});
    gradeDoc.querySelector('#btnQry').click();state.phase='grades-read';return result({error:'waiting'});
  }
  var failure={error:'waiting'};
  for(var i=0;i<docs.length;i++){
    var doc=docs[i];if(doc.readyState!=='complete')continue;
    if(kind!=='schedule' && state.oldGradeDocs && state.oldGradeDocs.indexOf(doc)>=0)continue;
    var parsed=kind!=='schedule'?parser.effective(doc,state.target,state.queryVerified,kind==='summary'):parser.schedule(doc,state.target);
    if(!parsed.error)return result(parsed);
    if(parsed.error!=='waiting')failure=parsed;
  }
  return result(failure);
})
