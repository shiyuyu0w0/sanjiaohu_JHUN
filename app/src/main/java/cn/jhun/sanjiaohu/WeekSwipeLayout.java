package cn.jhun.sanjiaohu;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/** Intercept only a recognized horizontal gesture, cancelling the course's click. */
final class WeekSwipeLayout extends FrameLayout {
    interface Listener {void drag(float offset);void release(int direction);}
    final WeekSwipeGesture gesture;final Listener listener;VelocityTracker velocity;boolean transitioning;
    WeekSwipeLayout(Context context,Listener listener){
        super(context);this.listener=listener;gesture=new WeekSwipeGesture(getResources().getDisplayMetrics().density,ViewConfiguration.get(context).getScaledTouchSlop());setClipChildren(true);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){release();velocity=VelocityTracker.obtain();gesture.begin(e.getX(),e.getY(),getWidth());}
        if(velocity!=null)velocity.addMovement(e);
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){gesture.cancel();listener.release(0);}
        boolean handled=super.dispatchTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){gesture.cancel();release();}
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return transitioning||e.getActionMasked()==MotionEvent.ACTION_MOVE&&gesture.move(e.getX(),e.getY());}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(transitioning)return true;
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&gesture.move(e.getX(),e.getY()))listener.drag(e.getX()-gesture.startX);
        if(e.getActionMasked()==MotionEvent.ACTION_UP){float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getXVelocity();}int direction=gesture.finish(e.getX(),e.getY(),speed);listener.release(direction);}
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){gesture.cancel();listener.release(0);}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    void release(){if(velocity!=null){velocity.recycle();velocity=null;}}
    @Override protected void onDetachedFromWindow(){release();gesture.cancel();super.onDetachedFromWindow();}
}
