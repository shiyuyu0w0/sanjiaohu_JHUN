package cn.jhun.sanjiaohu;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** A dated, one-off entry. It never participates in repeating teaching-week rules. */
final class ScheduleEvent {
    static final int MAX_NOTES=2000;
    static final int EARLIEST=8*60+20,LATEST=21*60+40;
    final String id,term,title,notes;
    final LocalDate date;
    final int start,end;

    private ScheduleEvent(String id,String term,LocalDate date,int start,int end,String title,String notes){
        this.id=id;this.term=term;this.date=date;this.start=start;this.end=end;this.title=title;this.notes=notes;
    }
    static ScheduleEvent create(String id,String term,LocalDate date,int start,int end,String title,String notes){
        validateTime(start,end);
        return restore(id,term,date,start,end,title,notes);
    }
    static void validateTime(int start,int end){
        if(start<EARLIEST||end>LATEST)throw new IllegalArgumentException("日程时间仅支持 08:20–21:40");
        if(start>=end)throw new IllegalArgumentException("结束时间需晚于开始时间");
    }
    // Older versions allowed the whole day. Read those entries without losing the rest of the file.
    static ScheduleEvent restore(String id,String term,LocalDate date,int start,int end,String title,String notes){
        if(title==null||title.trim().isEmpty()||title.length()>80)throw new IllegalArgumentException("日程标题请填写 1–80 个字");
        if(notes==null||notes.length()>MAX_NOTES)throw new IllegalArgumentException("备注最多填写 2000 个字");
        if(date==null||term==null||term.trim().isEmpty())throw new IllegalArgumentException("请选择日程日期");
        if(start<0||end>=24*60||start>=end)throw new IllegalArgumentException("结束时间需晚于开始时间，且在当天内");
        return new ScheduleEvent(id==null?UUID.randomUUID().toString():id,term,date,start,end,title.trim(),notes.trim());
    }
    Course courseConflict(Schedule schedule,List<Course> local,LocalDate anchor){
        if(!term.equals(schedule.term))return null;
        int week=anchor==null?0:(int)Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(anchor,date),7)+1;
        List<Course> courses=anchor==null?CustomCourses.all(schedule.courses,local,term):CustomCourses.at(schedule.courses,local,term,week);
        for(Course c:courses)if(c.day==date.getDayOfWeek().getValue()){
            // Match the course blocks on the grid, which split at lunch and dinner.
            for(int first=c.start;first<=c.end;first=((first-1)/4+1)*4+1){
                int last=Math.min(c.end,((first-1)/4+1)*4);
                if(start<minute(schedule.ends[last])&&end>minute(schedule.starts[first]))return c;
            }
        }
        return null;
    }
    static List<ScheduleEvent> inWeek(List<ScheduleEvent> events,String term,LocalDate monday){
        List<ScheduleEvent> result=new ArrayList<>();
        for(ScheduleEvent event:events)if(event.term.equals(term)&&!event.date.isBefore(monday)&&event.date.isBefore(monday.plusDays(7)))result.add(event);
        result.sort(Comparator.comparing((ScheduleEvent e)->e.date).thenComparingInt(e->e.start).thenComparing(e->e.title));return result;
    }
    static int minute(String time){LocalTime t=LocalTime.parse(time);return t.getHour()*60+t.getMinute();}
    static String time(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
    String interval(){return time(start)+"–"+time(end);}
    String duration(){return duration(start,end);}
    static String duration(int start,int end){int minutes=end-start;return minutes<60?minutes+" 分钟":minutes/60+" 小时"+(minutes%60==0?"":" "+minutes%60+" 分钟");}
}
