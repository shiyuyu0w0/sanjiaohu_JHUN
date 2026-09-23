package cn.jhun.sanjiaohu;

/** Checks that the about page presents an accurate version history. */
public final class AboutChangelogTest {
    /** Wording that only makes sense to the author; the page must stay readable for students. */
    static final String[] JARGON={"AtomicFile","WebView","Cookie","DOM","重定向","端到端","签名指纹","回归"};

    static int checks, failures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) {
        String[][] updates = AboutActivity.UPDATES;
        String[] versions={"v1.1.3","v1.1.2","v1.1.1","v1.1.0"};
        check(updates.length == versions.length, "one entry per known version");
        for(int i=0;i<updates.length;i++){
            String[] entry=updates[i];
            check(entry.length==2,"version and notes for entry "+i);
            if(entry.length!=2)continue;
            check(i<versions.length&&versions[i].equals(entry[0]),"newest-first version order at "+i);
            String[] lines=entry[1].split("\n");
            check(lines.length>=2&&lines.length<=6,"brief main changes for "+entry[0]);
            for(String line:lines){
                check(line.startsWith("- "),"plain bullet for "+entry[0]);
                check(line.length()<=90,"short line for "+entry[0]);
            }
            for(String jargon:JARGON)check(!entry[1].contains(jargon),"no internal wording in "+entry[0]+": "+jargon);
            check(!entry[1].contains("\\n"),"real line breaks in "+entry[0]);
            check(!entry[1].contains("`")&&!entry[1].contains("**"),"plain text in "+entry[0]);
        }
        if(updates.length>=4){
            check(updates[0][1].contains("刷新")&&updates[0][1].contains("考试"),"v1.1.3 records visual improvements");
            check(updates[1][1].contains("检查新版本")&&updates[1][1].contains("安装包"),"v1.1.2 records the updater");
            check(updates[2][1].contains("课表")&&updates[2][1].contains("跨校区"),"v1.1.1 records fixes");
            check(updates[3][1].contains("考试查询")&&updates[3][1].contains("电量"),"v1.1.0 records new functions");
            check(!updates[0][1].contains("检查新版本"),"v1.1.2 updater is not listed as new in v1.1.3");
        }
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
        } catch (ClassNotFoundException e) {
            check(false, "about page class is on the classpath");
        } catch (NoSuchFieldException e) {
            check(false, "about page exposes its data as UPDATES / SUPPORT: " + e.getMessage());
        }
        System.out.println("About page: " + checks + " checks passed");
        if (failures > 0) { System.out.println(failures + " checks failed"); System.exit(1); }
    }
}
