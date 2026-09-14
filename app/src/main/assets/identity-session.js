(function(){
  if(!/^https?:$/.test(location.protocol)||location.hostname!=='ehall.jhun.edu.cn'||location.pathname!=='/new/index.html')return false;
  function visible(e){return !!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';}
  // Confirmed against the actual hall DOM. Its sign-out control is a DIV,
  // not an <a> or <button>, and is labeled 安全退出.
  var tool=document.getElementById('ampHasLoginTool');
  var avatar=document.getElementById('ampHeaderToolUser');
  var logout=document.getElementById('ampHeaderUserInfoLogoutBtn');
  return visible(tool)&&!!avatar&&tool.contains(avatar)&&!!logout&&
    logout.textContent.trim()==='安全退出'&&/[\u4e00-\u9fffA-Za-z0-9]/.test(tool.textContent||'');
})()
