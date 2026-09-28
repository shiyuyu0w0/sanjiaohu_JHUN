package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Small themed panel anchored to the overflow button; actions stay in the host activity. */
public final class MoreMenu {
    public interface Action {void run(int id);}
    private MoreMenu(){}
    public static PopupWindow show(Activity activity,View anchor,ThemePalette theme,boolean hasPreviousSchedule,Action action){
        int padding=dp(activity,10),panelColor=theme.sheetSurface;
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min(dp(activity,288),Math.max(dp(activity,180),visible.width()-dp(activity,24)));
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(padding,dp(activity,8),padding,dp(activity,10));
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);
        GradientDrawable surface=shape(activity,panelColor,22);surface.setStroke(dp(activity,1),theme.outline);scroll.setBackground(surface);scroll.setClipToOutline(true);
        PopupWindow popup=new PopupWindow(scroll,width,ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(shape(activity,panelColor,22));popup.setElevation(dp(activity,14));popup.setOutsideTouchable(true);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);popup.setAnimationStyle(R.style.MoreMenuAnimation);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(activity,8),0,0,dp(activity,4));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(activity,"更多",18,theme.text,true));TextView caption=text(activity,"三角狐",11,theme.muted,false);caption.setPadding(0,dp(activity,2),0,0);words.addView(caption);heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Icon close=new Icon(activity,22,theme.muted);close.setContentDescription("关闭更多菜单");close.setFocusable(true);close.setBackground(ripple(activity,theme.primary,theme.rippleMask));close.setOnClickListener(v->popup.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,44)));content.addView(heading);
        section(activity,content,"课表",theme);
        row(activity,content,"更新课表",1,theme,popup,action);
        if(hasPreviousSchedule)row(activity,content,"恢复上一份课表",21,theme,popup,action);
        row(activity,content,"切换学期",12,theme,popup,action);
        row(activity,content,"回到本周",2,theme,popup,action);
        row(activity,content,"周次校准",3,theme,popup,action);
        separator(activity,content,theme);
        section(activity,content,"外观",theme);
        row(activity,content,"更换课表背景",5,theme,popup,action);
        row(activity,content,"主题调色",9,theme,popup,action);
        row(activity,content,"课程透明度",7,theme,popup,action);
        row(activity,content,"恢复默认背景",6,theme,popup,action);
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int[] pos=new int[2];anchor.getLocationOnScreen(pos);int below=visible.bottom-pos[1]-anchor.getHeight()-dp(activity,16);
        popup.setHeight(Math.min(content.getMeasuredHeight(),Math.max(dp(activity,160),below)));
        popup.setOnDismissListener(()->anchor.setSelected(false));anchor.setSelected(true);
        popup.showAsDropDown(anchor,-dp(activity,2),dp(activity,5),Gravity.RIGHT);
        return popup;
    }
    static void section(Activity a,LinearLayout parent,String title,ThemePalette theme){TextView label=text(a,title,11,theme.muted,false);label.setPadding(dp(a,9),dp(a,5),0,dp(a,3));parent.addView(label);}
    static void separator(Activity a,LinearLayout parent,ThemePalette theme){View line=new View(a);line.setBackgroundColor(theme.outline);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,1));lp.setMargins(dp(a,9),dp(a,5),dp(a,9),dp(a,4));parent.addView(line,lp);}
    static void row(Activity a,LinearLayout parent,String title,int id,ThemePalette theme,PopupWindow popup,Action action){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,8),dp(a,7),dp(a,10),dp(a,7));row.setMinimumHeight(dp(a,48));row.setBackground(ripple(a,theme.primary,theme.rippleMask));row.setFocusable(true);row.setClickable(true);row.setContentDescription(title);row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        Icon icon=new Icon(a,id,theme.deepAccent);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(dp(a,32),dp(a,32)));
        TextView label=text(a,title,14,theme.text,false);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(a,12);row.addView(label,lp);
        row.setOnClickListener(v->{popup.dismiss();action.run(id);});parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    static TextView text(Activity a,String s,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));return t;}
    static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static RippleDrawable ripple(Activity a,int primary,int mask){return new RippleDrawable(ColorStateList.valueOf((primary&0xffffff)|0x22000000),shape(a,Color.TRANSPARENT,12),shape(a,mask,12));}
    static int dp(Activity a,float value){return (int)(a.getResources().getDisplayMetrics().density*value+.5f);}
    static final class Icon extends AppIcons.Glyph {
        Icon(Activity a,int id,int color){super(a,AppIcons.resource(id),color,id==1?20:24);}
    }
}