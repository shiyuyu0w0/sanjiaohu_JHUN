package cn.jhun.sanjiaohu;

import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared fields and period controls for courses and dated events. */
class ScheduleEditorForm {
    final MainActivity a;
    int start,end;
    final TextView[] periodValue=new TextView[2];
    final TextView[] periodTime=new TextView[2];
    ScheduleEditorForm(MainActivity activity){a=activity;}
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
