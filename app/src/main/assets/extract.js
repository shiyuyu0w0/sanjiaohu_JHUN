(function () {
  function read(doc, depth) {
    var box = doc.querySelector('#kbDiv');
    if (box && box.querySelector('table')) {
      var table = box.querySelector('table');
      var header = Array.from(table.querySelectorAll('thead td')).map(function (e) { return e.textContent.trim(); });
      if (!['星期一','星期二','星期三','星期四','星期五','星期六','星期日'].every(function (s) { return header.indexOf(s) >= 0; })) return {error:'structure'};
      var rows = [];
      var malformed = false;
      Array.from(table.querySelectorAll('tbody tr')).forEach(function (tr) {
        var cells = Array.from(tr.cells);
        var index = cells.findIndex(function (c) { return /^\d{1,2}\s*[（(]\d{2}:\d{2}/.test(c.textContent.trim()); });
        if (index < 0) return;
        if (cells.length - index !== 8 || cells.slice(index + 1).some(function(c){ return c.colSpan !== 1 || c.rowSpan !== 1; })) { malformed = true; return; }
        rows.push({period:cells[index].textContent.trim(), days:cells.slice(index+1).map(function (c) { return c.innerText.trim(); })});
      });
      var title = (box.querySelector('div') || box).textContent.trim();
      var range = box.innerText.match(/周次[：:]\s*1\s*[-－~～]\s*(\d+)/);
      if (malformed || rows.length !== 12 || !range || !/学期教学安排表$/.test(title)) return {error:'incomplete'};
      return {version:1, term:title.replace(/教学安排表$/, ''), maxWeek:Number(range[1]), rows:rows};
    }
    if (depth < 4) {
      var frames = doc.querySelectorAll('iframe,frame');
      for (var i=0;i<frames.length;i++) {
        try { if (frames[i].contentDocument) { var result=read(frames[i].contentDocument,depth+1); if (result && !result.error) return result; } } catch (ignore) {}
      }
    }
    if (doc.querySelector('input[type=password]')) return {error:'login'};
    return {error:'waiting'};
  }
  return read(document,0);
})()
