package cn.jhun.sanjiaohu;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Separate atomic storage keeps school syncs and repeating courses independent of events. */
final class ScheduleEventStore {
    private final AtomicFile file;
    private final Context context;
    ScheduleEventStore(Context context){this.context=context.getApplicationContext();file=new AtomicFile(new File(context.getFilesDir(),"schedule-events.json"));}
    ScheduleEventStore(File path){context=null;file=new AtomicFile(path);}
    List<ScheduleEvent> load()throws Exception{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new ArrayList<>();
        JSONObject root=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        int version=root.getInt("version");if(version!=1&&version!=2)throw new IOException("未知日程格式");
        List<ScheduleEvent> result=new ArrayList<>();JSONArray rows=root.getJSONArray("events");
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);
            String term=r.getString("term");int start=r.getInt("start"),end=r.getInt("end");
            if(version==1){
                if(start<1||end>12||start>end)throw new IOException("旧日程节次无效");
                Schedule schedule=legacySchedule(term);start=ScheduleEvent.minute(schedule.starts[start]);end=ScheduleEvent.minute(schedule.ends[end]);
            }
            result.add(ScheduleEvent.restore(r.getString("id"),term,LocalDate.parse(r.getString("date")),start,end,r.getString("title"),r.optString("notes")));
        }
        return result;
    }
    void save(List<ScheduleEvent> events)throws Exception{
        JSONArray rows=new JSONArray();for(ScheduleEvent e:events)rows.put(new JSONObject().put("id",e.id).put("term",e.term).put("date",e.date.toString()).put("start",e.start).put("end",e.end).put("title",e.title).put("notes",e.notes));
        byte[] bytes=new JSONObject().put("version",2).put("events",rows).toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    private Schedule legacySchedule(String term)throws Exception{
        try{Term.key(term);}catch(IllegalArgumentException localTerm){return Schedule.local(term);}
        if(context!=null){try{return new Schedule(new AcademicStore(context).load("schedule",term));}catch(FileNotFoundException missing){/* Local-only schedules use the built-in bell times. */}}
        return Schedule.local(term);
    }
}
