package cn.jhun.sanjiaohu;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class TodayWidgetProvider extends AppWidgetProvider {
    static final String TICK="cn.jhun.sanjiaohu.WIDGET_TICK",OPEN="openTodayFromWidget";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static ComponentName component(Context c){return new ComponentName(c,TodayWidgetProvider.class);}
    static int[] ids(Context c){return AppWidgetManager.getInstance(c).getAppWidgetIds(component(c));}
    static boolean owns(Context c,int id){AppWidgetProviderInfo info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id);return info!=null&&component(c).equals(info.provider);}
    public static void refresh(Context c){Context app=c.getApplicationContext();WORK.execute(()->updateAll(app));}
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        PendingResult pending=goAsync();Context app=c.getApplicationContext();
        WORK.execute(()->{try{updateAll(app);}finally{pending.finish();}});
    }
    // All broadcasts share the asynchronous update above, including options/date/time/reboot.
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){}
    @Override public void onDeleted(Context c,int[] ids){for(int id:ids)WidgetPhotoStore.delete(c,id);}
    static void updateAll(Context c){
        try{
            AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=ids(c);
            AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent tick=PendingIntent.getBroadcast(c,0,new Intent(c,TodayWidgetProvider.class).setAction(TICK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            if(ids.length==0){alarm.cancel(tick);return;}
            Snapshot snapshot=Snapshot.load(c);long next=Long.MAX_VALUE;
            for(int id:ids){
                int height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160);
                WidgetSchedule data=snapshot.model(height);next=Math.min(next,data.nextRefresh);
                try{manager.updateAppWidget(id,views(c,id,snapshot,data,null));}catch(RuntimeException e){android.util.Log.w("TodayWidget","Widget update failed",e);}
            }
            // Inexact, non-wakeup refresh at the next course boundary or midnight. No alarm permission.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC,Math.max(System.currentTimeMillis()+1000,next),tick);
        }catch(RuntimeException e){android.util.Log.w("TodayWidget","Unable to refresh desktop",e);}
    }
    static RemoteViews preview(Context c,int id,Bitmap photo){Snapshot s=Snapshot.load(c);return views(c,id,s,s.model(160),photo);}
    static RemoteViews views(Context c,int id,Snapshot snapshot,WidgetSchedule data,Bitmap override){
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.widget_today);
        LocalDate date=snapshot.now.toLocalDate();
        view.setTextViewText(R.id.widget_weekday,"周"+"一二三四五六日".charAt(date.getDayOfWeek().getValue()-1));
        view.setTextViewText(R.id.widget_date,date.getMonthValue()+"月"+date.getDayOfMonth()+"日");
        view.setTextViewText(R.id.widget_heading,data.heading);view.setTextViewText(R.id.widget_footer,data.footer);
        view.setViewVisibility(R.id.widget_footer,snapshot.height<140?View.GONE:View.VISIBLE);
        Bitmap photo=override!=null?override:id>0?WidgetPhotoStore.load(WidgetPhotoStore.photo(c,id)):null;
        if(photo!=null)view.setImageViewBitmap(R.id.widget_photo,photo);
        view.removeAllViews(R.id.widget_courses);
        view.setViewVisibility(R.id.widget_courses,data.visible.isEmpty()?View.GONE:View.VISIBLE);
        view.setViewVisibility(R.id.widget_empty,data.visible.isEmpty()?View.VISIBLE:View.GONE);
        view.setTextViewText(R.id.widget_empty_title,data.message);view.setTextViewText(R.id.widget_empty_detail,data.detail);
        StringBuilder accessibility=new StringBuilder(data.heading);
        for(Course course:data.visible){
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_course);
            long start=WidgetSchedule.boundary(date,snapshot.schedule.starts[course.start]),end=WidgetSchedule.boundary(date,snapshot.schedule.ends[course.end]);
            long now=snapshot.now.toInstant().toEpochMilli();boolean ongoing=now>=start&&now<end,done=now>=end;
            String time=snapshot.schedule.starts[course.start]+"–"+snapshot.schedule.ends[course.end]+(course.room.isEmpty()?"":"  "+course.room);
            row.setTextViewText(R.id.widget_course_name,course.name);row.setTextViewText(R.id.widget_course_time,time);
            // Keep resource IDs in the cached RemoteViews so the launcher can re-inflate in night mode
            // even when our app process is not running. Never freeze theme colors into this parcel.
            row.setInt(R.id.widget_course_accent,"setBackgroundResource",ongoing?R.drawable.widget_accent_ongoing:done?R.drawable.widget_accent_done:R.drawable.widget_accent_pending);
            row.setContentDescription(R.id.widget_course_row,course.name+"，"+time+(ongoing?"，正在上课":done?"，已结束":""));
            view.addView(R.id.widget_courses,row);accessibility.append("，").append(course.name).append("，").append(time);
        }
        if(data.visible.isEmpty())accessibility.append("，").append(data.message).append("，").append(data.detail);
        view.setContentDescription(R.id.widget_schedule_area,accessibility+"，点击打开课表");
        Intent open=new Intent(c,MainActivity.class).putExtra(OPEN,true).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        view.setOnClickPendingIntent(R.id.widget_schedule_area,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        Intent config=new Intent(c,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("sanjiaohu://widget/"+id));
        view.setOnClickPendingIntent(R.id.widget_picture_area,PendingIntent.getActivity(c,id,config,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        return view;
    }
    static final class Snapshot {
        Schedule schedule;List<Course> local=new ArrayList<>();LocalDate anchor;boolean hasData,error;float fontScale=1;int height=160;
        ZonedDateTime now=ZonedDateTime.now(WidgetSchedule.ZONE);
        static Snapshot load(Context c){
            Snapshot s=new Snapshot();s.fontScale=c.getResources().getConfiguration().fontScale;android.content.SharedPreferences prefs=c.getSharedPreferences("settings",Context.MODE_PRIVATE);
            String term=prefs.getString("selectedTerm","");s.schedule=Schedule.local(term.isEmpty()?"自定义学期":term);
            if(!term.isEmpty())try{s.schedule=new Schedule(new AcademicStore(c).load("schedule",term));s.hasData=true;}catch(Exception ignored){s.error=new java.io.File(c.getFilesDir(),"schedule-"+Term.key(term)+".json").exists();}
            try{s.local=new CustomCourseStore(c).load();for(Course course:s.local)if(course.term.equals("*")||course.term.equals(s.schedule.term))s.hasData=true;}catch(Exception e){s.error=true;}
            try{s.anchor=LocalDate.parse(prefs.getString("monday:"+s.schedule.term,""));}catch(Exception ignored){}
            return s;
        }
        WidgetSchedule model(int height){
            this.height=height;
            int slots=height>=300?4:height>=230?3:height<155?1:2;
            int fitting=Math.max(1,(int)((height-76)/(22*Math.max(1,fontScale)+15)));
            WidgetSchedule result=WidgetSchedule.create(schedule,local,anchor,now,hasData,Math.min(slots,fitting));
            if(error){result.visible.clear();result.message="课表暂时无法读取";result.detail="点击打开应用查看";}return result;
        }
    }
}
