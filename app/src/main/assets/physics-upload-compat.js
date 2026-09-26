(function () {
  'use strict';
  if (location.hostname !== 'wlxpk.jhun.edu.cn' || location.port !== '6603' ||
      !/^https?:$/.test(location.protocol) || location.pathname !== '/Page/Android/html/index.htm') return;
  if (window.__sanjiaohuReportCompat) return;
  window.__sanjiaohuReportCompat = true;

  function notify(message) {
    if (window.layer && typeof window.layer.open === 'function') {
      window.layer.open({content: message, skin: 'msg', time: 3});
    }
  }
  function install() {
    if (location.hash !== '#/StuReportChoosePic' || !window.angular) return;
    var input = document.getElementById('upfile');
    if (!input || input.type !== 'file') return;
    var scope = window.angular.element(input).scope();
    if (!scope || scope.__sanjiaohuReportCompat || typeof scope.fileChange !== 'function' ||
        !Array.isArray(scope.files) || !Array.isArray(scope.imgs)) return;
    scope.__sanjiaohuReportCompat = true;
    var pending = 0, nextId = Date.now();

    // The site's realod() calls an Angular component that does not exist in its AngularJS scope.
    scope.realod = function () { if (!scope.$$destroyed) scope.$evalAsync(); };
    scope.fileChange = function () {
      var selected = Array.prototype.slice.call(input.files || []);
      if (scope.files.length + pending + selected.length > 4) {
        notify('最多只能上传 4 张图片，请先移除多余图片。');
        input.value = '';
        return;
      }
      pending += selected.length;
      selected.forEach(function (file) {
        var header = new FileReader();
        function failed(message) { pending--; if (!scope.$$destroyed) notify(message); }
        header.onerror = function () { failed('图片读取失败，请重新选择。'); };
        header.onload = function () {
          if (scope.$$destroyed) { pending--; return; }
          var bytes = new Uint8Array(header.result), suffix = '', mime = '';
          if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) {
            suffix = '.jpg'; mime = 'image/jpeg';
          } else if (bytes.length >= 8 && bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 &&
                     bytes[3] === 71 && bytes[4] === 13 && bytes[5] === 10 && bytes[6] === 26 && bytes[7] === 10) {
            suffix = '.png'; mime = 'image/png';
          } else { failed('报告系统仅支持 JPG、JPEG、PNG 图片，请转换格式后选择。'); return; }
          var name = file.name || 'report';
          if (!(mime === 'image/jpeg' ? /\.jpe?g$/i : /\.png$/i).test(name)) {
            name = name.replace(/\.[^.]+$/, '') + suffix;
          }
          var normalized;
          try {
            // Rename the File metadata only. Its original bytes, resolution and quality are kept.
            normalized = new File([file], name, {type: mime, lastModified: file.lastModified});
          } catch (error) { failed('当前网页组件不支持读取图片，请更新 Android System WebView 后重试。'); return; }
          var preview = new FileReader();
          preview.onerror = function () { failed('图片预览读取失败，请重新选择。'); };
          preview.onload = function () {
            if (scope.$$destroyed) { pending--; return; }
            scope.$evalAsync(function () {
              pending--;
              if (scope.$$destroyed) return;
              scope.files.push(normalized);
              scope.imgs.push({id: ++nextId, fileName: normalized.name, base64: preview.result});
            });
          };
          try { preview.readAsDataURL(normalized); }
          catch (error) { failed('图片预览读取失败，请重新选择。'); }
        };
        try { header.readAsArrayBuffer(file.slice(0, 8)); }
        catch (error) { failed('图片读取失败，请重新选择。'); }
      });
      // Selecting files prepares previews; the original uploadPic() still handles the user's submit.
      input.value = '';
    };
  }
  new MutationObserver(install).observe(document.documentElement, {childList: true, subtree: true});
  window.addEventListener('hashchange', install);
  install();
})()
