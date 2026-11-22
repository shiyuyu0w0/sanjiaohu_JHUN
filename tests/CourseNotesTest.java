package cn.jhun.sanjiaohu;

import java.io.*;
import java.util.Properties;

public final class CourseNotesTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Course course(String name,String teacher){
        Course c=new Course();c.name=name;c.teacher=teacher;c.room="A101";c.day=1;c.start=1;c.end=2;return c;
    }
    static CourseNotes reopen(CourseNotes notes)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();notes.write(output);
        return CourseNotes.read(new ByteArrayInputStream(output.toByteArray()));
    }
    public static void main(String[] args)throws Exception{
        String term="2026-2027学年第一学期";
        Course physics=course("大学物理","张老师");
        CourseNotes notes=new CourseNotes();
        check(notes.get(physics,term).isEmpty(),"new course has no note");
        String text="带实验报告\n第 3 章习题；公式 a=b:c\\d ☀️";
        notes.put(physics,term,"  "+text+"  ");notes=reopen(notes);
        check(notes.get(physics,term).equals(text),"multiline Unicode and punctuation survive persistence");
        Course refreshed=course("大学物理","张老师");refreshed.room="B202";refreshed.day=5;refreshed.start=7;refreshed.end=8;refreshed.weekText="3-16";
        check(notes.get(refreshed,term).equals(text),"refresh and timetable changes preserve the note");
        check(notes.get(refreshed,"2026－2027 学年 第1学期").equals(text),"equivalent term labels share the note");
        check(notes.get(refreshed,"2026-2027学年第二学期").isEmpty(),"different semesters stay isolated");
        check(notes.get(course("大学物理","李老师"),term).equals(text),"same-term course name shares notes after a teacher change");
        check(notes.get(course("大学英语","张老师"),term).isEmpty(),"different course names stay isolated");
        Course local=course("大学物理","张老师");local.localId="custom-1";
        notes.put(local,"*","自定义安排的提醒");notes=reopen(notes);
        check(notes.get(local,"*").equals("自定义安排的提醒"),"custom course note persists");
        check(notes.get(physics,term).equals(text),"custom and imported courses do not collide");
        Course edited=course("改名后的自习","");edited.localId="custom-1";
        check(notes.get(edited,"另一个学期").equals("自定义安排的提醒"),"editing a custom course keeps its note by ID");
        Course another=course("改名后的自习","");another.localId="custom-2";
        check(notes.get(another,"*").isEmpty(),"separate custom courses are isolated");
        notes.put(physics,term,"修改后的备注");notes=reopen(notes);
        check(notes.get(refreshed,term).equals("修改后的备注"),"edits replace the old note");
        notes.put(physics,term," \n\t ");notes=reopen(notes);
        check(notes.get(physics,term).isEmpty(),"saving empty text removes the note");
        check(notes.get(local,"*").equals("自定义安排的提醒"),"deleting one note preserves the others");
        String max=new String(new char[CourseNotes.MAX_LENGTH]).replace('\0','字');
        notes.put(physics,term,max);notes=reopen(notes);
        check(notes.get(physics,term).equals(max),"maximum-length note persists");
        try{notes.put(physics,term,max+"字");throw new AssertionError("oversized note accepted");}
        catch(IllegalArgumentException expected){check(notes.get(physics,term).equals(max),"invalid note leaves previous text intact");}
        check(!CourseNotes.key(course("a|b","c"),term).equals(CourseNotes.key(course("a","b|c"),term)),"delimiters cannot mix course identities");
        checkLegacyNotes(term);
        System.out.println("PASS: "+checks+" course note persistence, refresh, isolation and editing checks");
    }
    static void checkLegacyNotes(String term)throws Exception{
        Properties legacy=new Properties();
        legacy.setProperty("school|6:2026-14:大学物理3:张老师","原备注");
        legacy.setProperty("school|6:2026-14:大学物理3:李老师","另一份备注");
        legacy.setProperty("school|6:2026-14:大学物理3:王老师","原备注");
        legacy.setProperty("school|6:2026-24:大学物理3:张老师","下学期");
        legacy.setProperty("school|6:2026-15:大学物理学3:张老师","另一个课名");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();legacy.store(bytes,null);
        CourseNotes notes=CourseNotes.read(new ByteArrayInputStream(bytes.toByteArray()));
        Course replacement=course("大学物理","新老师");
        String combined=notes.get(replacement,term);
        check(combined.contains("原备注")&&combined.contains("另一份备注")&&combined.split("\n\n").length==2,"read distinct legacy entries through a new teacher");
        notes.put(replacement,term,"共享备注");notes=reopen(notes);
        check(notes.get(course("大学物理","张老师"),term).equals("共享备注"),"shared save replaces the legacy group");
        notes.put(replacement,term,"");notes=reopen(notes);
        check(notes.get(replacement,term).isEmpty(),"clearing the shared note also clears legacy fallback");
        check(notes.get(replacement,"2026-2027学年第二学期").equals("下学期"),"clearing leaves the other term intact");
        check(notes.get(course("大学物理学","张老师"),term).equals("另一个课名"),"prefix names remain separate");
        legacy.setProperty("school|6:2026-14:大学物理3:张老师",new String(new char[1500]).replace('\0','甲'));
        legacy.setProperty("school|6:2026-14:大学物理3:李老师",new String(new char[1500]).replace('\0','乙'));
        bytes.reset();legacy.store(bytes,null);
        notes=CourseNotes.read(new ByteArrayInputStream(bytes.toByteArray()));
        String longNote=notes.get(replacement,term);
        check(CourseNotes.limit(longNote)==longNote.length()&&longNote.length()>CourseNotes.MAX_LENGTH,"combined legacy notes are editable without truncation");
        notes.put(replacement,term,longNote);notes=reopen(notes);
        check(notes.get(replacement,term).equals(longNote),"confirmed shared save preserves long existing text");
    }
}
