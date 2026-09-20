package cn.jhun.sanjiaohu;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Each semester and data type has its own atomic cache. */
final class AcademicStore {
    private final File directory;
    AcademicStore(Context context){directory=context.getFilesDir();}
    private AtomicFile file(String kind,String term){if(!kind.equals("schedule")&&!kind.equals("grades")&&!kind.equals("summary")&&!kind.equals("exams"))throw new IllegalArgumentException();return new AtomicFile(new File(directory,kind+"-"+(kind.equals("summary")?"all":Term.key(term))+".json"));}
    JSONObject load(String kind,String term)throws Exception{
        JSONObject data=new JSONObject(new String(file(kind,term).readFully(),StandardCharsets.UTF_8));
        if(kind.equals("schedule")&&data.optString("format").equals("schedule-snapshots-v1"))data=data.getJSONObject("active");
        boolean scope=kind.equals("summary")?data.optString("scope").equals("all"):Term.same(term,data.getString("term"));
        if(!scope||!CachePolicy.usable(data.optLong("savedAt"),data.optString("source")))throw new IOException("缓存校验失败");
        return data;
    }
    void save(String kind,JSONObject data)throws Exception{
        if(kind.equals("schedule")){commitSchedule(data);return;}
        AtomicFile target=file(kind,data.getString("term"));FileOutputStream stream=null;
        try{stream=target.startWrite();stream.write(data.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception error){if(stream!=null)target.failWrite(stream);throw error;}
    }
    private JSONObject snapshots(String term)throws Exception{
        JSONObject raw;
        try{raw=new JSONObject(new String(file("schedule",term).readFully(),StandardCharsets.UTF_8));}catch(FileNotFoundException missing){return new JSONObject().put("format","schedule-snapshots-v1").put("term",term);}
        if(raw.optString("format").equals("schedule-snapshots-v1")){if(!Term.same(term,raw.optString("term")))throw new IOException("快照学期不匹配");return raw;}
        if(!Term.same(term,raw.optString("term")))throw new IOException("课表学期不匹配");new Schedule(raw);
        return new JSONObject().put("format","schedule-snapshots-v1").put("term",term).put("active",raw);
    }
    private void writeSnapshots(JSONObject state)throws Exception{
        AtomicFile target=file("schedule",state.getString("term"));FileOutputStream stream=null;
        try{stream=target.startWrite();stream.write(state.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception error){if(stream!=null)target.failWrite(stream);throw error;}
    }
    JSONObject scheduleSnapshot(String term,String name)throws Exception{
        if(!name.equals("candidate")&&!name.equals("previous"))throw new IllegalArgumentException();JSONObject data=snapshots(term).optJSONObject(name);
        if(data!=null){if(!Term.same(term,data.optString("term")))throw new IOException("快照学期不匹配");new Schedule(data);}return data;
    }
    boolean stageSchedule(JSONObject fresh)throws Exception{
        String term=fresh.getString("term");JSONObject state=snapshots(term),active=state.optJSONObject("active");fresh.put("fingerprint",ScheduleRevision.fingerprint(fresh));
        if(ScheduleRevision.needsReview(active,fresh)){state.put("candidate",fresh).put("checkedAt",System.currentTimeMillis());writeSnapshots(state);return false;}
        commitSchedule(fresh);return true;
    }
    private void commitSchedule(JSONObject fresh)throws Exception{
        new Schedule(fresh);JSONObject state=snapshots(fresh.getString("term")),active=state.optJSONObject("active");
        String hash=ScheduleRevision.fingerprint(fresh);if(active!=null&&!ScheduleRevision.fingerprint(active).equals(hash))state.put("previous",active);
        fresh.put("fingerprint",hash);state.put("active",fresh).put("checkedAt",System.currentTimeMillis());state.remove("candidate");writeSnapshots(state);
    }
    void acceptSchedule(String term,String fingerprint)throws Exception{
        JSONObject candidate=scheduleSnapshot(term,"candidate");if(candidate==null||!ScheduleRevision.fingerprint(candidate).equals(fingerprint))throw new IOException("候选课表已变化，请重新查看");commitSchedule(candidate);
    }
    void discardSchedule(String term)throws Exception{JSONObject state=snapshots(term);state.remove("candidate");writeSnapshots(state);}
    void restoreSchedule(String term)throws Exception{
        JSONObject state=snapshots(term),previous=scheduleSnapshot(term,"previous");if(previous==null)throw new IOException("尚无上一份课表");JSONObject active=state.optJSONObject("active");state.put("active",previous).put("previous",active);state.remove("candidate");writeSnapshots(state);
    }
}
