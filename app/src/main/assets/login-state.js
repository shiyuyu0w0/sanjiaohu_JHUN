(function(){
  if(location.origin!=='https://jwxt.jhun.edu.cn')return {state:'unsupported'};
  if(location.pathname==='/frame/homes.action')return {state:'success'};
  var user=document.getElementById('username'),pass=document.getElementById('password'),button=document.getElementById('login');
  if(!user||!pass||!button)return {state:'waiting'};
  var field=document.getElementById('randnumber'),pic=document.getElementById('randpic');
  var required=!!field&&!field.disabled&&getComputedStyle(field).display!=='none';
  var data='';
  if(required&&pic&&pic.complete&&pic.naturalWidth){try{var c=document.createElement('canvas');c.width=pic.naturalWidth;c.height=pic.naturalHeight;c.getContext('2d').drawImage(pic,0,0);data=c.toDataURL('image/png');}catch(ignore){}}
  // Read only dedicated error elements, never the page's general password-help text.
  var message='';
  if(typeof document.querySelectorAll==='function'){
    var errors=document.querySelectorAll('[role="alert"],.login-error,.error-message,#errorMsg,#errorMessage,#loginError');
    for(var i=0;i<errors.length;i++){
      var node=errors[i],style=getComputedStyle(node);
      if(style.display==='none'||style.visibility==='hidden')continue;
      var text=(node.textContent||'').trim();
      if(text){message=text.slice(0,240);break;}
    }
  }
  return {state:'ready',captcha:required,image:data,message:message};
})()
