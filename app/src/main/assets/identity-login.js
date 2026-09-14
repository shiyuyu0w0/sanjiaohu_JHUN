(function(action,account,password,code){
  // Use the school's own submit handler (including any password encryption).
  function loginPath(path){return /^\/authserver\/login(?:;jsessionid=[A-Za-z0-9._-]+)?$/.test(path);}
  if(!/^https?:\/\/authserver\.jhun\.edu\.cn$/.test(location.origin)||!loginPath(location.pathname))return {state:'unsupported',error:'当前页面不是学校的统一认证登录页，请重新加载。'};
  function serviceAllowed(url,depth){if(depth>4)return false;var values=url.searchParams.getAll('service');return values.every(function(value){try{var next=new URL(value);return /^(https?:)$/.test(next.protocol)&&/^(ehall|hqfw)\.jhun\.edu\.cn$/.test(next.hostname)&&!next.username&&!next.password&&!next.port&&serviceAllowed(next,depth+1);}catch(e){return false;}});}
  if(!serviceAllowed(new URL(location.href),0))return {state:'unsupported'};
  // DOM parsing must finish, but images and unrelated resources may still be loading.
  if(document.readyState==='loading')return {state:'waiting'};
  function visible(e){return !!e&&!e.disabled&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';}
  var passwords=Array.from(document.querySelectorAll('input[type="password"]')).filter(visible);
  if(passwords.length!==1)return {state:'waiting'};
  var pass=passwords[0],form=pass.form;
  if(!form)return {state:'unsupported'};
  function schoolAction(){
    var target;try{target=new URL(form.action||location.href,location.href);}catch(e){return false;}
    if(!/^https?:\/\/authserver\.jhun\.edu\.cn$/.test(target.origin)||!loginPath(target.pathname)||target.username||target.password||!serviceAllowed(target,0)||String(form.method).toLowerCase()!=='post')return false;
    form.action=target.href;return true;
  }
  // The school's HTTPS GET redirects to HTTP. Preserve its form action and session;
  // upgrading it again creates a real loop. Still validate the exact host and CAS POST.
  if(!schoolAction())return {state:'unsupported',error:'学校表单的提交地址无法安全使用，请重新加载登录页。'};
  if(!form.__sanjiaohuSchoolAction){
    form.addEventListener('submit',function(event){if(!schoolAction())event.preventDefault();},true);
    form.__sanjiaohuSchoolAction=true;
  }
  if(action==='prepare')return {state:'prepared'};
  function hint(e){return [e.name,e.id,e.placeholder,e.getAttribute('aria-label')].join(' ');}
  var inputs=Array.from(form.querySelectorAll('input')).filter(visible);
  var captcha=inputs.find(function(e){return /captcha|验证码|randnumber/i.test(hint(e))&&e.type!=='hidden';});
  var candidates=inputs.filter(function(e){return e!==captcha&&/^(text|email|tel)$/.test(e.type);});
  var user=candidates.find(function(e){return /^(username|userName|account)$/.test(e.name)||/^(username|userName|account)$/.test(e.id);})||(candidates.length===1?candidates[0]:null);
  var buttons=Array.from(form.querySelectorAll('button,input[type="submit"],input[type="button"]')).filter(visible);
  var button=buttons.find(function(e){return /^登\s*录$/.test((e.innerText||e.value||'').trim());})||buttons.find(function(e){return e.type==='submit';});
  if(!user||!button)return {state:'unsupported'};
  var encrypted=Array.from(form.querySelectorAll('input')).find(function(e){return e.id==='passwordEncrypt'&&e.name==='password';});
  var image='';
  if(captcha){var picture=Array.from(form.querySelectorAll('img')).filter(visible).find(function(e){return /captcha|验证码|code|rand/i.test([e.id,e.alt,e.src].join(' '));});
    if(picture&&picture.complete&&picture.naturalWidth){try{var canvas=document.createElement('canvas');canvas.width=picture.naturalWidth;canvas.height=picture.naturalHeight;canvas.getContext('2d').drawImage(picture,0,0);image=canvas.toDataURL('image/png');}catch(ignore){}}
  }
  var errors=Array.from(form.querySelectorAll('[role="alert"],.error,.errors,.error-msg,.auth_error')).filter(visible).map(function(e){return (e.innerText||'').trim();}).filter(Boolean).join(' ').slice(0,180);
  if(action!=='submit')return {state:'ready',captcha:!!captcha,image:image,error:errors};
  if(captcha&&!code)return {state:'captcha',image:image};
  function fill(e,value){var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(e,value);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('keyup',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));e.dispatchEvent(new Event('blur',{bubbles:true}));}
  fill(user,account);fill(pass,password);if(captcha)fill(captcha,code);
  // Never enable website password storage; the native vault owns persistence.
  Array.from(form.querySelectorAll('input[type="checkbox"]')).forEach(function(e){if(/remember/i.test(hint(e)))e.checked=false;});
  // School templates differ: some use passwordEncrypt, others handle password
  // in-place or during submission. Never require a field from one template.
  // Observe the school's submission without replacing its password handling.
  var seen=false,blocked=false,waiting=false;
  function observe(event){
    seen=true;blocked=event.defaultPrevented;
    if(!blocked&&encrypted&&(!encrypted.value||encrypted.value===password)){
      event.preventDefault();waiting=true;
    }
  }
  form.addEventListener('submit',observe);
  try{button.click();}finally{form.removeEventListener('submit',observe);}
  if(waiting)return {state:'waiting'};
  if(blocked)return {state:'rejected',error:'学校未接受本次提交，请检查页面提示、账号密码或验证码。'};
  if(!seen)return {state:'rejected',error:'学校登录按钮未触发表单提交，请重新加载后再试，或在学校页面完成验证。'};
  return {state:'submitted'};
})
