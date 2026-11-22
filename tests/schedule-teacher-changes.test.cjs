const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

let now = 1000;
const context = vm.createContext({ URL, Date: { now: () => now, parse: Date.parse }, window: {} });
const asset = name => fs.readFileSync('app/src/main/assets/' + name + '.js', 'utf8');
const listScript = asset('schedule-list');
const teacherScript = asset('schedule-teacher-changes');
const portalScript = asset('schedule-portal');
const parser = vm.runInContext(listScript, context);
const changes = vm.runInContext(teacherScript, context)(parser);
const term = parser.term('2026-2027学年第一学期');
const origin = 'https://jwxt.jhun.edu.cn';
const noticePath = '/student/jxap.jxaptzxx_rpt.jsp';
const cell = value => ({ innerText: value, querySelector: () => null });
const row = values => ({ cells: values.map(value => typeof value === 'string' ? cell(value) : value) });

function notice({ date = '2026-10-30 10:46', code = '112233445', name = '测试课程', number = '033',
    before = '[101] 原教师', after = '[102] 新教师', week = '6-7', period = '一[2-3节]',
    room = 'J05-101', changedWeek = week, teacherChecked = true, kind = '调课',
    semester = term.label, method = '讲授' } = {}) {
  function field(label, oldValue, newValue, checked = false) {
    return row([{ querySelector: () => ({ checked }) }, label, oldValue, newValue]);
  }
  const table = { rows: [row(['调课项目', '调课前', '调课后']),
    field('周\u2002次', week, changedWeek, week !== changedWeek), field('节 次', period, period),
    field('地 点', room, room), field('教 师', before, after, teacherChecked), field('授课方式', '讲授', method, method !== '讲授')] };
  // The metadata may live outside the immediate wrapper of the table.
  const page = {
    innerText: `江汉大学调课通知 ${semester}\n调课时间：${date}\n${kind}\n课\u2002程： [${code}]${name}\n上课班号：${number}`,
    contains: child => child === table
  };
  table.parentElement = { innerText: '调课项目\n调课前\n调课后', parentElement: page, contains: page.contains };
  return { table, heading: page.innerText };
}

function report(pages, body = pages.map(p => p.heading).join('\n'), url = origin + noticePath) {
  return { URL: url, readyState: 'complete', body: cell(body), querySelector: () => null,
    querySelectorAll: selector => selector === 'table' ? pages.map(p => p.table) : [] };
}

const headers = ['上课班级\n代码', '课程', '学分', '任课教师', '上课时间地点'];
function list(teacher = '[101]原教师', extra = []) {
  const rows = [headers,
    ['9112233445-033', '[112233445]测试课程', '2', teacher, '1-8周 一[1-4] J05-101(48)'],
    ['9112233445-034', '[112233445]测试课程', '2', '[101]原教师', '1-8周 一[1-4] J05-101(48)'],
    ...extra];
  const doc = report([{ table: { rows: rows.map(row) } }], term.label + '\n课程门数：' + (rows.length - 1), origin + '/list');
  return { doc, parsed: parser.parse(doc, term, true) };
}

function at(schedule, classKey, week, period) {
  return schedule.courses.find(c => c.classKey === classKey && c.start <= period && c.end >= period && c.weekText.split(',').includes(String(week)));
}

const base = list().parsed;
const key = '112233445-33';
const other = '112233445-34';
assert.equal(base.courses.length, 2, 'same name, teacher and time must retain separate classes for matching');
assert.equal(at(base, key, 6, 2).teacherCode, '101');
const single = changes.parse(report([notice()]), term);
assert.equal(single.length, 1);
const edited = changes.apply(base, single);
assert.equal(at(edited, key, 6, 2).teacher, '新教师');
assert.equal(at(edited, key, 7, 3).teacher, '新教师');
assert.equal(at(edited, key, 8, 2).teacher, '原教师');
assert.equal(at(edited, key, 7, 1).teacher, '原教师');
assert.equal(at(edited, key, 7, 4).teacher, '原教师');
assert.equal(at(edited, other, 7, 2).teacher, '原教师');
assert.equal(at(edited, key, 7, 2).room, 'J05-101');
assert.equal(at(base, key, 7, 2).teacher, '原教师', 'input cache remains unchanged');
assert.strictEqual(changes.apply(edited, single), edited, 'duplicate refresh is idempotent');
assert.strictEqual(changes.apply(list('[109]原教师').parsed, single).teacherUpdated, undefined, 'same name with another staff ID is unchanged');
assert.strictEqual(changes.apply(list('[102]新教师').parsed, single).teacherUpdated, undefined, 'already reflected in list');

const later = notice({ date: '2026-11-02 11:00', before: '[102] 新教师', after: '[103] 后任教师', week: '7' });
const chain = changes.parse(report([later, notice(), notice()]), term);
assert.equal(at(changes.apply(base, chain), key, 7, 2).teacher, '后任教师', 'chooses last change in a verified chain');
assert.equal(at(changes.apply(base, chain), key, 6, 2).teacher, '新教师', 'later notice scoped to its week');
const mixedMethod = notice({ date: '2026-11-06 18:30', before: '[102] 新教师', after: '[103] 后任教师', week: '7', changedWeek: '第7周', method: '线上授课' });
const methodChain = changes.parse(report([mixedMethod, notice()]), term);
assert.equal(methodChain.length, 2, 'equivalent week formatting and changed delivery method retain the newer teacher notice');
assert.equal(at(changes.apply(base, methodChain), key, 7, 2).teacher, '后任教师');
assert.equal(at(changes.apply(base, methodChain), key, 6, 2).teacher, '新教师');
const moved = notice({ date: '2026-11-06 18:30', before: '[102] 新教师', after: '[103] 后任教师', week: '7', changedWeek: '8' });
const movedDiagnostics = {};
assert.equal(changes.parse(report([notice(), moved]), term, movedDiagnostics).length, 0, 'a real move blocks stale teacher overlays for that class');
assert.equal(movedDiagnostics.skipped, 2);
const revert = notice({ date: '2026-11-04 11:00', before: '[102] 新教师', after: '[101] 原教师' });
assert.strictEqual(changes.apply(base, changes.parse(report([revert, notice()]), term)), base, 'explicit reversion wins');
const conflict = notice({ after: '[103] 冲突教师' });
const conflictDiagnostics = {};
assert.strictEqual(changes.apply(base, changes.parse(report([notice(), conflict]), term), conflictDiagnostics), base, 'competing notices cannot replace the teacher');
assert.equal(conflictDiagnostics.issues.join(','), 'notice-conflict');
const unrelated = notice({ before: '[221] 其他教师', after: '[222] 后任教师', date: '2026-11-04 11:00' });
assert.strictEqual(changes.apply(base, changes.parse(report([notice(), unrelated]), term)), base, 'broken change chain is left alone');
for (const page of [notice({ number: '035' }), notice({ name: '别的课程' }), notice({ room: 'J05-201' }), notice({ period: '一[3-5节]' })]) {
  assert.strictEqual(changes.apply(base, changes.parse(report([page]), term)), base, 'unmatched or incomplete slot cannot modify a course');
}
const teacherDiagnostics = {};
changes.apply(list('[109]原教师').parsed, single, teacherDiagnostics);
assert.equal(teacherDiagnostics.issues.join(','), 'teacher-mismatch');
const appliedDiagnostics = {};
changes.apply(list('[102]新教师').parsed, single, appliedDiagnostics);
assert.equal(appliedDiagnostics.issues.length, 0, 'a timetable already showing the new teacher is a verified match');
const ambiguous = list('[101]原教师', [['9112233445-033', '[112233445]测试课程', '2', '[200]另一教师', '7周 一[2] J05-101']]).parsed;
assert.equal(at(changes.apply(ambiguous, single), key, 7, 3).teacher, '原教师', 'one ambiguous period blocks the change for that week');
assert.equal(at(changes.apply(ambiguous, single), key, 6, 3).teacher, '新教师', 'unambiguous weeks can update');
assert.equal(changes.parse(report([notice({ changedWeek: '8' }), notice({ kind: '补课' }), notice({teacherChecked: false})]), term).length, 0);
const unsupported = notice({ code: '112233448', name: '测试选修', number: '002', week: '12', period: '三[5-6节]', room: 'A101', before: '[301]原选修教师', after: '[302]教师甲\n[303]教师乙' });
const skippedOnly = {};
assert.equal(changes.parse(report([unsupported]), term, skippedOnly).length, 0);
assert.equal(skippedOnly.skipped, 1, 'ambiguous teacher identity is skipped, not treated as a verified empty report');
assert.strictEqual(changes.parse(report([notice()], notice().heading + '\n第1页 共2页'), term), null);
assert.strictEqual(changes.parse(report([notice()], notice().heading.replace('2026-2027', '2025-2026')), term), null);
const oldTerm = '2025-2026学年第一学期';
const oldTermNotice = notice({ semester: oldTerm });
assert.strictEqual(changes.parse(report([notice({ teacherChecked: false }), oldTermNotice]), term), null, 'check the term of every notice, including the one after a valid heading');
assert.strictEqual(changes.parse(report([notice({ semester: '' }), notice()]), term), null, 'do not inherit the next notice’s term');
assert.strictEqual(changes.parse(report([oldTermNotice, notice()]), term), null, 'mixed semesters are rejected in either order');
const wrongTermDiagnostics = {};
assert.strictEqual(changes.apply(base, [{ ...single[0], term: oldTerm }], wrongTermDiagnostics), base);
assert.equal(wrongTermDiagnostics.issues.join(','), 'term-mismatch');
assert.strictEqual(changes.parse(report([notice()], notice().heading, 'https://example.com' + noticePath), term), null);
assert.strictEqual(changes.parse(report([notice()], notice().heading + '\n江汉大学调课通知'), term), null);
assert.equal(changes.parse(report([], '没有检索到记录'), term).length, 0);

// A real report can combine an ordinary course code, a tagged code, and another record we cannot use.
const sports = notice({ code: '112233447', name: '测试体育', week: '12', period: '五[3-4节]', room: '测试操场-2' });
const tagged = notice({ code: '112233446-1', name: '测试实验', number: '010', week: '16', before: '[201]原实验教师', after: '[202]新实验教师' });
const mixedList = list('[101]原教师', [
  ['9112233447-033', '[112233447]测试体育', '2', '[101]原教师', '11-13周 五[3-4] 测试操场-2(48)'],
  ['9112233446-1-010', '[112233446-1]测试实验', '2', '[201]原实验教师', '15-17周 一[2-3] J05-101(48)'],
  ['9112233448-002', '[112233448]测试选修', '2', '[301]原选修教师', '12周 三[5-6] A101(48)']
]);
const mixedReport = report([sports, tagged, unsupported]);
const mixedDiagnostics = {};
const mixedChanges = changes.parse(mixedReport, term, mixedDiagnostics);
assert.equal(mixedChanges.length, 2, 'an unsupported notice cannot discard the rest of the report');
assert.equal(mixedDiagnostics.skipped, 1);
const sportsKey = '112233447-33';
const taggedKey = '112233446-1-10';
const mixed = changes.apply(mixedList.parsed, mixedChanges);
assert.equal(at(mixed, sportsKey, 12, 3).teacher, '新教师');
assert.equal(at(mixed, sportsKey, 13, 3).teacher, '原教师');
assert.equal(at(mixed, taggedKey, 16, 2).teacher, '新实验教师');
assert.equal(at(mixed, taggedKey, 17, 2).teacher, '原实验教师');
assert.equal(at(mixed, '112233448-2', 12, 5).teacher, '原选修教师');

const blockedTagged = notice({ code: '112233446-1', name: '测试实验', number: '010', week: '17', before: '[202]新实验教师', after: '[203]教师甲\n[204]教师乙' });
const blockedDiagnostics = {};
const unblockedChanges = changes.parse(report([sports, tagged, blockedTagged]), term, blockedDiagnostics);
assert.equal(unblockedChanges.length, 1, 'a disputed class cannot suppress another verified class');
assert.equal(blockedDiagnostics.skipped, 2, 'include notices withheld because the same class is disputed');
const unblocked = changes.apply(mixedList.parsed, unblockedChanges);
assert.equal(at(unblocked, sportsKey, 12, 3).teacher, '新教师');
assert.equal(at(unblocked, taggedKey, 16, 2).teacher, '原实验教师', 'an invalid notice blocks other notices for that same class');
assert.strictEqual(changes.parse(report([sports, notice({ code: '112233446--1' })]), term), null, 'unknown notice scope cannot be safely isolated');

// Exercise the real polling controller with stale and fresh frames from both queries.
function poll(job, cached, noticeAdapter = teacherScript) {
  // Use the same script assembly as MainActivity.scheduleQuery and the WebView JSON bridge.
  const query = `${portalScript}(${job},${JSON.stringify(term.label)},${listScript},${noticeAdapter},${JSON.stringify(cached)})`;
  return JSON.parse(JSON.stringify(vm.runInContext(query, context)));
}
let changeQueries = 0;
const selector = { value: 'current', selectedIndex: 0, options: [
  { value: 'current', textContent: term.label }, { value: 'other', textContent: '2025-2026学年第二学期' }
] };
const listFrame = { contentDocument: null, addEventListener() {} };
const listRadio = { checked: false, click() { this.checked = true; } };
const listForm = {
  URL: origin + '/student/xkjg.wdkb.jsp', readyState: 'complete',
  querySelector: s => ({ '#xnxq': selector, '#cxfs_lb': listRadio, '#frmReport': listFrame })[s],
  querySelectorAll: s => s === 'iframe,frame' ? [listFrame] : []
};
const changeFrame = { contentDocument: report([notice()]) };
const changeSelector = { ...selector, value: 'other' };
const changeButton = { click() { changeQueries++; assert.equal(changeSelector.value, 'current'); } };
const changeForm = {
  URL: origin + '/student/jxap.jxaptzxx.html', readyState: 'complete',
  querySelector: s => ({ '#xnxq': changeSelector, '#btnSubmit': changeButton })[s],
  querySelectorAll: s => s === 'iframe,frame' ? [changeFrame] : []
};
let children = [];
const tabs = [
  { textContent: '个人课表', click() { children = [listForm]; } },
  { textContent: '调课信息', click() { children = [changeForm]; changeSelector.value = 'other'; } }
];
context.document = {
  URL: origin + '/frame/homes.action', body: cell(term.label),
  querySelector: s => s.includes('S203') ? { click() {} } : null,
  querySelectorAll: s => s === 'a' ? tabs : s === 'iframe,frame' ? children.map(d => ({ contentDocument: d })) : []
};
function reachNotices(job, listReport = list().doc) {
  listFrame.contentDocument = null;
  for (const stage of ['schedule-menu', 'schedule-tab', 'term-switching', 'list-switching']) {
    assert.equal(poll(job, single).stage, stage);
  }
  listFrame.contentDocument = listReport;
  assert.equal(poll(job, single).stage, 'report-settling');
  assert.equal(poll(job, single).stage, 'teacher-changes');
  assert.equal(poll(job, single).stage, 'teacher-changes');
}
reachNotices(1);
assert.equal(poll(1, []).stage, 'teacher-changes', 'old notice frame is ignored');
changeFrame.contentDocument = report([notice()]);
assert.equal(poll(1, []).stage, 'teacher-changes', 'new snapshot must settle');
let synced = poll(1, []);
assert.equal(synced.teacherSync, 'checked');
assert.equal(synced.term, term.label);
assert.equal(synced.version, 2);
assert.equal(synced.complete, true);
assert.equal(at(synced, key, 7, 2).teacher, '新教师');
assert.equal(synced.teacherChanges.length, 1);
reachNotices(2);
now += 9000;
synced = poll(2, single);
assert.equal(synced.teacherSync, 'unavailable');
assert.equal(at(synced, key, 7, 2).teacher, '新教师', 'unavailable notice report reuses verified changes');
reachNotices(3);
changeFrame.contentDocument = report([], '没有检索到记录');
assert.equal(poll(3, single).stage, 'teacher-changes');
synced = poll(3, single);
assert.equal(synced.teacherSync, 'checked');
assert.equal(synced.teacherChanges.length, 0, 'verified empty report clears obsolete local notices');
assert.equal(at(synced, key, 7, 2).teacher, '原教师');
reachNotices(4);
now += 9000;
const malformedOverlay = '(function(){return {apply:function(){return {parse:function(){}};}};})';
synced = poll(4, single, malformedOverlay);
assert.equal(synced.teacherSync, 'unavailable');
assert.equal(synced.term, term.label, 'bad optional adapter result must not replace schedule');
assert.equal(synced.courses.length, base.courses.length);
assert.equal(synced.teacherChanges.length, 1, 'preserve known notices for the next refresh');
reachNotices(5, mixedList.doc);
changeFrame.contentDocument = mixedReport;
assert.equal(poll(5, []).stage, 'teacher-changes');
synced = poll(5, []);
assert.equal(synced.teacherSync, 'partial', 'mixed notice report returns usable changes and marks the skipped record');
assert.equal(synced.teacherChanges.length, 2);
assert.equal(at(synced, sportsKey, 12, 3).teacher, '新教师');
assert.equal(at(synced, taggedKey, 16, 2).teacher, '新实验教师');
reachNotices(6);
changeFrame.contentDocument = report([notice({ room: 'J05-201' }), notice({ number: '035' })]);
assert.equal(poll(6, []).stage, 'teacher-changes');
synced = poll(6, []);
assert.equal(synced.teacherSync, 'partial', 'parsed but unmatched notices must flag partial sync');
assert.deepEqual(synced.teacherMatchIssues, ['slot-mismatch','class-mismatch']);
assert.equal(at(synced, key, 7, 2).teacher, '原教师');
reachNotices(7);
changeFrame.contentDocument = report([notice({ teacherChecked: false }), oldTermNotice]);
assert.equal(poll(7, []).stage, 'teacher-changes');
now += 9000;
const legacy = { ...single[0] };
delete legacy.term;
synced = poll(7, [legacy, { ...legacy, term: oldTerm }]);
assert.equal(synced.teacherSync, 'unavailable');
assert.equal(synced.teacherChanges.length, 0, 'legacy and other-term cache entries are not trusted after a bad report');
assert.equal(at(synced, key, 7, 2).teacher, '原教师');
assert.equal(changeQueries, 7, 'each job queries the notice report just once');

console.log('PASS: teacher change report, exact slot matching, conflict history and refresh fallback');
