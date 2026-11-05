package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;

/** Real pointer gestures and confirmation/cancel semantics for the custom time wheels. */
public final class TimeWheelInstrumentation extends Instrumentation {
    MainActivity a;TimeWheelSheet picker;int checks;Throwable failure;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=-1;
        try{
            a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
            onMain(()->{a.editEvent(null,a.today(),3);a.eventEditor.title.setText("小组讨论");a.eventEditor.notes.setText("保留草稿");open();});settle();
            onMain(()->{
                check(picker.isShowing()&&picker.hours.getHeight()>0,"wheel sheet has a visible layout");
                check(picker.hours.value()==10&&picker.minutes.value()==20,"initial hour and minute match editor");
                check(picker.hours.palette.dark==a.palette.dark,"wheel follows active theme");
                check(ThemePalette.contrast(ThemePalette.readable(a.palette.deepAccent,a.palette.selectedSurface,4.5),a.palette.selectedSurface)>=4.5,"selected number contrast");
                drag(picker.minutes,1,false);
            });settleWheel();
            onMain(()->{check(picker.minutes.value()==21&&picker.minutes.position==21,"one-row drag snaps to next minute");check(a.eventEditor.start==620,"drag is only a draft");drag(picker.hours,-1,false);});settleWheel();
            onMain(()->{check(picker.hours.value()==9,"hours scroll independently");find(picker.sheet.dialog.getWindow().getDecorView(),"取消").performClick();check(a.eventEditor.start==620&&a.activeSheet.isShowing(),"cancel leaves original time and editor open");check(a.eventEditor.notes.getText().toString().equals("保留草稿"),"cancel retains unsaved editor fields");open();});settle();
            onMain(()->{check(picker.hours.value()==10&&picker.minutes.value()==20,"reopening discards cancelled draft");drag(picker.minutes,1,true);});
            SystemClock.sleep(130);onMain(()->{int visible=picker.minutes.value();check(visible>20,"fling advances minutes");find(picker.sheet.dialog.getWindow().getDecorView(),"确定").performClick();check(a.eventEditor.start==600+visible,"confirm during fling uses visible value");check(picker.minutes.scroller.isFinished(),"confirm stops pending motion");open();});settle();
            onMain(()->{picker.hours.setValue(23);picker.minutes.setValue(59);drag(picker.minutes,1,true);});settleWheel();
            onMain(()->{check(picker.hours.value()==21&&picker.minutes.value()==39,"start stops at 21:39, leaving room for the end");picker.hours.setValue(0);drag(picker.hours,-1,true);});settleWheel();
            onMain(()->{
                check(picker.hours.value()==8&&picker.minutes.lower==20,"earliest hour restricts minutes to 20 or later");
                picker.minutes.setValue(0);check(picker.minutes.value()==20,"08:00 cannot be selected");
                check(picker.minutes.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null)&&picker.minutes.value()==21,"TalkBack increments within range");
                picker.minutes.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));check(picker.minutes.value()==22,"keyboard selects next valid minute");
                picker.hours.setValue(9);picker.minutes.setValue(0);check(picker.minutes.value()==0&&picker.minutes.upper==59,"middle hours restore all 60 minutes");
                picker.hours.setValue(8);check(picker.minutes.value()==20,"changing to 08 clamps current minute");
                picker.hours.setValue(10);picker.minutes.setValue(35);
            });screenshot("time-wheel-"+(a.palette.dark?"dark":"light"));
            onMain(()->{find(picker.sheet.dialog.getWindow().getDecorView(),"确定").performClick();check(a.eventEditor.start==635&&a.eventEditor.times[0].getText().toString().equals("10:35"),"confirm writes exact time into editor");
                find(a.eventEditor.sheet.dialog.getWindow().getDecorView(),"结束时间").performClick();picker=a.eventEditor.timePicker;picker.hours.setValue(23);picker.minutes.setValue(59);check(picker.hours.value()==21&&picker.minutes.value()==40,"end allows exactly 21:40");
                find(picker.sheet.dialog.getWindow().getDecorView(),"确定").performClick();check(a.eventEditor.end==1300,"latest end committed");open();});settle();
            onMain(()->a.eventEditor.sheet.dialog.dismiss());settle();onMain(()->check(!picker.isShowing(),"closing editor also closes wheel sheet"));
            result.putString("stream","PASS: "+checks+" wheel gesture, snap, draft, accessibility and theme checks\n");
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));code=0;}
        finally{if(a!=null)onMain(()->{if(picker!=null)picker.dismiss();if(a.activeSheet!=null)a.activeSheet.dismiss();a.finish();});}
        finish(code,result);
    }
    void open(){find(a.eventEditor.sheet.dialog.getWindow().getDecorView(),"开始时间").performClick();picker=a.eventEditor.timePicker;}
    void drag(TimeWheelView wheel,int rows,boolean fling){
        long start=SystemClock.uptimeMillis();float y=wheel.getHeight()/2f,x=wheel.getWidth()/2f;
        int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};
        long[] delays=fling?new long[]{0,12,25,30}:new long[]{0,180,360,600};
        float[] offsets=fling?new float[]{0,.4f,1,1.1f}:new float[]{0,.4f,1,1};
        for(int i=0;i<actions.length;i++){MotionEvent event=MotionEvent.obtain(start,start+delays[i],actions[i],x,y-rows*wheel.row*offsets[i],0);try{wheel.dispatchTouchEvent(event);}finally{event.recycle();}}
    }
    void settleWheel(){for(int i=0;i<50;i++){SystemClock.sleep(100);final boolean[] done={false};onMain(()->done[0]=picker.hours.scroller.isFinished()&&picker.minutes.scroller.isFinished()&&!picker.hours.needsSnap&&!picker.minutes.needsSnap&&picker.hours.position==picker.hours.value()&&picker.minutes.position==picker.minutes.value());if(done[0])return;}throw new AssertionError("wheel did not settle");}
    void settle(){waitForIdleSync();SystemClock.sleep(400);waitForIdleSync();}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    void onMain(Runnable action){failure=null;runOnMainSync(()->{try{action.run();}catch(Throwable error){failure=error;}});if(failure!=null)throw new AssertionError("UI failure",failure);}
    View find(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View child=find(((ViewGroup)view).getChildAt(i),text);if(child!=null)return child;}return null;}
    void screenshot(String name)throws Exception{settle();Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}finally{image.recycle();}}
}
