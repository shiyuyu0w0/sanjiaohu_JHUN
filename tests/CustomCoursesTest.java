package cn.jhun.sanjiaohu;
import java.util.*;

public final class CustomCoursesTest {
    static int checks;
    static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    static Course custom(String term,String name,String weeks){return CustomCourses.create(null,term,name,"","",2,3,4,weeks);}
    static void invalid(Runnable action){try{action.run();throw new AssertionError("accepted invalid course");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        Course all=custom("*","自习","1-16(单)"),spring=custom("春季学期","实验","1-20"),autumn=custom("秋季学期","训练","2,4,6");
        List<Course> local=new ArrayList<>(Arrays.asList(all,spring,autumn));
        Course remote=Course.parse("高等数学 张老师(1-16 A101)",2,3,20);
        List<Course> oldNetwork=new ArrayList<>(Collections.singletonList(remote));
        check(CustomCourses.at(oldNetwork,local,"春季学期",3).size()==3,"remote and overlapping custom courses coexist");
        check(CustomCourses.at(oldNetwork,local,"春季学期",4).size()==2,"odd weeks respected");
        List<Course> uncalibrated=CustomCourses.all(oldNetwork,local,"春季学期");
        check(uncalibrated.size()==3&&uncalibrated.contains(remote)&&uncalibrated.contains(all)&&uncalibrated.contains(spring),"uncalibrated grid shows all relevant courses once");
        check(!uncalibrated.contains(autumn),"uncalibrated grid keeps the selected term");
        check(CustomCourses.all(oldNetwork,local,"春季学期").size()==3,"uncalibrated grid does not duplicate a course for each week");
        check(CustomCourses.at(Collections.emptyList(),local,"春季学期",3).contains(spring),"empty refresh preserves custom");
        check(CustomCourses.at(Collections.emptyList(),local,"秋季学期",4).contains(autumn),"term scope");
        check(!CustomCourses.at(oldNetwork,local,"秋季学期",4).contains(spring),"do not leak other term");
        check(CustomCourses.at(oldNetwork,local,"冬季学期",3).contains(all),"all-term course survives term change");
        Course replacement=Course.parse("大学英语 李老师(1-18 B202)",1,1,20);
        List<Course> refreshed=CustomCourses.at(Collections.singletonList(replacement),local,"春季学期",3);
        check(refreshed.contains(spring)&&!refreshed.contains(remote),"refresh replaces only network courses");
        check(oldNetwork.size()==1&&local.size()==3,"display does not mutate either store");
        check(local.get(0).weeks.equals(new TreeSet<>(Arrays.asList(1,3,5,7,9,11,13,15))),"week values survive composition");
        Course edited=CustomCourses.create(spring.localId,"春季学期","实验改期","老师","C305",7,11,12,"18-20");
        check(edited.localId.equals(spring.localId)&&edited.end==12,"edit preserves identity and last period");
        check(!edited.localId.equals(all.localId),"independent local identities");
        invalid(()->custom("*"," ","1-16"));invalid(()->custom("*","课程","0-10"));invalid(()->custom("*","课程","1-61"));invalid(()->custom("*","课程","6-2"));invalid(()->custom("*","课程","1(双)"));
        invalid(()->CustomCourses.create(null,"*","课程","","",8,1,2,"1-2"));invalid(()->CustomCourses.create(null,"*","课程","","",1,3,2,"1-2"));invalid(()->CustomCourses.create(null,"*","课程","","",1,1,13,"1-2"));
        Set<Integer> colors=new HashSet<>();for(int color:CourseColors.PALETTE){check(colors.add(color),"distinct palette entries");check(ThemePalette.contrast(ThemePalette.neutralText(color),color)>=4.5,"readable card text");check((color>>>24)==255,"wallpaper cannot obscure card text");}
        check(CourseAppearance.background(0xff78b7f4,0)==0xff78b7f4,"zero transparency keeps original color");
        check(CourseAppearance.background(0xff78b7f4,100)==0x0078b7f4,"full transparency removes only surface alpha");
        check((CourseAppearance.background(0xff78b7f4,50)>>>24)==128,"midpoint transparency");
        check(CourseAppearance.background(0xff78b7f4,-20)==0xff78b7f4,"clamp old or invalid setting");
        check(CourseAppearance.background(0xff78b7f4,140)==0x0078b7f4,"clamp high setting");
        System.out.println("PASS: "+checks+" custom course isolation, refresh, term/week filters, validation and color checks");
    }
}
