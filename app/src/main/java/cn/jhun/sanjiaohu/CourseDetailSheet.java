package cn.jhun.sanjiaohu;

import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Course information and local-course actions share the app's current theme. */
final class CourseDetailSheet {
    static void show(MainActivity a,Course c){
        boolean local=c.localId!=null;
        UiSheet sheet=new UiSheet(a,"课程详情",local?"自定义课程":"教务课程",.7f);
        TextView title=a.label(c.name,23,a.INK,true);title.setLineSpacing(a.dp(3),1);sheet.body.addView(title);a.space(sheet.body,16);
        LinearLayout timing=a.column();timing.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(14));timing.setBackground(a.shape(a.palette.entrySurface,18));
        timing.addView(a.label("周"+"一二三四五六日".charAt(c.day-1)+"  ·  第 "+c.start+"–"+c.end+" 节",17,a.palette.deepAccent,true));
        TextView hours=a.label(a.activeSchedule().starts[c.start]+" — "+a.activeSchedule().ends[c.end],13,a.INK,false);hours.setPadding(0,a.dp(7),0,0);timing.addView(hours);sheet.body.addView(timing);a.space(sheet.body,16);
        info(a,sheet,"上课地点",c.room.isEmpty()?"未填写":c.room);
        info(a,sheet,"授课教师",c.teacher.isEmpty()?"未填写":c.teacher);
        info(a,sheet,"教学周",c.weekText);
        info(a,sheet,"适用学期",local?(c.term.equals("*")?"所有学期":c.term):a.activeSchedule().term);
        if(local){
            TextView remove=a.themedButton("删除课程",()->{sheet.dialog.dismiss();confirmDelete(a,c);},false);
            TextView edit=a.themedButton("编辑课程",()->{sheet.dialog.dismiss();a.editCustom(c);},true);
            sheet.footer.addView(remove,new LinearLayout.LayoutParams(0,a.dp(48),1));LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,a.dp(48),1);right.leftMargin=a.dp(12);sheet.footer.addView(edit,right);
        }else sheet.footer.addView(a.themedButton("知道了",()->sheet.dialog.dismiss(),true),new LinearLayout.LayoutParams(-1,a.dp(48)));
        a.showSheet(sheet);
    }
    static void info(MainActivity a,UiSheet sheet,String name,String value){
        LinearLayout row=a.row();row.setGravity(Gravity.TOP);row.setPadding(0,a.dp(8),0,a.dp(8));TextView label=a.label(name,12,a.MUTED,false);row.addView(label,new LinearLayout.LayoutParams(a.dp(76),-2));TextView text=a.label(value,14,a.INK,false);text.setLineSpacing(a.dp(3),1);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));sheet.body.addView(row);
    }
    static void confirmDelete(MainActivity a,Course c){
        UiSheet sheet=new UiSheet(a,"删除这门课程？","仅删除这条自定义安排",.44f);
        TextView title=a.label(c.name,19,a.INK,true);title.setPadding(a.dp(16),a.dp(15),a.dp(16),a.dp(15));title.setBackground(a.shape(a.palette.entrySurface,17));sheet.body.addView(title);a.space(sheet.body,14);
        TextView note=a.label("删除后无法撤销，其他课程不受影响。",13,a.MUTED,false);note.setLineSpacing(a.dp(4),1);sheet.body.addView(note);
        sheet.actions(a,"确认删除",()->{List<Course> remaining=new ArrayList<>(a.localCourses);remaining.remove(c);if(a.saveCustom(remaining)){sheet.dialog.dismiss();Toast.makeText(a,"已删除自定义课程",Toast.LENGTH_SHORT).show();}});a.showSheet(sheet);
    }
}
