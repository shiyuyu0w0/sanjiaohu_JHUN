package cn.jhun.sanjiaohu;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Two independent, themed wheels. Selection stays local until confirmation. */
final class TimeWheelSheet {
    interface Selection {void select(int minute);}
    final UiSheet sheet;
    final TimeWheelView hours,minutes;
    TimeWheelSheet(MainActivity a,String title,int minute,Selection selection){
        this(a,title,minute,0,24*60-1,selection);
    }
    TimeWheelSheet(MainActivity a,String title,int minute,int earliest,int latest,Selection selection){
        minute=Math.max(earliest,Math.min(latest,minute));
        boolean landscape=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        sheet=new UiSheet(a,title,"可选时间 "+ScheduleEvent.time(earliest)+"–"+ScheduleEvent.time(latest),landscape?1f:.60f,false);
        LinearLayout labels=a.row();TextView hour=a.label("小时",12,a.MUTED,false),min=a.label("分钟",12,a.MUTED,false);
        hour.setGravity(Gravity.CENTER);min.setGravity(Gravity.CENTER);
        labels.addView(hour,new LinearLayout.LayoutParams(0,a.dp(28),1));labels.addView(new View(a),new LinearLayout.LayoutParams(a.dp(24),1));labels.addView(min,new LinearLayout.LayoutParams(0,a.dp(28),1));sheet.body.addView(labels);
        FrameLayout wheel=new FrameLayout(a){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            {setWillNotDraw(false);}
            @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float row=getHeight()/5f;paint.setColor(a.palette.selectedSurface);canvas.drawRoundRect(a.dp(8),row*2,getWidth()-a.dp(8),row*3,a.dp(14),a.dp(14),paint);}
        };
        wheel.setBackground(a.shape(a.palette.controlSurface,20));wheel.setClipToOutline(true);
        LinearLayout columns=a.row();hours=new TimeWheelView(a,24,"小时",minute/60);minutes=new TimeWheelView(a,60,"分钟",minute%60);
        hours.setRange(earliest/60,latest/60);
        hours.onSelection=()->minutes.setRange(hours.value()==earliest/60?earliest%60:0,hours.value()==latest/60?latest%60:59);
        hours.onSelection.run();
        columns.addView(hours,new LinearLayout.LayoutParams(0,-1,1));TextView colon=a.label(":",26,a.palette.deepAccent,true);colon.setGravity(Gravity.CENTER);colon.setIncludeFontPadding(false);colon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);columns.addView(colon,new LinearLayout.LayoutParams(a.dp(24),-1));columns.addView(minutes,new LinearLayout.LayoutParams(0,-1,1));
        wheel.addView(columns,new FrameLayout.LayoutParams(-1,-1));sheet.body.addView(wheel,new LinearLayout.LayoutParams(-1,0,1));
        sheet.actions(a,"确定",()->{int selected=hours.finishSelection()*60+minutes.finishSelection();sheet.dialog.dismiss();selection.select(selected);});
        sheet.dialog.setOnDismissListener(dialog->{hours.stop();minutes.stop();});
        // Keep the editor and its unsaved fields behind this secondary sheet.
        sheet.show(a);
    }
    boolean isShowing(){return sheet.dialog.isShowing();}
    void dismiss(){sheet.dialog.dismiss();}
}
