package cn.jhun.sanjiaohu;

/** Data checks for the about page update notes and the technical-support names. */
public final class AboutChangelogTest {
    static final String[] FEATURES={"课表","成绩","考试查询","电费","网上报修","大物实验报告",
            "自定义课程","校园地图","校历","登录","外观","更新内容","技术支持","覆盖升级"};
    /** Wording that only makes sense to the author; the page must stay readable for students. */
    static final String[] JARGON={"AtomicFile","WebView","Cookie","DOM","重定向","端到端","签名指纹","回归"};

    static int checks, failures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) {
        String[][] updates = AboutActivity.UPDATES;
        check(updates.length == 1, "one update entry instead of a version history, got " + updates.length);
        String title = updates[0][0];
        String body = updates[0][1];
        check("v1.1.0".equals(title), "entry is titled v1.1.0, got " + title);
        check(!title.contains("当前版本") && !title.contains("首个公开版"), "title is just the version: " + title);
        check(body.indexOf('\n') > 0, "entry body is a list of lines");
        String[] lines = body.split("\n");
        check(lines.length >= 9, "entry lists the features, got " + lines.length + " lines");
        for (String line : lines) {
            check(line.startsWith("- "), "line is a bullet item: " + line);
            check(line.length() <= 90, "line stays short (" + line.length() + "): " + line);
        }
        for (String feature : FEATURES) check(body.contains(feature), "update note mentions " + feature);
        for (String jargon : JARGON) check(!body.contains(jargon), "update note avoids internal wording: " + jargon);
        check(!body.contains("\\n"), "entry body uses real line breaks");
        check(!body.contains("`") && !body.contains("**"), "entry body is plain text");
        check(!body.contains("v1.0."), "no per-version history remains");
        check(AboutActivity.SUPPORT.length == 2, "technical support lists two names");
        check("广".equals(AboutActivity.SUPPORT[0]), "first support name is 广");
        check("yy792e".equals(AboutActivity.SUPPORT[1]), "second support name is yy792e");
        for (String name : AboutActivity.SUPPORT) {
            check(!name.trim().isEmpty(), "support name is not blank");
            check(name.length() <= 8, "support name stays short: " + name);
        }
        try {
            Class<?> type = Class.forName("cn.jhun.sanjiaohu.AboutActivity");
            check(type.getDeclaredField("UPDATES").getType() == String[][].class, "about page reads its update notes");
            check(type.getDeclaredField("SUPPORT").getType() == String[].class, "about page reads its support names");
            boolean legacy = false;
            for (java.lang.reflect.Field field : type.getDeclaredFields()) if ("CHANGELOG".equals(field.getName())) legacy = true;
            check(!legacy, "the per-version changelog field is gone");
        } catch (ClassNotFoundException e) {
            check(false, "about page class is on the classpath");
        } catch (NoSuchFieldException e) {
            check(false, "about page exposes its data as UPDATES / SUPPORT: " + e.getMessage());
        }
        System.out.println("About page: " + checks + " checks passed");
        if (failures > 0) { System.out.println(failures + " checks failed"); System.exit(1); }
    }
}
