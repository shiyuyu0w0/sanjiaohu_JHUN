package cn.jhun.sanjiaohu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Shared motion and touch feedback. */
public final class Ui {
    private Ui(){}
    public static final long DURATION_FAST=120,DURATION_PAGE=320,DURATION_ENTER=280,DURATION_SLOW=360;
    public static int dp(Context c,float x){return (int)(x*c.getResources().getDisplayMetrics().density+0.5f);}
    /** 标准缓动：起步快、收尾稳，和周切换手势用的是同一条曲线。 */
    public static android.view.animation.Interpolator easing(){
        return new android.view.animation.PathInterpolator(.22f,1f,.36f,1f);
    }
    /** 动画是否可用。系统设置里关掉动画后必须老实不动。 */
    public static boolean animated(){return android.animation.ValueAnimator.areAnimatorsEnabled();}

    /** Use the system's touch feedback, respecting its setting without vibration permissions. */
    public static void selection(View view){
        haptic(view,android.os.Build.VERSION.SDK_INT>=34?android.view.HapticFeedbackConstants.SEGMENT_TICK:android.view.HapticFeedbackConstants.CLOCK_TICK);
    }
    public static void tap(View view){haptic(view,android.view.HapticFeedbackConstants.CONTEXT_CLICK);}
    private static void haptic(View view,int effect){
        if(view!=null&&view.isEnabled()&&view.isHapticFeedbackEnabled())view.performHapticFeedback(effect);
    }

    /** 让一组子视图依次淡入上移。父容器已经布好局才调用。 */
    static boolean isSpacer(int width,int height,int pixel){
        // MATCH_PARENT (-1) / WRAP_CONTENT (-2) are sizing modes, not tiny views.
        return width>=0&&height>=0&&(width<=pixel||height<=pixel);
    }
    public static void stagger(ViewGroup parent,int from){
        // One reveal for the content group; repeated cards do not cascade.
        enter(parent,0,dp(parent.getContext(),4));
    }
    /** 单个视图的入场：淡入 + 轻微上移。 */
    public static void enter(View v,long delay,float rise){
        v.animate().cancel();
        if(!animated()){v.setAlpha(1);v.setTranslationY(0);return;}
        v.setAlpha(0);v.setTranslationY(rise);
        v.animate().alpha(1).translationY(0).setStartDelay(delay)
         .setDuration(DURATION_ENTER).setInterpolator(easing()).start();
    }

    /** 点一下的回弹：先缩一点再弹回原位，给底栏图标用。 */
    public static void pop(View v){
        v.animate().cancel();
        if(!animated()){v.setScaleX(1);v.setScaleY(1);return;}
        v.setScaleX(.82f);v.setScaleY(.82f);
        v.animate().scaleX(1).scaleY(1).setStartDelay(0)
         .setDuration(DURATION_SLOW).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
    }
}
