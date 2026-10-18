package cn.jhun.sanjiaohu;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

/** Read before writing, and retain the previous notes if a write fails. */
final class CourseNoteStore {
    private final AtomicFile file;
    CourseNoteStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"course-notes.properties"));}
    private CourseNotes load()throws IOException{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new CourseNotes();
        try(InputStream input=file.openRead()){
            return CourseNotes.read(input);
        }catch(IllegalArgumentException error){throw new IOException("课程备注读取失败",error);}
    }
    synchronized String get(Course course,String term)throws IOException{return load().get(course,term);}
    synchronized void save(Course course,String term,String text)throws IOException{
        CourseNotes notes=load();notes.put(course,term,text);
        FileOutputStream output=null;
        try{output=file.startWrite();notes.write(output);file.finishWrite(output);}
        catch(IOException error){if(output!=null)file.failWrite(output);throw error;}
    }
}
