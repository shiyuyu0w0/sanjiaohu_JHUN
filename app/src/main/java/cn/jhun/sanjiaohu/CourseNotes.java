package cn.jhun.sanjiaohu;

import java.io.*;
import java.util.Properties;

/** Personal notes stay independent of the school's replaceable schedule data. */
final class CourseNotes {
    static final int MAX_LENGTH=2000;
    private final Properties notes=new Properties();

    static String key(Course course,String term){
        if(course.localId!=null&&!course.localId.isEmpty())return "local|"+part(course.localId);
        String semester=clean(term);
        try{semester=Term.key(semester);}catch(IllegalArgumentException ignored){}
        // A course can have several weekly arrangements; room/time/week changes must not erase its note.
        return "school|"+part(semester)+part(clean(course.name))+part(clean(course.teacher));
    }
    private static String clean(String value){return value==null?"":value.trim();}
    private static String part(String value){return value.length()+":"+value;}
    String get(Course course,String term){return notes.getProperty(key(course,term),"");}
    void put(Course course,String term,String value){
        String note=clean(value);
        if(note.length()>MAX_LENGTH)throw new IllegalArgumentException("备注最多 "+MAX_LENGTH+" 字");
        String key=key(course,term);
        if(note.isEmpty())notes.remove(key);else notes.setProperty(key,note);
    }
    static CourseNotes read(InputStream input)throws IOException{
        CourseNotes result=new CourseNotes();result.notes.load(input);return result;
    }
    void write(OutputStream output)throws IOException{notes.store(output,"Sanjiaohu course notes");}
}
