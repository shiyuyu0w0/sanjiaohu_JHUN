package cn.jhun.sanjiaohu;

import java.io.*;

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
        check(notes.get(course("大学物理","李老师"),term).isEmpty(),"different teachers stay isolated");
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
        System.out.println("PASS: "+checks+" course note persistence, refresh, isolation and editing checks");
    }
}
