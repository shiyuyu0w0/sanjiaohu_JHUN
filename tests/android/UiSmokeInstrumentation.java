package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Runs real click/lifecycle/animation paths against an installed APK on a test device. */
public final class UiSmokeInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private Throwable mainFailure;
    private FeedbackRoot feedback;

    private final class FeedbackRoot extends android.widget.FrameLayout {
        int requests;
        FeedbackRoot(){super(activity);}
        @Override public boolean performHapticFeedback(int effect){requests++;return super.performHapticFeedback(effect);}
    }

    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            onMain(()->{
                android.widget.FrameLayout original=activity.root;
                feedback=new FeedbackRoot();feedback.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());
                activity.root=feedback;activity.setContentView(feedback);activity.render();
            });settle();
            checkBars(activity);
            screenshot("home");
            nav("课程",1);nav("个人",2);nav("首页",0);
            openActivity("成绩查看",LoginActivity.class);assertPage(3);click("返回首页");settle();assertPage(0);
            openActivity("考试查询",LoginActivity.class);assertPage(4);click("返回首页");settle();assertPage(0);
            click("自定义课程");settle();assertSheet();dismissSheet();
            openActivity("校历",AcademicCalendarActivity.class);
            openActivity("校园地图",CampusMapActivity.class);
            openActivity("图书查询",LibraryActivity.class);
            openActivity("大物实验报告",PhysicsLabActivity.class);
            openActivity("网上报修",IdentityActivity.class);
            openActivity("电费",IdentityActivity.class);
            nav("个人",2);openActivity("关于",AboutActivity.class);
            Activity crop=startActivitySync(new Intent(getTargetContext(),WallpaperCropActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();checkBars(crop);screenshot("WallpaperCropActivity");onMain(crop::finish);settle();
            nav("课程",1);click("更多选项");settle();
            onMain(()->{check(activity.moreMenu!=null&&activity.moreMenu.isShowing(),"overflow menu");activity.moreMenu.dismiss();});
            onMain(()->new CalibrationSheet(activity));settle();assertSheet();dismissSheet();
            onMain(()->new ThemeColorSheet(activity));settle();assertSheet();dismissSheet();
            onMain(()->{
                activity.switchPage(2);
                check(Ui.animated()?activity.pageAnimator!=null&&activity.outgoing!=null:activity.pageAnimator==null,"page motion respects system animation setting");
            });settle();
            // Refresh while outgoing/incoming trees coexist; exercise cancellation cleanup.
            onMain(()->{activity.switchPage(2);activity.render();});settle();assertPage(2);
            onMain(()->{activity.switchPage(0);activity.switchPage(1);activity.switchPage(2);});settle();assertPage(2);
            onMain(()->{
                check(activity.outgoing==null,"outgoing page removed");
                check(activity.pageAnimator==null,"page animator cleared");
                check(activity.pageContent.getAlpha()==1,"incoming page visible");
                check(activity.pageContent.getTranslationX()==0,"incoming page settled");
                check(activity.pageContent.getClipChildren(),"scroll content cannot overlap status and navigation");
            });
            populatedPages();
            weekMotion();
            wallpaperAndFeedback();
            result.putString("stream","PASS: "+checks+" real Android UI smoke checks\n");finish(-1,result);
        }catch(Throwable failure){
            result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(failure));finish(0,result);
        }
    }
    private void onMain(Runnable work){
        mainFailure=null;
        runOnMainSync(()->{try{work.run();}catch(Throwable e){mainFailure=e;}});
        if(mainFailure!=null)throw new AssertionError("UI thread failure",mainFailure);
    }
    private void populatedPages(){
        onMain(()->{
            try{
                org.json.JSONObject metric=new org.json.JSONObject().put("name","总计").put("earned","24").put("average","87.5").put("gpa","3.7").put("weighted","88.2");
                org.json.JSONObject entry=new org.json.JSONObject().put("name","高等数学与应用基础（测试课程）").put("credits","4").put("hours","64")
                        .put("category","公共基础").put("nature","必修").put("assessment","考试").put("method","笔试").put("score","90").put("point","4.0").put("note","");
                activity.grades=new Grades(new org.json.JSONObject().put("version",2).put("complete",true).put("mode","effective").put("term","2026-2027学年第一学期")
                        .put("entries",new org.json.JSONArray().put(entry)).put("summary",new org.json.JSONObject().put("groups",new org.json.JSONArray().put(metric)).put("total",metric)));
                activity.switchPage(3);
            }catch(Exception e){throw new AssertionError(e);}
        });settle();screenshot("grades-populated");
        onMain(()->{
            check(find(activity.screen,"90")!=null,"original grade row shows score");
            try{
                activity.exams=new Exams(new org.json.JSONObject().put("version",1).put("complete",true).put("term","2026-2027学年第一学期")
                        .put("entries",new org.json.JSONArray().put(new org.json.JSONObject().put("name","测试考试").put("time","2026-11-12 13:40-15:40").put("room","教学楼 A101").put("seat","12"))));
                activity.switchPage(4);
            }catch(Exception e){throw new AssertionError(e);}
        });settle();screenshot("exams-populated");
    }
    private void settle(){waitForIdleSync();SystemClock.sleep(600);waitForIdleSync();}
    private void weekMotion(){
        final String[] original=new String[1];
        onMain(()->{original[0]=activity.prefs.getString(activity.anchorKey(),null);activity.prefs.edit().putString(activity.anchorKey(),activity.today().toString()).commit();activity.switchPage(1);});settle();
        onMain(()->{activity.selectedWeek=1;activity.render();});settle();
        onMain(()->{activity.changeWeek(2);check(Ui.animated()?activity.weekAnimator!=null:activity.weekAnimator==null,"week motion respects system animation setting");});settle();
        onMain(()->{
            check(activity.selectedWeek==2,"week navigation commits");check(activity.weekHost.getChildCount()==1,"week transition removes outgoing view");
            check(activity.weekHost.getChildAt(0).getTranslationX()==0,"week settles in position");
            if(original[0]==null)activity.prefs.edit().remove(activity.anchorKey()).commit();else activity.prefs.edit().putString(activity.anchorKey(),original[0]).commit();
        });
    }
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void wallpaperAndFeedback(){
        final String anchorKey=activity.anchorKey();
        final String savedAnchor=activity.prefs.getString(anchorKey,null);
        final boolean hadCrop=activity.prefs.contains("wallpaperUserCrop"),savedCrop=activity.prefs.getBoolean("wallpaperUserCrop",false);
        final android.graphics.Bitmap savedWallpaper=activity.wallpaper;
        final int[] fullSize=new int[2];
        try{
            // Every ordered pair of bottom tabs must produce exactly one request.
            for(int from=0;from<3;from++)for(int to=0;to<3;to++){
                final int source=from,target=to;
                onMain(()->activity.switchPage(source));settle();
                onMain(()->{
                    int before=feedback.requests;
                    android.view.ViewGroup nav=(android.view.ViewGroup)activity.screen.getChildAt(activity.screen.getChildCount()-1);
                    nav.getChildAt(target).performClick();
                    check(feedback.requests==before+(source==target?0:1),"one feedback for tab "+source+" -> "+target);
                });settle();
            }
            onMain(()->{
                int before=feedback.requests;feedback.setHapticFeedbackEnabled(false);activity.switchPage(0);
                check(feedback.requests==before,"view touch feedback setting respected");feedback.setHapticFeedbackEnabled(true);
                activity.prefs.edit().putString(anchorKey,activity.today().toString()).putBoolean("wallpaperUserCrop",true).commit();
                activity.switchPage(1);activity.selectedWeek=1;activity.render();
            });settle();
            onMain(()->{
                fullSize[0]=activity.weekHost.getWidth();fullSize[1]=activity.weekHost.getHeight();
                activity.wallpaper=android.graphics.Bitmap.createBitmap(fullSize[0],fullSize[1],android.graphics.Bitmap.Config.ARGB_8888);
                activity.wallpaper.eraseColor(android.graphics.Color.CYAN);activity.render();
            });settle();
            onMain(()->{
                assertWallpaperEdge();
                int before=feedback.requests;activity.changeWeek(2);check(feedback.requests==before+1,"committed week has one feedback");
            });settle();
            onMain(()->{
                int before=feedback.requests;activity.changeWeek(2);check(feedback.requests==before,"same week is silent");
                activity.prepareWeek(3);activity.settleWeek(false);check(feedback.requests==before,"cancelled swipe is silent");
            });settle();
            onMain(()->{
                int before=feedback.requests;activity.detail(Course.parse("测试课程 教师(1-16 教室)",1,1,20));
                check(feedback.requests==before+1,"course detail has one feedback");activity.activeSheet.dismiss();
                activity.prefs.edit().remove(anchorKey).commit();activity.render();
            });settle();
            onMain(()->{
                check(activity.weekHost.getHeight()<fullSize[1],"calibration notice reduces viewport height");assertWallpaperEdge();
                int before=feedback.requests;activity.changeWeek(3);check(feedback.requests==before,"uncalibrated week is silent");
            });screenshot("wallpaper-uncalibrated");
        }finally{
            onMain(()->{
                android.content.SharedPreferences.Editor edit=activity.prefs.edit();
                if(savedAnchor==null)edit.remove(anchorKey);else edit.putString(anchorKey,savedAnchor);
                if(hadCrop)edit.putBoolean("wallpaperUserCrop",savedCrop);else edit.remove("wallpaperUserCrop");edit.commit();
                android.graphics.Bitmap fixture=activity.wallpaper;activity.wallpaper=savedWallpaper;activity.render();
                if(fixture!=null&&fixture!=savedWallpaper)fixture.recycle();
            });
        }
    }
    private void assertWallpaperEdge(){
        View grid=activity.weekHost.getChildAt(0);int w=grid.getWidth(),h=grid.getHeight();
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
        try{
            grid.getBackground().draw(new android.graphics.Canvas(image));
            int expected=image.getPixel(w/2,h/2);
            for(int y=activity.dp(32);y<h;y++)check(image.getPixel(w-1,y)==expected,"wallpaper covers right edge");
            check(expected!=activity.palette.gridSurface,"test distinguishes image from blank surface");
        }finally{image.recycle();}
    }
    private void assertPage(int expected){onMain(()->check(activity.page==expected,"page "+expected));}
    private void nav(String label,int page){click(label);settle();assertPage(page);screenshot("page-"+page);}
    private void assertSheet(){onMain(()->check(activity.activeSheet!=null&&activity.activeSheet.isShowing(),"sheet visible"));}
    private void dismissSheet(){onMain(()->activity.activeSheet.dismiss());settle();}
    private void openActivity(String label,Class<? extends Activity> target){
        ActivityMonitor monitor=addMonitor(target.getName(),null,false);
        click(label);
        Activity opened=waitForMonitorWithTimeout(monitor,5000);
        check(opened!=null,"opened "+target.getSimpleName());
        settle();checkBars(opened);screenshot(target.getSimpleName());onMain(opened::finish);settle();removeMonitor(monitor);
    }
    private void checkBars(Activity target){
        onMain(()->{
            check(target.getWindow().peekDecorView()!=null,"decor initialized: "+target.getClass().getSimpleName());
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.view.WindowInsetsController controller=target.getWindow().getInsetsController();
                check(controller!=null,"system bars controller available");
                int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                check((controller.getSystemBarsAppearance()&mask)==(AppTheme.isDark(target)?0:mask),"system bar contrast: "+target.getClass().getSimpleName());
            }
        });
    }
    private void screenshot(String name){
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"screenshot "+name);
        java.io.File base=getTargetContext().getExternalFilesDir(null);
        check(base!=null,"external screenshot directory available");
        java.io.File directory=new java.io.File(base,"ui-smoke");
        check(directory.isDirectory()||directory.mkdirs(),"create screenshot directory");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"save screenshot");
        }catch(java.io.IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
    private void click(String label){
        final float[] point=new float[2];
        onMain(()->{
            View view=find(activity.screen,label);
            check(view!=null,"button exists: "+label);
            while(view!=null&&!view.isClickable())view=view.getParent() instanceof View?(View)view.getParent():null;
            check(view!=null&&view.isShown(),"clickable: "+label);
            view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);
            int[] xy=new int[2];view.getLocationOnScreen(xy);
            point[0]=xy[0]+view.getWidth()/2f;point[1]=xy[1]+view.getHeight()/2f;
        });
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);
        MotionEvent up=MotionEvent.obtain(now,now+70,MotionEvent.ACTION_UP,point[0],point[1],0);
        try{sendPointerSync(down);SystemClock.sleep(70);sendPointerSync(up);}finally{down.recycle();up.recycle();}
    }
    private View find(View view,String label){
        if(view.getVisibility()!=View.VISIBLE)return null;
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=find(group.getChildAt(i),label);if(child!=null)return child;}}
        return null;
    }
}
