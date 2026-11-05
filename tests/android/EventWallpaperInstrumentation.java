package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

/** Long events must preserve the grid cells and the continuous wallpaper behind them. */
public final class EventWallpaperInstrumentation extends Instrumentation {
    MainActivity a;int checks;Throwable failure;String originalAnchor;boolean hadAnchor;
    static final String TERM="背景回归学期",KEY="monday:"+TERM;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=-1;
        try{
            a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            originalAnchor=a.prefs.getString(KEY,null);hadAnchor=a.prefs.contains(KEY);
            runOnMainSync(()->{try{exercise();}catch(Throwable error){failure=error;}});if(failure!=null)throw failure;
            result.putString("stream","PASS: "+checks+" composed wallpaper continuity and bounded long-event layouts\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));code=0;}
        finally{if(a!=null)runOnMainSync(()->{if(hadAnchor)a.prefs.edit().putString(KEY,originalAnchor).commit();else a.prefs.edit().remove(KEY).commit();a.finish();});}
        finish(code,result);
    }
    void exercise()throws Exception{
        Bitmap saved=a.wallpaper,photo=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);
        for(int y=0;y<640;y++)for(int x=0;x<360;x++)photo.setPixel(x,y,Color.rgb(20+x/3,35+y/6,160+(x+y)%80));
        try{
            a.wallpaper=photo;a.schedule=Schedule.local(TERM);a.selectedTerm=TERM;a.localCourses=new ArrayList<>();a.selectedWeek=1;
            LocalDate monday=a.today().minusDays(a.today().getDayOfWeek().getValue()-1);
            for(boolean dark:new boolean[]{false,true})for(boolean calibrated:new boolean[]{false,true}){
                if(calibrated)a.prefs.edit().putString(KEY,monday.toString()).commit();else a.prefs.edit().remove(KEY).commit();
                a.palette=new ThemePalette(a.PRIMARY,dark);a.BG=a.palette.surface;a.INK=a.palette.text;a.MUTED=a.palette.muted;a.ACCENT_TEXT=a.palette.accent;
                for(int[] interval:new int[][]{{500,1300},{620,665},{635,852}})for(int[] size:new int[][]{{360,520},{720,320}}){
                    a.events=Collections.singletonList(ScheduleEvent.create("background-fixture",TERM,monday.plusDays(1),interval[0],interval[1],"长日程",""));a.visibleCards.clear();
                    WeekGridView grid=(WeekGridView)a.weekView();int w=size[0],h=size[1];grid.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));grid.layout(0,0,w,h);
                    Bitmap actual=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888),background=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    try{
                        grid.draw(new Canvas(actual));grid.getBackground().draw(new Canvas(background));
                        if(!calibrated&&interval[0]==500&&w==360)try(FileOutputStream out=new FileOutputStream(new File(a.getExternalFilesDir(null),dark?"wallpaper-long-dark.png":"wallpaper-long-light.png"))){actual.compress(Bitmap.CompressFormat.PNG,100,out);}
                        GridGeometry g=new GridGeometry(w,h,a.dp(33),a.dp(32),a.dp(14));
                        for(int p=1;p<=12;p++)if(!Arrays.equals(grid.timeline.bounds(g,WeekGridView.CELL,7,p,p,0,1),g.bounds(WeekGridView.CELL,7,p,p,0,1)))throw new AssertionError("event resized an existing cell");checks++;
                        // Sample empty columns away from the event and the centered meal text.
                        for(int day:new int[]{6,7}){int x=(g.x(day-1)+g.x(day))/2;
                            for(int y=g.header+1;y<h-1;y++){
                                int before=background.getPixel(x,y),after=actual.getPixel(x,y);
                                if(Math.abs(Color.red(before)-Color.red(after))>24||Math.abs(Color.green(before)-Color.green(after))>24||Math.abs(Color.blue(before)-Color.blue(after))>24)
                                    throw new AssertionError("wallpaper obscured by row at "+x+","+y+" interval "+Arrays.toString(interval)+" dark="+dark+" calibrated="+calibrated);
                            }
                        }checks++;
                        boolean found=false;for(int i=0;i<grid.getChildCount();i++){View child=grid.getChildAt(i);if(child instanceof CourseCardView){found=true;if(child.getHeight()<=0||child.getTop()<g.header||child.getBottom()>h)throw new AssertionError("long event outside grid");}}
                        if(!found)throw new AssertionError("long event missing");checks++;
                    }finally{actual.recycle();background.recycle();}
                }
            }
        }finally{a.wallpaper=saved;photo.recycle();}
    }
}
