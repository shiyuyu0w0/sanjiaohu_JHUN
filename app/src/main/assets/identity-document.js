(function(){
  var secure=location.origin==='https://authserver.jhun.edu.cn';
  var login=/^\/authserver\/login(?:;jsessionid=[A-Za-z0-9._-]+)?$/.test(location.pathname);
  // Only booleans and fixed categories leave the page. No field values, URLs or page text.
  return {secure:secure,login:login,form:!!document.getElementById('casLoginForm'),
    originKind:secure?'https-auth':location.origin==='http://authserver.jhun.edu.cn'?'http-auth':'other',
    ready:document.readyState==='complete'?'complete':document.readyState==='interactive'?'interactive':'loading'};
})()
