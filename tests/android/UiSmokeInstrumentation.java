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
    private boolean motionOnly;

    private final class FeedbackRoot extends android.widget.FrameLayout {
        int requests;
        FeedbackRoot(){super(activity);}
        @Override public boolean performHapticFeedback(int effect){requests++;return super.performHapticFeedback(effect);}
    }

    @Override public void onCreate(Bundle args){super.onCreate(args);motionOnly=args!=null&&"true".equals(args.getString("motion_only"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();
            if(motionOnly){
                onMain(()->{
                    android.widget.FrameLayout original=activity.root;
                    feedback=new FeedbackRoot();feedback.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());
                    activity.root=feedback;activity.setContentView(feedback);activity.switchPage(0);activity.render();
                });settle();
                pressFeedback();wallpaperAndFeedback();
                result.putString("stream","PASS: "+checks+" focused Android motion checks\n");finish(-1,result);return;
            }
            assertPage(1);
            onMain(()->check(activity.weekHost!=null,"cold launch opens timetable"));
            onMain(()->{
                ViewGroup grid=(ViewGroup)activity.weekHost.getChildAt(0);
                for(int i=0;i<grid.getChildCount();i++){
                    View child=grid.getChildAt(i);CharSequence description=child.getContentDescription();
                    if(child instanceof TextView&&description!=null&&description.toString().contains(" 上课，")){
                        TextView time=(TextView)child;check(time.getLayout()!=null&&time.getLayout().getHeight()<=time.getHeight(),"period time fits short/large-font viewport: "+time.getText()+" in "+time.getHeight()+" px, text "+(time.getLayout()==null?0:time.getLayout().getHeight())+" px");
                        check(description.toString().contains(" 下课"),"full interval remains accessible");
                    }
                }
            });
            onMain(()->{
                android.widget.FrameLayout original=activity.root;
                feedback=new FeedbackRoot();feedback.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());
                activity.root=feedback;activity.setContentView(feedback);activity.render();
            });settle();
            checkBars(activity);
            screenshot("default-timetable");
            nav("首页",0);homeTileSizes();pressFeedback();nav("课程",1);nav("个人",2);nav("首页",0);
            openActivity("成绩查看",LoginActivity.class);assertPage(3);click("返回首页");settle();assertPage(0);
            openActivity("考试查询",LoginActivity.class);assertPage(4);click("返回首页");settle();assertPage(0);
            click("自定义课程");settle();assertSheet();dismissSheet();
            openActivity("校历",AcademicCalendarActivity.class);
            openActivity("校园地图",CampusMapActivity.class);
            openActivity("图书查询",LibraryActivity.class);
            openActivity("大物实验报告",PhysicsLabActivity.class);
            openActivity("网上报修",IdentityActivity.class);
            openActivity("电费",IdentityActivity.class);
            nav("个人",2);openActivity("关于",AboutActivity.class);assertPage(2);
            Activity crop=startActivitySync(new Intent(getTargetContext(),WallpaperCropActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();checkBars(crop);screenshot("WallpaperCropActivity");onMain(crop::finish);settle();
            nav("课程",1);click("更多选项");settle();
            screenshot("overflow-menu");onMain(()->{check(activity.moreMenu!=null&&activity.moreMenu.isShowing(),"overflow menu");activity.moreMenu.dismiss();});
            onMain(()->new CalibrationSheet(activity));settle();assertSheet();dismissSheet();
            final ThemeColorSheet[] colorSheet=new ThemeColorSheet[1];
            final int originalColor=activity.PRIMARY;
            onMain(()->colorSheet[0]=new ThemeColorSheet(activity));settle();assertSheet();screenshot("theme-palette");
            onMain(()->{
                check(find(activity.activeSheet.getWindow().getDecorView(),"预设配色")==null,"preset section removed");
                colorSheet[0].hex.setText("#A68CDB");
                check(colorSheet[0].color==0xffa68cdb&&!colorSheet[0].follow.isChecked(),"custom color input updates draft");
                check(activity.PRIMARY==originalColor,"draft does not change saved appearance");
            });
            settle();screenshot("theme-custom-color");dismissSheet();
            onMain(()->check(activity.PRIMARY==originalColor,"cancel preserves appearance"));
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
                check(activity.pageContent.getScaleX()==1&&activity.pageContent.getScaleY()==1,"incoming page scale restored");
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
    private void pressFeedback(){
        final View[] tile=new View[1];
        final android.widget.TextView[] button=new android.widget.TextView[1];
        onMain(()->{tile[0]=(View)find(activity.screen,"考试查询").getParent();tile[0].setPressed(true);button[0]=activity.themedButton("测试按钮",()->{},true);activity.root.addView(button[0],new android.widget.FrameLayout.LayoutParams(160,80));button[0].setPressed(true);});
        SystemClock.sleep(180);waitForIdleSync();
        onMain(()->{
            check(Ui.animated()?tile[0].getScaleX()<1&&tile[0].getScaleX()>=.97f:tile[0].getScaleX()==1,"press response respects animation setting");
            check(Ui.animated()?button[0].getScaleX()<1&&button[0].getScaleX()>=.97f:button[0].getScaleX()==1,"text button contact respects animation setting");
            tile[0].setPressed(false);button[0].setPressed(false);
        });settle();
        onMain(()->check(Math.abs(tile[0].getScaleX()-1)<.0001&&Math.abs(tile[0].getScaleY()-1)<.0001,"release or cancelled press restores size"));
        onMain(()->{check(Math.abs(button[0].getScaleX()-1)<.0001,"text button release restores size");activity.root.removeView(button[0]);});
    }
    private void homeTileSizes(){
        onMain(()->{
            View title=find(activity.screen,"考试查询");ViewGroup tile=(ViewGroup)title.getParent();ViewGroup grid=(ViewGroup)tile.getParent();
            check(grid.getChildCount()==MainActivity.HOME_TITLES.length,"all home entries present");
            for(int i=0;i<grid.getChildCount();i++){
                View card=grid.getChildAt(i);check(card.getWidth()==tile.getWidth()&&card.getHeight()==tile.getHeight(),"home entry sizes match, including final library entry");
                check(card.getHeight()>=activity.dp(48),"home entry touch target");
            }
        });
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
                    assertTabMotion(source,target);
                });settle();
                onMain(()->{
                    check(activity.pageAnimator==null&&activity.outgoing==null,"tab motion removes outgoing page");
                    check(activity.pageContent.getAlpha()==1&&activity.pageContent.getTranslationX()==0,"tab motion settles visibly");
                    check(activity.pageContent.getScaleX()==1&&activity.pageContent.getScaleY()==1,"tab motion restores scale");
                    check(((Ui.Page)activity.pageContent).progress==1,"completed page has no remaining reveal clip");
                });
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
    private void assertTabMotion(int from,int to){
        if(from==to||!Ui.animated()){
            check(activity.pageAnimator==null&&activity.outgoing==null,"same tab or disabled animations switch immediately");return;
        }
        check(activity.pageAnimator!=null&&activity.pageAnimator.isRunning(),"animation starts for tab "+from+" -> "+to);
        android.animation.ValueAnimator motion=activity.pageAnimator;
        int direction=to>from?1:-1;
        motion.setCurrentPlayTime(motion.getDuration()/6);
        check(activity.outgoingContent.getAlpha()==1,"previous content stays opaque underneath reveal");
        check(activity.outgoingContent.getTranslationX()*direction>0,"previous content has shallow directional parallax");
        check(activity.pageContent.getAlpha()==1,"incoming text stays opaque");
        Ui.Page incoming=(Ui.Page)activity.pageContent;
        float early=incoming.progress;
        check(early>0&&early<1&&incoming.direction==direction,"reveal follows navigation direction");
        motion.setCurrentPlayTime(motion.getDuration()/2);
        check(incoming.progress>early&&incoming.progress<1,"cut-in advances continuously");
        check(activity.pageContent.getScaleX()==1&&activity.pageContent.getScaleY()==1,"page text is never stretched");
        Ui.Dock nav=(Ui.Dock)activity.screen.getChildAt(activity.screen.getChildCount()-1);
        check(nav.position>Math.min(from,to)&&nav.position<Math.max(from,to),"selection pill travels between tabs");
        View icon=((ViewGroup)nav.getChildAt(to)).getChildAt(0);
        check(icon.getScaleX()>=.94f&&icon.getScaleX()<=1.02f,"tab response remains subtle");
        if(from==0&&to==1){
            // A direct test click has not traversed layout yet. Lay out the new tree
            // before capturing its in-flight paint rather than an empty child.
            int width=activity.root.getWidth(),height=activity.root.getHeight();
            activity.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            activity.root.layout(0,0,width,height);
            motion.setCurrentPlayTime(motion.getDuration()/5);
            android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(activity.root.getWidth(),activity.root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try{activity.root.draw(new android.graphics.Canvas(frame));saveScreenshot("page-motion-midpoint",frame);}finally{frame.recycle();}
        }
        // Rendering during a transition must clean up both pages, including the new scale.
        if(from==2&&to==0){activity.render();check(activity.pageContent.getScaleX()==1,"refresh cancels transition cleanly");}
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
        settle();checkBars(opened);screenshot(target.getSimpleName());
        if(opened instanceof LibraryActivity){
            LibraryActivity library=(LibraryActivity)opened;
            onMain(()->{
                library.emptyView.setVisibility(View.GONE);library.status.setText("以下为界面测试数据");
                LibraryApi.Item book=new LibraryApi.Item();book.title="大学物理实验：测量方法与实验设计（测试数据）";book.author="测试作者";book.publisher="测试出版社";book.callNo="O4-33/01";book.available=3;book.totalCopies=5;
                library.addCard(book,1);
            });settle();screenshot("library-populated");
        }
        if(opened instanceof IdentityActivity&&"电费".equals(label)){
            IdentityActivity identity=(IdentityActivity)opened;
            onMain(()->{
                identity.web.stopLoading();identity.handler.removeCallbacksAndMessages(null);
                ElectricityPanel panel=identity.electricityPanel;
                check(panel!=null,"native electricity panel created");panel.invalidate();panel.show();
                panel.hint.setText("以下为界面测试数据");panel.buildingButton.setText("北区18舍");panel.floorButton.setText("4 层");panel.roomButton.setText("测试房间");
                panel.readings[0]=new ElectricityApi.Reading(true,false,false,126.38,"");panel.readings[1]=new ElectricityApi.Reading(true,false,false,18.60,"");panel.renderCards();
                // Draw the real native panel synchronously: a pending school login
                // callback must not replace this fixture with the login screen.
                int w=identity.body.getWidth(),h=identity.body.getHeight();
                panel.view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));panel.view.layout(0,0,w,h);
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);
                try{panel.view.draw(new android.graphics.Canvas(bitmap));saveScreenshot("electricity-populated",bitmap);}finally{bitmap.recycle();}
            });
        }
        onMain(opened::finish);settle();removeMonitor(monitor);
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
        try{saveScreenshot(name,bitmap);}finally{bitmap.recycle();}
    }
    private void saveScreenshot(String name,android.graphics.Bitmap bitmap){
        java.io.File base=getTargetContext().getExternalFilesDir(null);
        check(base!=null,"external screenshot directory available");
        java.io.File directory=new java.io.File(base,"ui-smoke");
        check(directory.isDirectory()||directory.mkdirs(),"create screenshot directory");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"save screenshot");
        }catch(java.io.IOException e){throw new AssertionError(e);}
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
