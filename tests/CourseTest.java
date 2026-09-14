package cn.jhun.sanjiaohu;
import java.util.*;
public class CourseTest {
    static int assertions;
    static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
    public static void main(String[]args) {
        Course a=Course.parse("概率论与数理统计（理） 汪继秀(1-4,6-13 J05A212)",2,1,25);
        check(a.name.equals("概率论与数理统计（理）"),"parenthesized title");check(a.teacher.equals("汪继秀"),"teacher");check(!a.weeks.contains(5)&&a.weeks.size()==12,"excluded holiday week");
        Course p=Course.parse("大学物理实验Ⅰ ② 物理组（吕洪方，郑广，詹志明，余华光等)(2-4,6-14 J15A530)",3,9,25);
        check(p.name.equals("大学物理实验Ⅰ ②"),"space in title");check(p.teacher.startsWith("物理组（"),"teacher group");check(p.room.equals("J15A530"),"room");
        check(Course.parseWeeks("3,5,7-8",25).equals(new TreeSet<>(Arrays.asList(3,5,7,8))),"disjoint weeks");
        check(Course.parseWeeks("-15",25).size()==15,"open beginning");check(Course.parseWeeks("10-",25).size()==16,"open end");
        check(Course.parseWeeks("1-10(单)",25).equals(new TreeSet<>(Arrays.asList(1,3,5,7,9))),"odd weeks");check(Course.parseWeeks("1-10双周",25).equals(new TreeSet<>(Arrays.asList(2,4,6,8,10))),"even weeks");
        check(Course.parse("课程 教师(1-10(单) 教室)",1,1,25).weeks.size()==5,"nested parity suffix");
        List<Course> merged=Course.merge(Arrays.asList(Course.parse("课程 教师(1-10 教室)",1,1,25),Course.parse("课程 教师(1-10 教室)",1,2,25),Course.parse("课程 教师(11 教室)",1,2,25)));
        check(merged.size()==2,"keep alternate week course separate");check(merged.stream().anyMatch(c->c.start==1&&c.end==2),"merge adjacent periods");
        check(Course.merge(Arrays.asList(Course.parse("课程 教师(1 教室)",1,4,25),Course.parse("课程 教师(1 教室)",1,5,25))).size()==2,"do not merge across lunch");
        for(String bad:Arrays.asList("26","0","5-2","1,x","1,,3")){boolean failed=false;try{Course.parseWeeks(bad,25);}catch(IllegalArgumentException e){failed=true;}check(failed,"reject invalid weeks: "+bad);}
        System.out.println("PASS: "+assertions+" parser assertions");
    }
}
