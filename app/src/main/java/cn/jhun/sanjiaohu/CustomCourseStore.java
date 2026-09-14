package cn.jhun.sanjiaohu;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate from schedule.json: syncing school data can never replace user-created courses. */
public final class CustomCourseStore {
    private final AtomicFile file;
    public CustomCourseStore(Context c){file=new AtomicFile(new File(c.getFilesDir(),"custom-courses.json"));}
    public List<Course> load()throws Exception{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new ArrayList<>();
        JSONObject root=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        if(root.getInt("version")!=1)throw new IOException("未知自定义课程格式");
        List<Course> result=new ArrayList<>();JSONArray rows=root.getJSONArray("courses");
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);result.add(CustomCourses.create(r.getString("id"),r.getString("term"),r.getString("name"),r.optString("teacher"),r.optString("room"),r.getInt("day"),r.getInt("start"),r.getInt("end"),r.getString("weeks")));}return result;
    }
    public void save(List<Course> courses)throws Exception{
        JSONArray rows=new JSONArray();for(Course c:courses)rows.put(new JSONObject().put("id",c.localId).put("term",c.term).put("name",c.name).put("teacher",c.teacher).put("room",c.room).put("day",c.day).put("start",c.start).put("end",c.end).put("weeks",c.weekText));
        byte[] bytes=new JSONObject().put("version",1).put("courses",rows).toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
}
