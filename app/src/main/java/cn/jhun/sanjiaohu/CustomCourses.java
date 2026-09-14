package cn.jhun.sanjiaohu;

import java.util.*;

/** Local course validation and display composition never mutate the network schedule. */
public final class CustomCourses {
    private CustomCourses(){}
    public static Course create(String id,String term,String name,String teacher,String room,int day,int start,int end,String weeks){
        if(name.trim().isEmpty()||name.length()>80)throw new IllegalArgumentException("课程名称请填写 1–80 个字");
        if(day<1||day>7||start<1||end>12||start>end)throw new IllegalArgumentException("请检查星期和起止节次");
        if(term.trim().isEmpty())throw new IllegalArgumentException("请选择学期范围");
        Course c=new Course();c.localId=id==null?UUID.randomUUID().toString():id;c.term=term;
        c.name=name.trim();c.teacher=teacher.trim();c.room=room.trim();c.day=day;c.start=start;c.end=end;c.weekText=weeks.trim();
        c.weeks=Course.parseWeeks(c.weekText,60);if(c.weeks.isEmpty())throw new IllegalArgumentException("所选周次没有课程");
        return c;
    }
    public static List<Course> at(List<Course> network,List<Course> local,String term,int week){
        List<Course> result=new ArrayList<>();
        for(Course c:network)if(c.weeks.contains(week))result.add(c);
        for(Course c:local)if((c.term.equals("*")||c.term.equals(term))&&c.weeks.contains(week))result.add(c);
        result.sort(Comparator.comparingInt((Course c)->c.day).thenComparingInt(c->c.start).thenComparing(c->c.name));return result;
    }
}
