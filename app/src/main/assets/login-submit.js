(function(account,password,code){
  if(location.origin !== 'https://jwxt.jhun.edu.cn')return {error:'origin'};
  var user=document.getElementById('username'),pass=document.getElementById('password'),button=document.getElementById('login');
  if(!user||!pass||!button||pass.type!=='password')return {error:'form'};
  var form=user.form;
  if(!form||new URL(form.action,location.href).origin!==location.origin)return {error:'origin'};
  var captcha=document.getElementById('randnumber');
  var required=captcha&&!captcha.disabled&&getComputedStyle(captcha).display!=='none';
  if(required&&!code)return {error:'captcha'};
  function fill(e,value){e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}
  fill(user,account);fill(pass,password);if(required)fill(captcha,code);
  var remember=document.getElementById('setCookie');if(remember)remember.checked=false;
  button.click();return {submitted:true};
})
