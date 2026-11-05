package cn.jhun.sanjiaohu;
import java.time.LocalDate;
import java.util.*;
public final class ScheduleEventTest {
    static int checks;
    static final LocalDate MONDAY=LocalDate.of(2026,11,2),DATE=MONDAY.plusDays(1);
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void invalid(Runnable action){try{action.run();throw new AssertionError("accepted invalid event");}catch(IllegalArgumentException expected){checks++;}}
    static ScheduleEvent event(int from,int to){return ScheduleEvent.create(null,"秋季学期",DATE,from,to,"讨论","带上笔记\n准备提纲");}
    public static void main(String[] args){
        Schedule schedule=Schedule.local("秋季学期");ScheduleEvent e=event(635,680);
        check(e.interval().equals("10:35–11:20")&&e.duration().equals("45 分钟"),"arbitrary minutes and real duration");
        check(e.notes.contains("\n"),"multiline notes");List<ScheduleEvent> all=Collections.singletonList(e);
        check(ScheduleEvent.inWeek(all,"秋季学期",MONDAY).size()==1,"selected week");
        check(ScheduleEvent.inWeek(all,"秋季学期",MONDAY.minusWeeks(1)).isEmpty(),"not previous week");
        check(ScheduleEvent.inWeek(all,"秋季学期",MONDAY.plusWeeks(1)).isEmpty(),"not next week");
        check(ScheduleEvent.inWeek(all,"春季学期",MONDAY).isEmpty(),"not another term");
        TimetableTimeline base=new TimetableTimeline(schedule);
        for(int[] size:new int[][]{{320,400},{1080,1500}}){
            GridGeometry g=new GridGeometry(size[0],size[1],33,32,14);
            for(int p=1;p<=12;p++){
                int[] old=g.bounds(2,2,p,p,0,1),fresh=base.bounds(g,2,2,p,p,0,1);
                check(Math.abs(old[1]-fresh[1])<=1&&Math.abs(old[3]-fresh[3])<=1,"empty timeline preserves course layout");
            }
            List<ScheduleEvent> examples=Arrays.asList(event(500,501),event(500,1300),event(547,548),event(635,680),event(757,792),event(1299,1300));
            TimetableTimeline timeline=new TimetableTimeline(schedule);
            for(ScheduleEvent item:examples){int[] r=timeline.bounds(g,5,2,item.start,item.end,0,1);check(r[1]>=g.header&&r[3]<=g.height&&r[3]>r[1],"all-day minute intervals visible");}
            int previous=g.header;for(int minute=0;minute<=1439;minute++){int y=timeline.y(minute,g);check(y>=previous&&y<=g.height,"minute map monotonic and bounded");previous=y;}
            int[] shortBox=timeline.bounds(g,5,2,635,650,0,1),longBox=timeline.bounds(g,5,2,635,680,0,1);
            check(shortBox[1]==longBox[1]&&longBox[3]>shortBox[3],"height follows minutes");
            check(timeline.y(635,g)>timeline.y(620,g)&&timeline.y(635,g)<timeline.y(665,g),"start can lie inside a period");
            int[] spanning=timeline.bounds(g,5,2,620,720,0,1),cells=g.bounds(2,2,3,4,0,1);
            check(Arrays.equals(spanning,cells),"long event fills the next existing cell without resizing rows");
            check(timeline.y(720,g)==g.periodBottom(4)&&timeline.y(840,g)==g.periodTop(5),"lunch keeps its original compact gap");
        }
        Course course=CustomCourses.create(null,"秋季学期","英语","","A101",2,3,3,"1");
        List<TimetableEntry> adjacent=TimetableEntry.create(Collections.singletonList(course),Arrays.asList(event(665,680),event(680,700)),schedule);
        for(TimetableEntry entry:adjacent)check(entry.lanes==1,"touching endpoints do not overlap");
        List<TimetableEntry> overlap=TimetableEntry.create(Collections.singletonList(course),Collections.singletonList(event(635,680)),schedule);
        check(overlap.get(0).lanes==2&&overlap.get(1).lanes==2&&overlap.get(0).lane!=overlap.get(1).lane,"actual overlapping minutes get separate lanes");
        invalid(()->event(620,620));invalid(()->event(700,620));invalid(()->event(-1,100));invalid(()->event(1400,1440));
        invalid(()->ScheduleEvent.create(null,"秋季学期",DATE,620,665," ",""));
        invalid(()->ScheduleEvent.create(null,"秋季学期",null,620,665,"会议",""));
        invalid(()->event(499,545));invalid(()->event(1255,1301));
        check(event(500,1300).duration().equals("13 小时 20 分钟"),"inclusive 08:20 and 21:40 limits");
        ScheduleEvent legacy=ScheduleEvent.restore("legacy","秋季学期",DATE,0,1439,"旧日程","");
        check(legacy.start==0&&legacy.end==1439,"old out-of-range entries stay readable without silently changing them");
        conflictRules(schedule);
        System.out.println("PASS: "+checks+" bounded-minute event, fixed grid, date and course conflict checks");
    }
    static void conflictRules(Schedule schedule){
        Course school=CustomCourses.create(null,schedule.term,"英语","","A101",2,3,4,"2");schedule.courses.add(school);
        List<Course> local=new ArrayList<>();LocalDate anchor=MONDAY.minusWeeks(1);
        check(event(635,680).courseConflict(schedule,local,anchor)==school,"reject school course in actual event week");
        check(event(619,621).courseConflict(schedule,local,anchor)==school,"one-minute overlap at course start");
        check(event(719,721).courseConflict(schedule,local,anchor)==school,"one-minute overlap at course end");
        check(event(500,620).courseConflict(schedule,local,anchor)==null,"ending at course start is allowed");
        check(event(720,800).courseConflict(schedule,local,anchor)==null,"starting at course end is allowed");
        check(event(635,680).courseConflict(schedule,local,MONDAY)==null,"other teaching week has no conflict");
        check(event(635,680).courseConflict(schedule,local,MONDAY.plusWeeks(1))==null,"dates before term do not truncate to week one");
        check(ScheduleEvent.create(null,schedule.term,MONDAY,635,680,"星期一","").courseConflict(schedule,local,anchor)==null,"other weekday has no conflict");
        check(event(635,680).courseConflict(schedule,local,null)==school,"uncalibrated checks the courses displayed");
        Course custom=CustomCourses.create(null,"*","自定义课程","","",2,6,6,"2");local.add(custom);
        check(event(889,891).courseConflict(schedule,local,anchor)==custom,"local wildcard courses also conflict");
        custom.term="其他学期";check(event(889,891).courseConflict(schedule,local,anchor)==null,"other-term custom course ignored");
        custom.term=schedule.term;check(event(889,891).courseConflict(schedule,local,anchor)==custom,"same-term custom course conflicts");
        school.end=5;check(event(730,800).courseConflict(schedule,local,anchor)==null,"meal break between course blocks remains free");
        check(event(500,1300).courseConflict(schedule,local,anchor)==school,"long event crossing several cells checks all courses");
    }
}
