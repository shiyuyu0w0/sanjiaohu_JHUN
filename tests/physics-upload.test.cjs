const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert');
const code = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/physics-upload-compat.js'), 'utf8');
const jpeg = Buffer.from([255, 216, 255, 224, 0, 16, 74, 70, 73, 70]);
const png = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, 0, 0]);

function fixture(options = {}) {
  const input = {type: 'file', files: [], value: 'selected'};
  const messages = [], tasks = [], listeners = {};
  let digests = 0, observers = 0, uploads = 0;
  let scope = makeScope();
  function makeScope() {
    return {files: [], imgs: [], fileChange() { throw Error('legacy handler'); },
      realod() { this.StuReportChoosePicCtrl.markForCheck(); },
      uploadPic() { uploads++; }, $evalAsync(fn) { digests++; if (fn) fn(); }};
  }
  class Reader {
    readAsArrayBuffer(blob) {
      tasks.push(async () => {this.result = await blob.arrayBuffer(); this.onload();});
    }
    readAsDataURL(blob) {
      tasks.push(async () => {
        if (options.previewFailure) {this.onerror(); return;}
        this.result = `data:${blob.type};base64,${Buffer.from(await blob.arrayBuffer()).toString('base64')}`;
        this.onload();
      });
    }
  }
  const location = {hostname: options.external ? 'other.invalid' : 'wlxpk.jhun.edu.cn', port: '6603',
    protocol: 'http:', pathname: '/Page/Android/html/index.htm', hash: options.list ? '#/StuReport' : '#/StuReportChoosePic'};
  const window = {angular: {element: () => ({scope: () => scope})},
    layer: {open: value => messages.push(value.content)}, addEventListener: (name, fn) => listeners[name] = fn};
  const document = {documentElement: {}, getElementById: id => id === 'upfile' ? input : null};
  let mutation;
  class Observer { constructor(fn) {mutation = fn; observers++;} observe() {} }
  const context = {window, location, document, MutationObserver: Observer, File, FileReader: Reader, Uint8Array};
  const originalRefresh = scope.realod;
  vm.runInNewContext(code, context);
  return {input, messages, context, originalRefresh, get scope() {return scope;},
    get digests() {return digests;}, get observers() {return observers;}, get uploads() {return uploads;},
    select(files) {input.files = files; input.value = 'selected'; scope.fileChange();},
    async flush() {while (tasks.length) await Promise.all(tasks.splice(0).map(fn => fn()));},
    routeToUpload() {location.hash = '#/StuReportChoosePic'; listeners.hashchange(); mutation();},
    newScope() {scope = makeScope(); mutation();}, repeat() {vm.runInNewContext(code, context);}};
}

(async () => {
  const f = fixture();
  assert.throws(() => f.originalRefresh.call({}), /markForCheck/);
  f.scope.realod();
  assert.equal(f.digests, 1, 'replacement refresh schedules AngularJS digest');
  f.select([new File([jpeg], '10000042', {lastModified: 123})]);
  await f.flush();
  assert.equal(f.scope.files[0].name, '10000042.jpg', 'extensionless phone photo gets a usable name');
  assert.equal(f.scope.files[0].type, 'image/jpeg');
  assert.deepEqual(Buffer.from(await f.scope.files[0].arrayBuffer()), jpeg, 'photo bytes remain unchanged');
  assert.match(f.scope.imgs[0].base64, /^data:image\/jpeg;base64,/);
  assert.equal(f.input.value, '', 'same image can be picked again after removal');
  f.select([new File([png], 'photo.JPG', {type: 'image/jpeg'})]);
  await f.flush();
  assert.equal(f.scope.files[1].name, 'photo.png', 'actual image format determines extension and MIME');
  assert.notEqual(f.scope.imgs[0].id, f.scope.imgs[1].id);
  f.select([new File(['not an image'], 'fake.jpg')]);
  await f.flush();
  assert.equal(f.scope.files.length, 2, 'unsupported bytes do not enter upload list');
  assert.match(f.messages.at(-1), /仅支持/);
  f.repeat();
  assert.equal(f.observers, 1, 'reinjection is idempotent');
  assert.equal(f.uploads, 0, 'selection never submits a report');

  const limit = fixture();
  limit.select([1, 2, 3, 4].map(i => new File([jpeg], `${i}.jpg`)));
  limit.select([new File([jpeg], '5.jpg')]);
  await limit.flush();
  assert.equal(limit.scope.files.length, 4, 'pending image reads count toward four-image maximum');
  assert.match(limit.messages[0], /最多/);
  limit.scope.files.pop(); limit.scope.imgs.pop();
  limit.select([new File([jpeg], '6.jpeg')]); await limit.flush();
  assert.equal(limit.scope.files.at(-1).name, '6.jpeg');

  const failed = fixture({previewFailure: true});
  failed.select([new File([jpeg], 'broken.jpg')]); await failed.flush();
  assert.equal(failed.scope.files.length, 0);
  assert.match(failed.messages[0], /预览读取失败/);
  const destroyed = fixture();
  destroyed.select([new File([jpeg], 'old.jpg')]); destroyed.scope.$$destroyed = true;
  await destroyed.flush(); assert.equal(destroyed.scope.files.length, 0);

  const route = fixture({list: true});
  assert.throws(() => route.scope.fileChange(), /legacy/);
  route.routeToUpload(); route.scope.realod(); route.newScope(); route.scope.realod();
  assert.equal(route.digests, 2, 'new SPA controller scopes are patched too');
  const external = fixture({external: true});
  assert.equal(external.observers, 0, 'compatibility patch is restricted to the report origin');
  console.log('PASS: phone filenames, original bytes, preview refresh, limits, errors, SPA routes and no auto-upload');
})().catch(error => {console.error(error); process.exitCode = 1;});
