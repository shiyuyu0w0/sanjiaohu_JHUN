package cn.jhun.sanjiaohu;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Compact, themed course editor using the same sheet and controls as appearance settings. */
final class CourseEditor {
    final MainActivity a;
    final Course existing;
    final UiSheet sheet;
    final EditText name,room,teacher,weeks;
    final List<String> terms=new ArrayList<>();
    int day,start,end,termIndex;
    final TextView[] periodValue=new TextView[2];
    final TextView[] periodTime=new TextView[2];
    CourseEditor(MainActivity activity,Course course){
        a=activity;existing=course;day=course==null?a.today().getDayOfWeek().getValue():course.day;start=course==null?1:course.start;end=course==null?2:course.end;
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
    void section(LinearLayout parent,String text){TextView heading=a.label(text,13,a.INK,true);heading.setPadding(0,0,0,a.dp(9));parent.addView(heading);}
    EditText field(LinearLayout parent,String title,String hint,String value){
        TextView heading=a.label(title,12,a.MUTED,false);heading.setPadding(a.dp(2),0,0,a.dp(6));parent.addView(heading);EditText input=new EditText(a);input.setTextSize(15);input.setTextColor(a.INK);input.setHintTextColor(a.MUTED);input.setSingleLine(true);input.setSelectAllOnFocus(false);input.setPadding(a.dp(13),0,a.dp(13),0);input.setText(value);input.setHint(hint);input.setBackground(fieldBackground(false));input.setOnFocusChangeListener((v,focus)->input.setBackground(fieldBackground(focus)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,a.dp(46));lp.bottomMargin=a.dp(13);parent.addView(input,lp);return input;
    }
    GradientDrawable fieldBackground(boolean focused){GradientDrawable d=a.shape(focused?a.palette.focusSurface:a.palette.controlSurface,13);d.setStroke(a.dp(1),focused?ThemePalette.mix(a.PRIMARY,a.INK,.2):a.palette.outline);return d;}
    TextView chip(String text){TextView t=a.label(text,14,a.INK,true);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);t.setFocusable(true);t.setContentDescription(text);return t;}
    void select(TextView t,boolean selected){t.setSelected(selected);t.setBackground(a.shape(selected?a.PRIMARY:a.palette.controlSurface,12));t.setTextColor(selected?a.ON_PRIMARY:a.INK);}
    View stepper(int index,String title){
        LinearLayout box=a.column();box.setPadding(a.dp(7),a.dp(9),a.dp(7),a.dp(8));box.setBackground(fieldBackground(false));TextView caption=a.label(title,11,a.MUTED,false);caption.setGravity(Gravity.CENTER);box.addView(caption);LinearLayout controls=a.row();TextView minus=chip("−"),plus=chip("＋"),value=chip("");minus.setContentDescription(title+"节次减一");plus.setContentDescription(title+"节次加一");minus.setTextSize(21);plus.setTextSize(19);controls.addView(minus,new LinearLayout.LayoutParams(a.dp(34),a.dp(38)));controls.addView(value,new LinearLayout.LayoutParams(0,a.dp(38),1));controls.addView(plus,new LinearLayout.LayoutParams(a.dp(34),a.dp(38)));periodValue[index]=value;minus.setOnClickListener(v->changePeriod(index,-1));plus.setOnClickListener(v->changePeriod(index,1));box.addView(controls);periodTime[index]=a.label("",10,a.MUTED,false);periodTime[index].setGravity(Gravity.CENTER);box.addView(periodTime[index]);return box;
    }
    void changePeriod(int index,int delta){if(index==0){start=Math.max(1,Math.min(12,start+delta));end=Math.max(start,end);}else{end=Math.max(1,Math.min(12,end+delta));start=Math.min(start,end);}updatePeriods();}
    void updatePeriods(){periodValue[0].setText(start+" 节");periodValue[1].setText(end+" 节");periodTime[0].setText(a.activeSchedule().starts[start]);periodTime[1].setText(a.activeSchedule().ends[end]);}
}
