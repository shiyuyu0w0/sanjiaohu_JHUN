package cn.jhun.sanjiaohu;

import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Compact, themed course editor using the same sheet and controls as appearance settings. */
final class CourseEditor extends ScheduleEditorForm {
    final Course existing;
    final UiSheet sheet;
    final EditText name,room,teacher,weeks;
    final List<String> terms=new ArrayList<>();
    int day,termIndex;
    CourseEditor(MainActivity activity,Course course){
        super(activity);existing=course;day=course==null?a.today().getDayOfWeek().getValue():course.day;start=course==null?1:course.start;end=course==null?2:course.end;
        sheet=new UiSheet(a,course==null?"添加课程":"编辑课程","自定义安排 · 独立保存",.9f);LinearLayout form=sheet.body;
        name=field(form,"课程名称","例如：大学英语",course==null?"":course.name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        LinearLayout info=a.row(),left=a.column(),right=a.column();info.addView(left,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.leftMargin=a.dp(12);info.addView(right,half);form.addView(info);
        room=field(left,"地点","选填",course==null?"":course.room);teacher=field(right,"教师","选填",course==null?"":course.teacher);
        section(form,"上课时间");
        LinearLayout days=a.row();TextView[] dayButtons=new TextView[7];for(int i=0;i<7;i++){final int value=i+1;TextView button=chip("一二三四五六日".substring(i,i+1));button.setContentDescription("周"+button.getText());dayButtons[i]=button;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(40),1);if(i>0)lp.leftMargin=a.dp(5);days.addView(button,lp);button.setOnClickListener(v->{day=value;for(int j=0;j<7;j++)select(dayButtons[j],day==j+1);});select(button,day==value);}form.addView(days);a.space(form,12);
        LinearLayout periods=a.row();periods.addView(stepper(0,"开始"),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,-2,1);second.leftMargin=a.dp(12);periods.addView(stepper(1,"结束"),second);form.addView(periods);a.space(form,14);updatePeriods();
        weeks=field(form,"教学周","例如：1-16 或 1,3,5",course==null?"1-16":course.weekText);
        LinearLayout presets=a.row();String[] modes={"全部周","单周","双周"};String[] suffix={"","(单)","(双)"};for(int i=0;i<3;i++){final int which=i;TextView quick=chip(modes[i]);select(quick,false);quick.setOnClickListener(v->{String value=weeks.getText().toString().trim().replaceAll("[（(][单双][)）]","").replace("单","").replace("双","");if(value.isEmpty())value="1-16";weeks.setText(value+suffix[which]);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(36),1);if(i>0)lp.leftMargin=a.dp(8);presets.addView(quick,lp);}form.addView(presets);a.space(form,16);
        terms.add("所有学期");terms.add(a.activeSchedule().term);if(course!=null&&!course.term.equals("*")&&!terms.contains(course.term))terms.add(course.term);termIndex=course==null?(a.schedule==null?0:1):course.term.equals("*")?0:terms.indexOf(course.term);
        section(form,"适用学期");TextView[] termButtons=new TextView[terms.size()];for(int i=0;i<terms.size();i++){final int which=i;TextView option=chip(terms.get(i));option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(a.dp(14),0,a.dp(14),0);option.setTextSize(13);termButtons[i]=option;select(option,termIndex==i);option.setOnClickListener(v->{termIndex=which;for(int j=0;j<termButtons.length;j++)select(termButtons[j],termIndex==j);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,a.dp(42));lp.bottomMargin=a.dp(6);form.addView(option,lp);}
        TextView error=a.label("",12,a.palette.error,false);error.setVisibility(View.GONE);form.addView(error,0);
        sheet.actions(a,"保存课程",()->{
            try{Course edited=CustomCourses.create(existing==null?null:existing.localId,termIndex==0?"*":terms.get(termIndex),name.getText().toString(),teacher.getText().toString(),room.getText().toString(),day,start,end,weeks.getText().toString());List<Course> result=new ArrayList<>(a.localCourses);if(existing!=null)result.remove(existing);result.add(edited);if(a.saveCustom(result)){sheet.dialog.dismiss();Toast.makeText(a,"课程已保存",Toast.LENGTH_SHORT).show();}}
            catch(IllegalArgumentException e){error.setText(e.getMessage());error.setPadding(0,0,0,a.dp(12));error.setVisibility(View.VISIBLE);((ScrollView)form.getParent()).smoothScrollTo(0,0);}
        });a.showSheet(sheet);
    }
}
