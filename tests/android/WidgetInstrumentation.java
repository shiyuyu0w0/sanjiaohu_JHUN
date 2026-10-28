package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

/** Real RemoteViews inflation, launcher binding, per-instance images and responsive text. */
public final class WidgetInstrumentation extends Instrumentation {
    int checks;AppWidgetHost host;int first,second;
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=-1;
        try{
            final Throwable[] failure={null};runOnMainSync(()->{try{exercise();}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw failure[0];
            navigation();
            result.putString("stream","PASS: "+checks+" widget binding, image isolation and Android layout checks\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));code=0;}
        finally{if(host!=null)host.deleteHost();if(first>0)WidgetPhotoStore.delete(getTargetContext(),first);if(second>0)WidgetPhotoStore.delete(getTargetContext(),second);TodayWidgetProvider.updateAll(getTargetContext());}
        finish(code,result);
    }
    void exercise()throws Exception{
        Context c=getTargetContext();AppWidgetManager manager=AppWidgetManager.getInstance(c);host=new AppWidgetHost(c,7091);for(int id:host.getAppWidgetIds())WidgetPhotoStore.delete(c,id);host.deleteHost();
        first=host.allocateAppWidgetId();second=host.allocateAppWidgetId();
        check(manager.bindAppWidgetIdIfAllowed(first,TodayWidgetProvider.component(c)),"first host binding permission");
        check(manager.bindAppWidgetIdIfAllowed(second,TodayWidgetProvider.component(c)),"second host binding");
        AppWidgetProviderInfo info=manager.getAppWidgetInfo(first);
        check(info.targetCellWidth==4&&info.targetCellHeight==2,"default is 4 by 2");
        check(info.resizeMode==AppWidgetProviderInfo.RESIZE_HORIZONTAL,"no vertical resizing");
        check(info.configure.getClassName().equals(WidgetConfigActivity.class.getName()),"launcher config activity");
        check(TodayWidgetProvider.owns(c,first)&&!TodayWidgetProvider.owns(c,987654321),"reject stale widget ids");
        String token=UUID.randomUUID().toString();Bitmap red=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888),blue=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888);red.eraseColor(0xffee6655);blue.eraseColor(0xff5599ee);
        try{
            WidgetPhotoStore.save(WidgetPhotoStore.draft(c,token),red);WidgetPhotoStore.commit(c,first,token,false);
            WidgetPhotoStore.save(WidgetPhotoStore.draft(c,token),blue);WidgetPhotoStore.commit(c,second,token,false);
            Bitmap a=WidgetPhotoStore.load(WidgetPhotoStore.photo(c,first)),b=WidgetPhotoStore.load(WidgetPhotoStore.photo(c,second));
            check(a.getPixel(100,100)==0xffee6655&&b.getPixel(100,100)==0xff5599ee,"each widget owns a photo; uncommitted draft leaves old photo");a.recycle();b.recycle();
            byte[] wallpaper=WallpaperStore.file(c).exists()?Files.readAllBytes(WallpaperStore.file(c).toPath()):null;
            WidgetPhotoStore.commit(c,first,token,true);check(!WidgetPhotoStore.photo(c,first).exists()&&WidgetPhotoStore.photo(c,second).exists(),"reset only its own image");
            check(wallpaper==null?!WallpaperStore.file(c).exists():Arrays.equals(wallpaper,Files.readAllBytes(WallpaperStore.file(c).toPath())),"widget images cannot change timetable wallpaper");
            try{WidgetPhotoStore.draft(c,"../wallpaper.png");throw new AssertionError("unsafe path");}catch(IllegalArgumentException expected){checks++;}
        }finally{red.recycle();blue.recycle();new android.util.AtomicFile(WidgetPhotoStore.draft(c,token)).delete();}
        TodayWidgetProvider.Snapshot snapshot=new TodayWidgetProvider.Snapshot();snapshot.schedule=Schedule.local("测试学期");snapshot.hasData=true;snapshot.anchor=LocalDate.of(2026,10,19);snapshot.now=LocalDateTime.of(2026,10,28,10,40).atZone(WidgetSchedule.ZONE);
        snapshot.schedule.courses.add(CustomCourses.create(null,"测试学期","毛泽东思想和中国特色社会主义理论体系概论","","J02A212",3,3,4,"1-20"));
        snapshot.schedule.courses.add(CustomCourses.create(null,"测试学期","JAVA 编程技术","","J04B307",3,5,6,"1-20"));
        snapshot.local.add(CustomCourses.create(null,"*","自习","","图书馆",3,7,8,"1-20"));snapshot.local.add(CustomCourses.create(null,"*","大学英语","","J05A101",3,9,10,"1-20"));
        for(float font:new float[]{1f,1.3f,1.6f})for(int[] size:new int[][]{{250,110},{300,160},{360,180},{400,210}}){
            Configuration config=new Configuration(c.getResources().getConfiguration());config.fontScale=font;Context sized=c.createConfigurationContext(config);snapshot.fontScale=font;
            WidgetSchedule data=snapshot.model(size[1]);RemoteViews remote=TodayWidgetProvider.views(sized,first,snapshot,data,null);
            FrameLayout frame=new FrameLayout(sized);View content=remote.apply(sized,frame);frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
            int width=Ui.dp(sized,size[0]),height=Ui.dp(sized,size[1]);frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));frame.layout(0,0,width,height);
            check(content.getWidth()==width&&content.getHeight()==height,"root fills host");
            LinearLayout rows=content.findViewById(R.id.widget_courses);check(rows.getChildCount()==data.visible.size(),"all selected rows inflated");
            for(int i=0;i<rows.getChildCount();i++){
                View row=rows.getChildAt(i);TextView name=row.findViewById(R.id.widget_course_name),time=row.findViewById(R.id.widget_course_time);
                check(row.getHeight()>0&&row.getBottom()<=rows.getHeight(),"row inside allocated height");
                check(name.getHeight()+time.getHeight()+Ui.dp(sized,4)<=row.getHeight(),"course text fits: "+Arrays.toString(size)+" font "+font);
                check(time.getLayout().getLineCount()==1&&name.getLayout().getLineCount()==1,"long titles truncate to one line");
            }
            View left=content.findViewById(R.id.widget_picture_area),right=content.findViewById(R.id.widget_schedule_area);
            check(left.hasOnClickListeners()&&right.hasOnClickListeners(),"both areas have click actions");
            if(font==1f&&size[0]==360){Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);frame.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"widget-preview.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
            manager.updateAppWidget(second,remote); // Exercise the actual system parcel/bitmap memory budget.
        }
        themeChange(snapshot);TodayWidgetProvider.updateAll(c);
    }
    void themeChange(TodayWidgetProvider.Snapshot snapshot)throws Exception{
        Context c=getTargetContext();snapshot.fontScale=1;RemoteViews cached=TodayWidgetProvider.views(c,first,snapshot,snapshot.model(180),null);
        int lightBackground=0;
        // Reuse the same parcel: the launcher must resolve night resources without a new provider update.
        for(boolean dark:new boolean[]{false,true,false}){
            Configuration configuration=new Configuration(c.getResources().getConfiguration());configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);configuration.fontScale=1;
            Context mode=c.createConfigurationContext(configuration);FrameLayout frame=new FrameLayout(mode);View view=cached.apply(mode,frame);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));int w=Ui.dp(mode,360),h=Ui.dp(mode,180);frame.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));frame.layout(0,0,w,h);
            View card=view.findViewById(android.R.id.background);int background=color(card.getBackground());
            check(dark?ThemePalette.luminance(background)<.1:ThemePalette.luminance(background)>.9,"cached widget follows system night mode");
            if(!dark){if(lightBackground!=0)check(lightBackground==background,"switching back restores light colors");lightBackground=background;}
            TextView heading=view.findViewById(R.id.widget_heading);check(ThemePalette.contrast(heading.getCurrentTextColor(),background)>=4.5,"heading contrast in both modes");
            LinearLayout rows=view.findViewById(R.id.widget_courses);
            for(int i=0;i<rows.getChildCount();i++){
                View row=rows.getChildAt(i);int fill=color(row.getBackground());TextView title=row.findViewById(R.id.widget_course_name),time=row.findViewById(R.id.widget_course_time);
                check(ThemePalette.contrast(title.getCurrentTextColor(),fill)>=4.5&&ThemePalette.contrast(time.getCurrentTextColor(),fill)>=4.5,"course text contrast in both modes");
                check(color(row.findViewById(R.id.widget_course_accent).getBackground())==mode.getColor(i==0?R.color.widget_accent:R.color.widget_pending),"dynamic course stripe resolves the host theme");
            }
            check(!containsText(view,"换张图片"),"no picture caption on the widget");
            Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);frame.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),dark?"widget-preview-dark.png":"widget-preview.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        }
    }
    int color(android.graphics.drawable.Drawable drawable){Bitmap b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);android.graphics.Rect old=new android.graphics.Rect(drawable.getBounds());drawable.setBounds(0,0,40,40);drawable.draw(new Canvas(b));int color=b.getPixel(20,20);drawable.setBounds(old);b.recycle();return color;}
    boolean containsText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(containsText(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    void navigation(){
        Context c=getTargetContext();MainActivity main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(TodayWidgetProvider.OPEN,true));
        waitForIdleSync();check(main.page==1,"cold widget launch opens timetable");
        runOnMainSync(()->{main.page=2;main.selectedWeek=20;main.render();View view=TodayWidgetProvider.preview(c,first,null).apply(c,new FrameLayout(c));view.findViewById(R.id.widget_schedule_area).performClick();});
        waitForIdleSync();runOnMainSync(()->check(main.page==1&&main.selectedWeek==main.currentWeek(),"warm widget tap returns from profile to actual week"));
        ActivityMonitor monitor=addMonitor(WidgetConfigActivity.class.getName(),null,false);
        runOnMainSync(()->{View view=TodayWidgetProvider.preview(c,first,null).apply(c,new FrameLayout(c));view.findViewById(R.id.widget_picture_area).performClick();});
        android.app.Activity config=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(config instanceof WidgetConfigActivity,"picture tap opens config");
        runOnMainSync(()->{check(((WidgetConfigActivity)config).widgetId==first,"config addresses the tapped widget");config.finish();main.finish();});waitForIdleSync();
    }
}
