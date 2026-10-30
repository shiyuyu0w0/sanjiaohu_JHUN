package cn.jhun.sanjiaohu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Shared motion and touch feedback. */
public final class Ui {
    private Ui(){}
    public static final long DURATION_FAST=120,DURATION_PAGE=420,DURATION_ENTER=420,DURATION_SLOW=420;
    public static int dp(Context c,float x){return (int)(x*c.getResources().getDisplayMetrics().density+0.5f);}
    /** Shared rounded surfaces, using each page's original palette roles. */
    public static android.graphics.drawable.GradientDrawable surface(Context c,int color,int edge,float radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(edge!=0)d.setStroke(Math.max(1,dp(c,.5f)),edge);
        return d;
    }
    public static android.graphics.drawable.GradientDrawable card(Context c,ThemePalette p){return surface(c,p.entrySurface,p.outline,18);}
    public static int actionInk(ThemePalette p,boolean filled){return filled?p.onPrimary:p.deepAccent;}
    public static android.graphics.drawable.RippleDrawable control(Context c,ThemePalette p,boolean filled){
        return control(c,p,filled,p.entrySurface);
    }
    public static android.graphics.drawable.RippleDrawable control(Context c,ThemePalette p,boolean filled,int secondary){
        int background=filled?p.primary:secondary;
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((actionInk(p,filled)&0xffffff)|0x22000000),
                surface(c,background,filled?0:p.outline,22),surface(c,0xffffffff,0,22));
    }
    public static android.widget.LinearLayout section(Context c,ThemePalette p,String title){
        android.widget.LinearLayout row=new android.widget.LinearLayout(c);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.TextView text=new android.widget.TextView(c);text.setText(title);text.setTextSize(14);text.setTextColor(p.muted);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));row.addView(text);
        View rule=new View(c);rule.setBackgroundColor(p.outline);
        android.widget.LinearLayout.LayoutParams line=new android.widget.LinearLayout.LayoutParams(0,Math.max(1,dp(c,.5f)),1);line.leftMargin=dp(c,12);row.addView(rule,line);
        row.setPadding(0,dp(c,4),0,dp(c,12));return row;
    }
    /** 页面切入的缓动：快速响应，然后平缓收尾。 */
    public static android.view.animation.Interpolator easing(){
        return new android.view.animation.PathInterpolator(.18f,.72f,.18f,1f);
    }
    /** 动画是否可用。系统设置里关掉动画后必须老实不动。 */
    public static boolean animated(){return android.animation.ValueAnimator.areAnimatorsEnabled();}

    /** A small contact/release response; state animations leave click and scroll handling native. */
    public static void press(View view){
        if(!animated())return;
        android.animation.StateListAnimator states=new android.animation.StateListAnimator();
        for(boolean down:new boolean[]{true,false}){
            android.animation.AnimatorSet motion=new android.animation.AnimatorSet();
            float size=down?.975f:1f;
            motion.playTogether(android.animation.ObjectAnimator.ofFloat(view,"scaleX",size),android.animation.ObjectAnimator.ofFloat(view,"scaleY",size));
            motion.setDuration(down?DURATION_FAST:DURATION_ENTER);
            motion.setInterpolator(down?easing():new android.view.animation.PathInterpolator(.2f,1.08f,.34f,1f));
            states.addState(down?new int[]{android.R.attr.state_pressed,android.R.attr.state_enabled}:new int[0],motion);
        }
        view.setStateListAnimator(states);
    }

    /** Retained page content revealed on a shallow diagonal, inspired by Wenku8's archive motion.
     * Only drawing changes during the transition; layout, text scale and colors stay stable. */
    static final class Page extends android.widget.LinearLayout {
        private final android.graphics.Path clip=new android.graphics.Path();
        private final int paper;
        float progress=1;
        int direction=1;
        Page(Context context,int color){super(context);paper=color;setOrientation(VERTICAL);setWillNotDraw(false);}
        void reveal(float value,int sign){progress=Math.max(0,Math.min(1,value));direction=sign;invalidate();}
        private float x(float value){return direction>0?value:getWidth()-value;}
        @Override public void draw(android.graphics.Canvas canvas){
            if(progress<=0)return;
            int saved=canvas.save();
            if(progress<1){
                float slant=Math.min(dp(getContext(),180),getWidth()*.46f),edge=(getWidth()+slant)*progress;
                clip.reset();clip.moveTo(x(0),0);clip.lineTo(x(edge),0);clip.lineTo(x(edge-slant),getHeight());clip.lineTo(x(0),getHeight());clip.close();
                canvas.clipPath(clip);
            }
            // Cover the previous content under the reveal without changing its opacity.
            canvas.drawColor(paper);
            canvas.translate(-dp(getContext(),24)*direction*(1-progress),dp(getContext(),12)*(1-progress));
            super.draw(canvas);canvas.restoreToCount(saved);
        }
    }

    /** One moving selection pill, instead of fading two unrelated tab backgrounds. */
    static final class Dock extends android.widget.LinearLayout {
        private final android.graphics.Paint ink=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float position;
        Dock(Context c,int color,int selected){super(c);ink.setColor(color);position=selected;setWillNotDraw(false);setGravity(android.view.Gravity.CENTER_VERTICAL);}
        void select(float value){position=value;invalidate();}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float width=(getWidth()-getPaddingLeft()-getPaddingRight())/3f;
            float left=getPaddingLeft()+position*width,top=getPaddingTop(),bottom=getHeight()-getPaddingBottom();
            canvas.drawRoundRect(left,top,left+width,bottom,dp(getContext(),26),dp(getContext(),26),ink);
        }
    }

    /** Use the system's touch feedback, respecting its setting without vibration permissions. */
    public static void selection(View view){
        haptic(view,android.os.Build.VERSION.SDK_INT>=34?android.view.HapticFeedbackConstants.SEGMENT_TICK:android.view.HapticFeedbackConstants.CLOCK_TICK);
    }
    public static void tap(View view){haptic(view,android.view.HapticFeedbackConstants.CONTEXT_CLICK);}
    private static void haptic(View view,int effect){
        if(view!=null&&view.isEnabled()&&view.isHapticFeedbackEnabled())view.performHapticFeedback(effect);
    }

    /** Ignore layout spacers when identifying real content. */
    static boolean isSpacer(int width,int height,int pixel){
        // MATCH_PARENT (-1) / WRAP_CONTENT (-2) are sizing modes, not tiny views.
        return width>=0&&height>=0&&(width<=pixel||height<=pixel);
    }
    public static void stagger(ViewGroup parent,int from){
        // Page transitions already own a reveal clock; do not animate their children twice.
        if(parent.getContext() instanceof MainActivity&&((MainActivity)parent.getContext()).transitionFrom>=0)return;
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
        v.setScaleX(.94f);v.setScaleY(.94f);
        v.animate().scaleX(1).scaleY(1).setStartDelay(0)
         .setDuration(DURATION_SLOW).setInterpolator(new android.view.animation.PathInterpolator(.2f,1.08f,.34f,1f)).start();
    }
}
