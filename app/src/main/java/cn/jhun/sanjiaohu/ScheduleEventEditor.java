package cn.jhun.sanjiaohu;

import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared course form styling, with arbitrary clock times and a fixed date. */
final class ScheduleEventEditor extends ScheduleEditorForm {
    final UiSheet sheet;
    final ScheduleEvent existing;
    final LocalDate date;
    final String term;
    final EditText title,notes;
    final TextView duration,error;
    final TextView[] times=new TextView[2];
    TimeWheelSheet timePicker;

    ScheduleEventEditor(MainActivity activity,ScheduleEvent event,LocalDate selectedDate,int period){
        this(activity,event,selectedDate,ScheduleEvent.minute(activity.activeSchedule().starts[period]),ScheduleEvent.minute(activity.activeSchedule().ends[period]));
    }
    ScheduleEventEditor(MainActivity activity,ScheduleEvent event,LocalDate selectedDate,int from,int to){
        super(activity);existing=event;date=event==null?selectedDate:event.date;term=event==null?a.activeSchedule().term:event.term;
        start=event==null?from:event.start;end=event==null?to:event.end;
        sheet=new UiSheet(a,event==null?"添加日程":"编辑日程",dateLabel(date)+" · 仅当天",.8f);
        title=field(sheet.body,"标题","例如：小组讨论",event==null?"":event.title);
        title.setContentDescription("日程标题");title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        error=a.label("",12,a.palette.error,false);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);sheet.body.addView(error,0);
        section(sheet.body,"日程时间");
        LinearLayout periods=a.row();periods.addView(timeButton(0,"开始时间"),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.leftMargin=a.dp(12);periods.addView(timeButton(1,"结束时间"),right);sheet.body.addView(periods);
        duration=a.label("",12,a.MUTED,false);duration.setPadding(a.dp(2),a.dp(9),0,a.dp(18));sheet.body.addView(duration);updatePeriods();
        notes=field(sheet.body,"备注","选填",event==null?"":event.notes);
        notes.setContentDescription("日程备注");notes.setSingleLine(false);notes.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setGravity(Gravity.TOP|Gravity.START);notes.setMinLines(3);notes.setMaxLines(6);notes.setPadding(a.dp(13),a.dp(12),a.dp(13),a.dp(12));
        notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ScheduleEvent.MAX_NOTES)});notes.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
        title.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){error.setVisibility(View.GONE);}
            public void afterTextChanged(android.text.Editable text){}
        });
        sheet.actions(a,"保存日程",()->{
            try{
                ScheduleEvent edited=ScheduleEvent.create(existing==null?null:existing.id,term,date,start,end,title.getText().toString(),notes.getText().toString());
                Course conflict=edited.courseConflict(a.activeSchedule(),a.localCourses,a.anchor());
                if(conflict!=null)throw new IllegalArgumentException("与「"+conflict.name+"」课程时间冲突，请调整日程时间");
                List<ScheduleEvent> next=new ArrayList<>(a.events);if(existing!=null)next.removeIf(e->e.id.equals(existing.id));next.add(edited);
                if(a.saveEvents(next)){sheet.dialog.dismiss();Toast.makeText(a,"日程已保存",Toast.LENGTH_SHORT).show();}
            }catch(IllegalArgumentException e){error.setText(e.getMessage());error.setPadding(0,0,0,a.dp(12));error.setVisibility(View.VISIBLE);((ScrollView)sheet.body.getParent()).smoothScrollTo(0,0);}
        });a.showSheet(sheet);sheet.dialog.setOnDismissListener(dialog->{if(a.activeSheet==sheet.dialog)a.activeSheet=null;if(timePicker!=null)timePicker.dismiss();});
    }
    View timeButton(int index,String label){
        LinearLayout box=a.column();box.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));box.setBackground(fieldBackground(false));
        box.addView(a.label(label,12,a.MUTED,false));times[index]=a.label("",24,a.INK,true);times[index].setPadding(0,a.dp(6),0,0);box.addView(times[index]);
        box.setFocusable(true);box.setContentDescription(label);box.setOnClickListener(v->{
            Ui.tap(v);int value=index==0?start:end;
            timePicker=new TimeWheelSheet(a,label,value,ScheduleEvent.EARLIEST+(index==1?1:0),ScheduleEvent.LATEST-(index==0?1:0),minute->{if(index==0)start=minute;else end=minute;error.setVisibility(View.GONE);updatePeriods();});
        });return box;
    }
    @Override void updatePeriods(){
        times[0].setText(ScheduleEvent.time(start));times[1].setText(ScheduleEvent.time(end));
        if(duration!=null){try{ScheduleEvent.validateTime(start,end);duration.setText("时长 "+ScheduleEvent.duration(start,end)+" · 08:20–21:40 内");duration.setTextColor(a.MUTED);}catch(IllegalArgumentException e){duration.setText(e.getMessage());duration.setTextColor(a.palette.error);}}
    }
    static String dateLabel(LocalDate date){return date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))+" 周"+"一二三四五六日".charAt(date.getDayOfWeek().getValue()-1);}
    static void showDetail(MainActivity a,ScheduleEvent event){
        UiSheet sheet=new UiSheet(a,"日程详情",dateLabel(event.date)+" · 仅当天",.65f);
        TextView title=a.label(event.title,23,a.INK,true);title.setLineSpacing(a.dp(3),1);sheet.body.addView(title);a.space(sheet.body,16);
        LinearLayout time=a.column();time.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(14));time.setBackground(a.shape(a.palette.entrySurface,18));
        time.addView(a.label(event.interval(),20,a.palette.deepAccent,true));a.space(time,7);
        time.addView(a.label("时长 "+event.duration(),13,a.INK,false));sheet.body.addView(time);a.space(sheet.body,16);
        CourseDetailSheet.info(a,sheet,"备注",event.notes.isEmpty()?"未填写":event.notes);
        sheet.footer.addView(a.themedButton("删除日程",()->confirmDelete(a,event),false),new LinearLayout.LayoutParams(0,a.dp(48),1));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,a.dp(48),1);right.leftMargin=a.dp(12);
        sheet.footer.addView(a.themedButton("编辑日程",()->a.editEvent(event,event.date,1),true),right);a.showSheet(sheet);
    }
    static void confirmDelete(MainActivity a,ScheduleEvent event){
        UiSheet sheet=new UiSheet(a,"删除这条日程？",dateLabel(event.date),.44f);
        sheet.body.addView(a.label(event.title,19,a.INK,true));
        sheet.actions(a,"确认删除",()->{List<ScheduleEvent> next=new ArrayList<>(a.events);next.removeIf(e->e.id.equals(event.id));if(a.saveEvents(next)){sheet.dialog.dismiss();Toast.makeText(a,"日程已删除",Toast.LENGTH_SHORT).show();}});a.showSheet(sheet);
    }
}
