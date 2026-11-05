package cn.jhun.sanjiaohu;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import android.widget.OverScroller;
import java.util.Locale;

/** Bounded minute/hour wheel with fling, row snapping, keyboard and TalkBack adjustment. */
final class TimeWheelView extends View {
    final int count;final String unit;final ThemePalette palette;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL),bold=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    final OverScroller scroller;final int slop,minFling,maxFling;
    float position,row,downY,lastY;boolean dragging,needsSnap;
    int selected,lower,upper;long lastFeedback;Runnable onSelection;
    VelocityTracker velocity;
    TimeWheelView(MainActivity a,int count,String unit,int value){
        super(a);this.count=count;this.unit=unit;palette=a.palette;position=selected=value;upper=count-1;
        scroller=new OverScroller(a);ViewConfiguration config=ViewConfiguration.get(a);slop=config.getScaledTouchSlop();minFling=config.getScaledMinimumFlingVelocity();maxFling=config.getScaledMaximumFlingVelocity();
        setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);describe();
    }
    int value(){return Math.max(lower,Math.min(upper,Math.round(position)));}
    void setValue(int value){stop();move(value,false);}
    void setRange(int lower,int upper){this.lower=Math.max(0,lower);this.upper=Math.min(count-1,upper);stop();move(position,false);}
    void describe(){setContentDescription(unit+"，"+String.format(Locale.ROOT,"%02d",selected));}
    void move(float value,boolean feedback){
        position=Math.max(lower,Math.min(upper,value));int next=value();
        if(next!=selected){selected=next;describe();if(feedback&&SystemClock.uptimeMillis()-lastFeedback>=45){Ui.selection(this);lastFeedback=SystemClock.uptimeMillis();}if(onSelection!=null)onSelection.run();}
        invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){stop();row=h/5f;position=selected;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(row<=0)return;float center=getHeight()/2f;
        int first=Math.max(lower,(int)Math.floor(position)-3),last=Math.min(upper,(int)Math.ceil(position)+3);
        float scaled=getResources().getDisplayMetrics().scaledDensity;
        for(int value=first;value<=last;value++){
            float distance=Math.abs(value-position),weight=Math.max(0,1-distance);
            paint.setTypeface(value==selected?bold:regular);paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(value==selected?ThemePalette.readable(palette.deepAccent,palette.selectedSurface,4.5):palette.muted);
            paint.setAlpha((int)(255*Math.max(.12,1-distance*.32)));
            paint.setTextSize(Math.min((24+6*weight)*scaled,row*.64f));
            Paint.FontMetrics font=paint.getFontMetrics();float y=center+(value-position)*row-(font.ascent+font.descent)/2;
            canvas.drawText(String.format(Locale.ROOT,"%02d",value),getWidth()/2f,y,paint);
        }
        if(isFocused()){paint.setAlpha(255);paint.setColor(palette.deepAccent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(getResources().getDisplayMetrics().density);canvas.drawRoundRect(2,center-row/2+2,getWidth()-2,center+row/2-2,12,12,paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled()||row<=0)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){stop();getParent().requestDisallowInterceptTouchEvent(true);downY=lastY=event.getY();dragging=false;velocity=VelocityTracker.obtain();velocity.addMovement(event);return true;}
        if(velocity!=null)velocity.addMovement(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                if(!dragging&&Math.abs(event.getY()-downY)>slop)dragging=true;
                if(dragging)move(position+(lastY-event.getY())/row,true);lastY=event.getY();return true;
            case MotionEvent.ACTION_UP:
                if(dragging){float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000,maxFling);speed=-velocity.getYVelocity();}
                    if(Math.abs(speed)>=minFling){scroller.fling(0,Math.round(position*row),0,Math.round(speed),0,0,Math.round(lower*row),Math.round(upper*row));needsSnap=true;postInvalidateOnAnimation();}else snap();
                }else{move(position+Math.round((event.getY()-getHeight()/2f)/row),true);snap();performClick();}
                release();getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:release();snap();getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    void snap(){
        int target=value();needsSnap=false;
        if(row<=0||!ValueAnimator.areAnimatorsEnabled()||Math.abs(position-target)<.005f){move(target,false);return;}
        scroller.startScroll(0,Math.round(position*row),0,Math.round(target*row)-Math.round(position*row),160);postInvalidateOnAnimation();
    }
    @Override public void computeScroll(){
        if(scroller.computeScrollOffset()&&row>0){move(scroller.getCurrY()/row,true);postInvalidateOnAnimation();}
        else if(needsSnap)snap();else if(!dragging&&velocity==null&&position!=value())move(value(),false);
    }
    int finishSelection(){int result=value();stop();move(result,false);return result;}
    void release(){if(velocity!=null){velocity.recycle();velocity=null;}dragging=false;}
    void stop(){scroller.forceFinished(true);needsSnap=false;release();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    boolean adjust(int delta){int next=Math.max(lower,Math.min(upper,value()+delta));if(next==value())return false;stop();move(next,true);sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);return true;}
    @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_UP)return adjust(-1);if(code==KeyEvent.KEYCODE_DPAD_DOWN)return adjust(1);return super.onKeyDown(code,event);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setScrollable(true);info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,lower,upper,value()));
        if(value()<upper)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);if(value()>lower)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)return adjust(1);if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)return adjust(-1);
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){float progress=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);if(Float.isFinite(progress)){setValue(Math.round(progress));sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);return true;}}
        return super.performAccessibilityAction(action,args);
    }
}
