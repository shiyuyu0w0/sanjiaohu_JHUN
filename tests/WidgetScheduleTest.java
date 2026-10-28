package cn.jhun.sanjiaohu;

import java.time.*;
import java.util.*;

public final class WidgetScheduleTest {
    static int checks;
    static final LocalDate ANCHOR=LocalDate.of(2026,10,19);
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static ZonedDateTime at(String value){return LocalDateTime.parse(value).atZone(WidgetSchedule.ZONE);}
    static Course course(String term,String name,int day,int start,int end,String weeks){return CustomCourses.create(null,term,name,"","J02A212",day,start,end,weeks);}
    static WidgetSchedule model(Schedule s,List<Course> local,LocalDate anchor,String now,boolean hasData,int capacity){return WidgetSchedule.create(s,local,anchor,at(now),hasData,capacity);}
    public static void main(String[] args){
        Schedule schedule=Schedule.local("测试学期");
        Course morning=course("测试学期","早课",3,1,2,"1-20"),middle=course("测试学期","中课",3,3,4,"2-20(双)"),afternoon=course("*","自习",3,5,6,"1-20"),night=course("测试学期","晚课",3,9,10,"1-20");
        schedule.courses.add(morning);schedule.courses.add(middle);
        List<Course> local=Arrays.asList(afternoon,night,course("其他学期","不应出现",3,7,8,"1-20"),course("*","周四",4,1,2,"1-20"));
        WidgetSchedule noData=model(schedule,local,ANCHOR,"2026-10-28T08:00",false,2);check(noData.visible.isEmpty()&&noData.message.equals("还没有课表"),"missing cache is not a free day");
        WidgetSchedule uncalibrated=model(schedule,local,null,"2026-10-28T08:00",true,2);check(uncalibrated.visible.isEmpty()&&uncalibrated.message.equals("先校准周次"),"no fabricated week one");
        WidgetSchedule before=model(schedule,local,ANCHOR,"2026-10-18T08:00",true,2);check(before.visible.isEmpty()&&before.message.equals("学期还未开始"),"before term is not clamped to week one");
        WidgetSchedule after=model(schedule,local,ANCHOR,"2027-10-28T08:00",true,2);check(after.visible.isEmpty()&&after.message.equals("本学期已结束"),"after term is not clamped");
        WidgetSchedule early=model(schedule,local,ANCHOR,"2026-10-28T08:00",true,2);check(early.week==2&&early.today.size()==4,"weekday, term, even weeks and custom scope");check(early.visible.equals(Arrays.asList(morning,middle)),"chronological first courses");check(early.footer.equals("另有 2 门课 · 查看全部"),"overflow count");check(early.nextRefresh==at("2026-10-28T08:20").toInstant().toEpochMilli(),"refresh at class start");
        WidgetSchedule during=model(schedule,local,ANCHOR,"2026-10-28T08:20",true,2);check(during.visible.get(0)==morning,"start is inclusive");check(during.nextRefresh==at("2026-10-28T10:00").toInstant().toEpochMilli(),"refresh at class end");
        WidgetSchedule end=model(schedule,local,ANCHOR,"2026-10-28T10:00",true,2);check(end.visible.equals(Arrays.asList(middle,afternoon)),"end is exclusive; next course moves into view");
        WidgetSchedule late=model(schedule,local,ANCHOR,"2026-10-28T22:00",true,2);check(late.visible.equals(Arrays.asList(afternoon,night)),"after classes retain last courses");check(late.nextRefresh==at("2026-10-29T00:00").toInstant().toEpochMilli(),"refresh at midnight");
        WidgetSchedule tall=model(schedule,local,ANCHOR,"2026-10-28T08:00",true,4);check(tall.visible.size()==4,"larger widgets reveal more courses");
        WidgetSchedule odd=model(schedule,local,ANCHOR,"2026-10-21T08:00",true,4);check(odd.today.size()==3&&!odd.today.contains(middle),"even course absent in odd week");
        WidgetSchedule free=model(schedule,local,ANCHOR,"2026-10-30T08:00",true,2);check(free.message.equals("今天没有课程"),"known empty day");
        WidgetSchedule zone=WidgetSchedule.create(schedule,local,ANCHOR,at("2026-10-28T08:00").withZoneSameInstant(ZoneId.of("America/New_York")),true,2);check(zone.today.equals(early.today)&&zone.nextRefresh==early.nextRefresh,"school timezone independent of device zone");
        Course overlap=course("*","重叠课程",3,1,3,"1-20");WidgetSchedule overlapping=model(schedule,Arrays.asList(overlap),ANCHOR,"2026-10-28T09:00",true,2);check(overlapping.visible.size()==2&&overlapping.visible.contains(overlap),"overlapping ongoing courses retained");
        check(schedule.courses.size()==2&&local.size()==4,"source data untouched");
        System.out.println("PASS: "+checks+" desktop schedule checks");
    }
}
