package cn.jhun.sanjiaohu;

import java.util.*;

/** Visible blocks use half-open minute intervals for accurate collision lanes. */
final class TimetableEntry {
    final Course course;final ScheduleEvent event;final int day,start,end;
    int lane,lanes=1;
    TimetableEntry(Course course,ScheduleEvent event,int day,int start,int end){this.course=course;this.event=event;this.day=day;this.start=start;this.end=end;}
    String title(){return event==null?course.name:event.title;}
    static List<TimetableEntry> create(List<Course> courses,List<ScheduleEvent> events,Schedule schedule){
        List<TimetableEntry> entries=new ArrayList<>();
        for(Course c:courses)for(int first=c.start;first<=c.end;first=((first-1)/4+1)*4+1){int last=Math.min(c.end,((first-1)/4+1)*4);entries.add(new TimetableEntry(c,null,c.day,ScheduleEvent.minute(schedule.starts[first]),ScheduleEvent.minute(schedule.ends[last])));}
        for(ScheduleEvent e:events)entries.add(new TimetableEntry(null,e,e.date.getDayOfWeek().getValue(),e.start,e.end));
        entries.sort(Comparator.comparingInt((TimetableEntry e)->e.day).thenComparingInt(e->e.start).thenComparing(e->e.title()));
        List<TimetableEntry> cluster=new ArrayList<>();int day=0,end=0;
        for(TimetableEntry e:entries){if(e.day!=day||e.start>=end){assign(cluster);cluster.clear();end=0;}cluster.add(e);day=e.day;end=Math.max(end,e.end);}assign(cluster);return entries;
    }
    static void assign(List<TimetableEntry> entries){List<Integer> ends=new ArrayList<>();for(TimetableEntry e:entries){int lane=0;while(lane<ends.size()&&ends.get(lane)>e.start)lane++;if(lane==ends.size())ends.add(e.end);else ends.set(lane,e.end);e.lane=lane;}for(TimetableEntry e:entries)e.lanes=ends.size();}
}
