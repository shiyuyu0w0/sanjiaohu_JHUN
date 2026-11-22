package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/** Exercises MainActivity's actual query, WebView result and cache commit without contacting the school. */
public final class ScheduleSyncInstrumentation extends Instrumentation {
    private static final String ORIGIN = "https://jwxt.jhun.edu.cn";
    private static final String HOME = ORIGIN + "/schedule-test/home";
    private static final String TERM = "2026-2027学年第一学期";
    private MainActivity activity;
    private File directory;
    private CourseNoteStore notes;
    private int checks;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            directory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "schedule-webview-").toFile();
            configure();
            String status = sync(false).json.optString("teacherSync");
            Schedule updated = load();
            checkCourse(updated, "测试体育", 12, 5, 3, 4, "测试操场-2", "最终教师");
            check(status.equals("partial"), "valid but unmatched notice reports partial sync: " + status);
            check(activity.state.contains("上课时段或地点不符"), "on-screen status explains the mismatch: " + activity.state);
            checkCourse(updated, "测试体育", 11, 5, 3, 4, "测试操场-2", "原教师");
            checkCourse(updated, "测试实验", 16, 1, 2, 3, "J05-101", "新实验教师");
            checkCourse(updated, "测试实验", 17, 1, 2, 3, "J05-101", "原实验教师");
            checkCourse(updated, "测试选修", 12, 3, 5, 6, "A101", "原选修教师");
            check(updated.json.getJSONArray("teacherChanges").length() == 4, "validated notices were persisted");
            for(int i=0;i<4;i++)check(updated.json.getJSONArray("teacherChanges").getJSONObject(i).getString("term").equals(TERM), "cache keeps the verified term");
            Course sports=updated.at(12,5).get(0), previousWeek=updated.at(11,5).get(0);
            check(notes.get(sports,TERM).equals("带球拍"),"legacy note is visible after the final teacher change");
            notes.save(sports,TERM,"带球拍和水");
            check(notes.get(previousWeek,TERM).equals("带球拍和水"),"same course name shares the edited note across teachers");
            notes.save(sports,TERM,"");
            check(notes.get(previousWeek,TERM).isEmpty(),"clearing the shared note does not restore a legacy note");

            check(sync(true).json.optString("teacherSync").equals("unavailable"), "missing notice entry used the optional fallback");
            Schedule fallback = load();
            checkCourse(fallback, "测试体育", 12, 5, 3, 4, "测试操场-2", "最终教师");
            checkCourse(fallback, "测试实验", 16, 1, 2, 3, "J05-101", "新实验教师");
            checkCourse(fallback, "测试体育", 13, 5, 3, 4, "测试操场-2", "原教师");
            result.putString("stream", "PASS: " + checks + " WebView schedule sync and cache checks\n");
        } catch (Throwable failure) {
            code = Activity.RESULT_CANCELED;
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(failure) + "\n");
        } finally {
            if (activity != null) runOnMainSync(() -> {
                activity.handler.removeCallbacksAndMessages(null);
                if (activity.web != null) activity.web.destroy();
                activity.authIo.shutdown();
            });
            if (directory != null) {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) file.delete();
                directory.delete();
            }
        }
        finish(code, result);
    }

    private void configure() throws Exception {
        String portal = readAsset("schedule-portal.js");
        String parser = readAsset("schedule-list.js");
        String changes = readAsset("schedule-teacher-changes.js");
        ContextWrapper isolated = new ContextWrapper(getTargetContext()) {
            @Override public File getFilesDir() { return directory; }
        };
        AcademicStore store = new AcademicStore(isolated);
        notes=new CourseNoteStore(isolated);
        Properties legacy=new Properties();
        legacy.setProperty("school|6:2026-14:测试体育3:原教师","带球拍");
        try(FileOutputStream output=new FileOutputStream(new File(directory,"course-notes.properties"))){legacy.store(output,null);}
        runOnMainSync(() -> {
            // Isolate the host from Activity lifecycle, credentials and personal preferences.
            activity = new MainActivity() {
                @Override void authenticated() { verified = true; }
                @Override void render() {}
                @Override void refreshSummary() {}
            };
            activity.schedulePortalScript = portal;
            activity.scheduleListScript = parser;
            activity.scheduleTeacherScript = changes;
            activity.academicStore = store;
            activity.jobKind = "schedule";
            activity.jobTerm = TERM;
            activity.terms.add(TERM);
            activity.web = new WebView(getTargetContext());
            activity.web.getSettings().setJavaScriptEnabled(true);
            activity.web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        });
    }

    private String readAsset(String file) throws Exception {
        try (java.io.InputStream stream = getTargetContext().getAssets().open(file);
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private Schedule sync(boolean noNoticeEntry) {
        String homeUrl = HOME + (noNoticeEntry ? "?without-notices" : "?with-notices");
        runOnMainSync(() -> {
            activity.web.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String body = fixture(request.getUrl().getPath(), noNoticeEntry);
                    String html = "<!doctype html><meta charset='UTF-8'><body>" + body + "</body>";
                    return new WebResourceResponse("text/html", "UTF-8",
                            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }

                @Override public void onPageFinished(WebView view, String url) {
                    if (homeUrl.equals(url)) activity.mainLoaded = true;
                }
            });
            activity.mainLoaded = false;
            activity.verified = false;
            activity.startPoll();
            activity.web.loadUrl(homeUrl);
        });
        long deadline = SystemClock.elapsedRealtime() + 18000;
        boolean[] complete = new boolean[1];
        while (SystemClock.elapsedRealtime() < deadline) {
            runOnMainSync(() -> complete[0] = !activity.busy);
            if (complete[0]) break;
            SystemClock.sleep(100);
        }
        check(complete[0], "fixture refresh finished before timeout");
        check(activity.verified && activity.schedule != null, "real poll committed the schedule: " + activity.state);
        return activity.schedule;
    }

    private Schedule load() throws Exception {
        return new Schedule(activity.academicStore.load("schedule", TERM));
    }

    private void checkCourse(Schedule schedule, String name, int week, int day, int start, int end,
            String room, String teacher) {
        String label = name + " week " + week;
        List<Course> courses = schedule.at(week, day);
        check(courses.size() == 1 && courses.get(0).name.equals(name), "expected a unique matching course: " + label);
        Course course = courses.get(0);
        check(course.start == start && course.end == end && course.room.equals(room), "same classroom and periods: " + label);
        check(course.teacher.equals(teacher), "expected teacher for " + label + ": " + course.teacher);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static String fixture(String path, boolean noNoticeEntry) {
        switch (path) {
            case "/schedule-test/home":
                return "<button class='menu-item' data-code='S203'>教学安排</button>"
                        + "<a href='/schedule-test/list-form' target='section'>个人课表</a>"
                        + (noNoticeEntry ? "" : "<a href='/student/jxap.jxaptzxx.html' target='section'>调课信息</a>")
                        + "<iframe name='section'></iframe>";
            case "/schedule-test/list-form":
                return termSelector() + "<input id='cxfs_lb' type='radio' onclick=\"document.getElementById('frmReport').src='/schedule-test/list-report'\">"
                        + "<iframe id='frmReport'></iframe>";
            case "/schedule-test/list-report":
                return "<h1>江汉大学学生个人课表 " + TERM + "</h1><p>课程门数：3</p><table>"
                        + "<tr><td>上课班级<br>代码</td><td>课程</td><td>学分</td><td>任课教师</td><td>上课时间地点</td></tr>"
                        + "<tr><td>9112233445-033</td><td>[112233445]测试体育</td><td>2</td><td>[101]原教师</td>"
                        + "<td>11,13周 五[3-4] 测试操场-2(48)；12周 五[3-4] 测试操场-2(48)</td></tr>"
                        + "<tr><td>9112233446-010</td><td>[112233446-1]测试实验</td><td>2</td><td>[201]原实验教师</td>"
                        + "<td>15-17周 一[2-3] J05-101(48)</td></tr>"
                        + "<tr><td>9112233447-002</td><td>[112233447]测试选修</td><td>2</td><td>[301]原选修教师</td>"
                        + "<td>12周 三[5-6] A101(48)</td></tr></table>";
            case "/student/jxap.jxaptzxx.html":
                return termSelector() + "<button id='btnSubmit' onclick=\"document.getElementById('frmReport').src='/student/jxap.jxaptzxx_rpt.jsp'\">检索</button>"
                        + "<iframe id='frmReport'></iframe>";
            case "/student/jxap.jxaptzxx_rpt.jsp":
                return notice("[112233445]测试体育", "033", "12", "五[3-4节]", "测试操场-2", "[102]新教师", "[103]最终教师", "2026-11-06 18:30", "线上教学")
                        + notice("[112233445]测试体育", "033", "12", "五[3-4节]", "测试操场-2", "[101]原教师", "[102]新教师", "2026-10-30 10:46", "讲授")
                        + notice("[112233446-1]测试实验", "010", "16", "一[2-3节]", "J05-101", "[201]原实验教师", "[202]新实验教师", "2026-10-30 10:46", "讲授")
                        + notice("[112233447]测试选修", "002", "12", "三[5-6节]", "B202", "[301]原选修教师", "[302]新选修教师", "2026-10-30 10:46", "讲授");
            default:
                return "";
        }
    }

    private static String termSelector() {
        return "<select id='xnxq'><option value='current'>" + TERM + "</option></select>";
    }

    private static String notice(String course, String classNumber, String week, String period, String room,
            String before, String after, String timestamp, String delivery) {
        return "<article><h1>江汉大学调课通知 " + TERM + "</h1><p>调课时间：" + timestamp + "</p>"
                + "<p>调课</p><p>课 程：" + course + "</p><p>上课班号：" + classNumber + "</p>"
                + "<table><tr><td colspan='2'>调课项目</td><td>调课前</td><td>调课后</td></tr>"
                + field("周 次", week, week, false) + field("节 次", period, period, false)
                + field("地 点", room, room, false) + field("教 师", before, after, true)
                + field("授课方式", "讲授", delivery, !delivery.equals("讲授")) + "</table></article>";
    }

    private static String field(String label, String before, String after, boolean changed) {
        return "<tr><td><input type='checkbox' disabled " + (changed ? "checked" : "") + "></td><td>"
                + label + "</td><td>" + before + "</td><td>" + after + "</td></tr>";
    }
}
