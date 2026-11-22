package cn.jhun.sanjiaohu;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Personal notes stay independent of the school's replaceable schedule data. */
final class CourseNotes {
    static final int MAX_LENGTH=2000;
    private final Properties notes=new Properties();

    static String key(Course course,String term){
        if(isLocal(course))return "local|"+part(course.localId);
        return "school-name|"+schoolIdentity(course,term);
    }
    private static boolean isLocal(Course course){return course.localId!=null&&!course.localId.isEmpty();}
    private static String schoolIdentity(Course course,String term){
        String semester=clean(term);
        try{semester=Term.key(semester);}catch(IllegalArgumentException ignored){}
        return part(semester)+part(clean(course.name));
    }
    private static String clean(String value){return value==null?"":value.trim();}
    private static String part(String value){return value.length()+":"+value;}
    private Set<String> legacyKeys(Course course,String term){
        Set<String> matches=new TreeSet<>();
        if(isLocal(course))return matches;
        String prefix="school|"+schoolIdentity(course,term);
        for(String oldKey:notes.stringPropertyNames())if(oldKey.startsWith(prefix)){
            String teacherPart=oldKey.substring(prefix.length());
            int separator=teacherPart.indexOf(':');
            if(separator>0&&teacherPart.substring(0,separator).equals(String.valueOf(teacherPart.length()-separator-1)))matches.add(oldKey);
        }
        return matches;
    }
    String get(Course course,String term){
        String saved=notes.getProperty(key(course,term));
        if(saved!=null)return saved;
        Set<String> legacyText=new LinkedHashSet<>();
        for(String oldKey:legacyKeys(course,term)){
            String text=notes.getProperty(oldKey,"");
            if(!text.isEmpty())legacyText.add(text);
        }
        return String.join("\n\n",legacyText);
    }
    static int limit(String existing){return Math.max(MAX_LENGTH,existing.length());}
    void put(Course course,String term,String value){
        String note=clean(value);
        int max=limit(get(course,term));
        if(note.length()>max)throw new IllegalArgumentException("备注最多 "+max+" 字");
        String key=key(course,term);
        // Replace or clear the legacy entries in the same atomic write as the shared note.
        for(String oldKey:legacyKeys(course,term))notes.remove(oldKey);
        if(note.isEmpty())notes.remove(key);else notes.setProperty(key,note);
    }
    static CourseNotes read(InputStream input)throws IOException{
        CourseNotes result=new CourseNotes();result.notes.load(input);return result;
    }
    void write(OutputStream output)throws IOException{notes.store(output,"Sanjiaohu course notes");}
}
