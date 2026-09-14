package cn.jhun.sanjiaohu;

import org.json.*;
import java.util.*;
import java.util.regex.*;

public final class Schedule {
    public final String term;
    public final int maxWeek;
    public final List<Course> courses;
    public final String[] starts = new String[13], ends = new String[13];
    public final JSONObject json;
    public long savedAt;
    public static Schedule local(){return local("自定义学期");}
    public static Schedule local(String term){return new Schedule(term);}
    private Schedule(String selectedTerm){
        term=selectedTerm;maxWeek=20;courses=new ArrayList<>();json=null;
        defaultTimes();
    }
    private void defaultTimes(){
        String[] time={"08:20-09:05","09:15-10:00","10:20-11:05","11:15-12:00","14:00-14:45","14:50-15:35","15:55-16:40","16:45-17:30","18:30-19:15","19:15-20:00","20:10-20:55","20:55-21:40"};
        for(int i=0;i<12;i++){String[] pair=time[i].split("-");starts[i+1]=pair[0];ends[i+1]=pair[1];}
    }
    public Schedule(JSONObject data) throws Exception {
        json=data;
        int version=data.getInt("version");
        if(version!=1 && version!=2) throw new IllegalArgumentException("未知缓存格式");
        term=data.getString("term"); maxWeek=data.getInt("maxWeek");
        if(!term.contains("学期") || maxWeek<1 || maxWeek>60) throw new IllegalArgumentException("学期不完整");
        savedAt=data.optLong("savedAt",0);
        if(version==2){
            if(!data.getBoolean("complete"))throw new IllegalArgumentException("课表未完整加载");
            defaultTimes();List<Course> all=new ArrayList<>();JSONArray items=data.getJSONArray("courses");Set<String> seen=new HashSet<>();
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);Course c=new Course();c.name=item.getString("name");c.teacher=item.getString("teacher");c.room=item.getString("room");c.weekText=item.getString("weekText");c.day=item.getInt("day");c.start=item.getInt("start");c.end=item.getInt("end");c.term=term;c.raw=c.name+" "+c.teacher+" "+c.weekText+" "+c.room;
                c.weeks=Course.parseWeeks(c.weekText,maxWeek);
                if(c.name.trim().isEmpty()||c.day<1||c.day>7||c.start<1||c.end>12||c.start>c.end||c.weeks.isEmpty())throw new IllegalArgumentException("课程字段异常");
                if(seen.add(c.key()+"|"+c.start+"|"+c.end))all.add(c);
            }
            courses=Course.merge(all);return;
        }
        JSONArray rows=data.getJSONArray("rows");
        if(rows.length()!=12) throw new IllegalArgumentException("课表未加载完整");
        List<Course> all=new ArrayList<>();
        Pattern p=Pattern.compile("^(\\d+)\\s*[（(](\\d{2}:\\d{2})-(\\d{2}:\\d{2})[)）]$");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i); Matcher m=p.matcher(row.getString("period"));
            if(!m.matches() || Integer.parseInt(m.group(1))!=i+1) throw new IllegalArgumentException("节次顺序异常");
            starts[i+1]=m.group(2); ends[i+1]=m.group(3);
            JSONArray days=row.getJSONArray("days"); if(days.length()!=7) throw new IllegalArgumentException("星期不完整");
            for(int d=0;d<7;d++) for(String line:days.getString(d).split("\\n")) {
                if(!line.trim().isEmpty()) all.add(Course.parse(line.trim(),d+1,i+1,maxWeek));
            }
        }
        courses=Course.merge(all);
    }
    public List<Course> at(int week,int day) {
        List<Course> out=new ArrayList<>();
        for(Course c:courses) if(c.weeks.contains(week) && (day==0 || c.day==day)) out.add(c);
        return out;
    }
}
