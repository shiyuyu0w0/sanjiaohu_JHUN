package cn.jhun.sanjiaohu;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** The desktop always follows the real teaching day, never the week browsed in the app. */
final class WidgetSchedule {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    final List<Course> today=new ArrayList<>();
    final List<Course> visible=new ArrayList<>();
    String message="",detail="",heading="今天",footer="查看课表";
    int week;
    long nextRefresh;

    static WidgetSchedule create(Schedule schedule,List<Course> local,LocalDate anchor,ZonedDateTime instant,boolean hasData,int capacity){
        ZonedDateTime now=instant.withZoneSameInstant(ZONE);LocalDate date=now.toLocalDate();
        WidgetSchedule out=new WidgetSchedule();out.nextRefresh=date.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        if(!hasData){out.message="还没有课表";out.detail="点击导入或添加课程";return out;}
        if(anchor==null){out.message="先校准周次";out.detail="打开课表，点击“去校准”";return out;}
        int max=schedule.maxWeek;for(Course c:local)if(c.term.equals("*")||c.term.equals(schedule.term))for(int w:c.weeks)max=Math.max(max,w);
        long days=ChronoUnit.DAYS.between(anchor,date);out.week=(int)Math.floorDiv(days,7)+1;
        if(out.week<1||out.week>max){out.message=out.week<1?"学期还未开始":"本学期已结束";out.detail="可在课表中切换学期或校准";out.week=0;return out;}
        out.heading="今天 · 第 "+out.week+" 周";
        for(Course c:CustomCourses.at(schedule.courses,local,schedule.term,out.week))if(c.day==date.getDayOfWeek().getValue())out.today.add(c);
        if(out.today.isEmpty()){out.message="今天没有课程";out.detail="留一点时间给自己";return out;}
        long millis=now.toInstant().toEpochMilli();List<Course> remaining=new ArrayList<>();
        for(Course c:out.today){
            long start=boundary(date,schedule.starts[c.start]),end=boundary(date,schedule.ends[c.end]);
            if(start>millis)out.nextRefresh=Math.min(out.nextRefresh,start);
            if(end>millis){out.nextRefresh=Math.min(out.nextRefresh,end);remaining.add(c);}
        }
        // Keep ongoing and upcoming courses in view; after class show the day's final courses.
        List<Course> source=remaining.isEmpty()?out.today:remaining;int from=remaining.isEmpty()?Math.max(0,source.size()-capacity):0;
        for(int i=from;i<source.size()&&out.visible.size()<capacity;i++)out.visible.add(source.get(i));
        int other=out.today.size()-out.visible.size();
        out.footer=other>0?"另有 "+other+" 门课 · 查看全部":remaining.isEmpty()?"今日课程已结束":"共 "+out.today.size()+" 门课 · 查看课表";
        return out;
    }
    static long boundary(LocalDate date,String time){return date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant().toEpochMilli();}
}
