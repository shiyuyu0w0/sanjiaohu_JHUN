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
    public static PopupWindow show(Activity activity,View anchor,ThemePalette theme,Action action){
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
        TextView close=text(activity,"×",24,theme.muted,false);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭更多菜单");close.setFocusable(true);close.setBackground(ripple(activity,theme.primary,theme.rippleMask));close.setOnClickListener(v->popup.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,44)));content.addView(heading);
        section(activity,content,"课表",theme);
        row(activity,content,"更新课表",1,theme,popup,action);
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
        Icon icon=new Icon(a,id,theme.text);icon.setBackground(shape(a,theme.controlSurface,10));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(dp(a,32),dp(a,32)));
        TextView label=text(a,title,14,theme.text,false);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(a,12);row.addView(label,lp);
        row.setOnClickListener(v->{popup.dismiss();action.run(id);});parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    static TextView text(Activity a,String s,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));return t;}
    static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static RippleDrawable ripple(Activity a,int primary,int mask){return new RippleDrawable(ColorStateList.valueOf((primary&0xffffff)|0x22000000),shape(a,Color.TRANSPARENT,12),shape(a,mask,12));}
    static int dp(Activity a,float value){return (int)(a.getResources().getDisplayMetrics().density*value+.5f);}
    static final class Icon extends View {
        final int id;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        void setColor(int color){pen.setColor(color);invalidate();}
        Icon(Activity a,int id,int color){super(a);this.id=id;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.6f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()*.2f,getHeight()*.2f);c.scale(getWidth()*.6f/24,getHeight()*.6f/24);
            if(id==1||id==6){c.drawArc(new RectF(4,4,20,20),id==1?35:45,280,false,pen);Path p=new Path();p.moveTo(4,3);p.lineTo(4,8);p.lineTo(9,8);c.drawPath(p,pen);if(id==6){c.drawLine(12,8,12,13,pen);c.drawLine(12,13,15,14,pen);}}
            else if(id==2){Path p=new Path();p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(6,10);p.lineTo(6,21);p.lineTo(18,21);p.lineTo(18,10);p.moveTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);c.drawPath(p,pen);}
            else if(id==16){c.drawCircle(12,12,9,pen);c.drawPoint(12,7,pen);c.drawLine(12,11,12,17,pen);}
            else if(id==17){Path p=new Path();p.moveTo(14,2);p.lineTo(5,14);p.lineTo(11,14);p.lineTo(10,22);p.lineTo(20,10);p.lineTo(13,10);p.close();c.drawPath(p,pen);}
            else if(id==18){Path p=new Path();p.moveTo(9,3);p.lineTo(15,3);p.moveTo(10,3);p.lineTo(10,9);p.lineTo(5,19);p.lineTo(19,19);p.lineTo(14,9);p.lineTo(14,3);c.drawPath(p,pen);c.drawLine(7,15,17,15,pen);}
            else if(id==15){Path p=new Path();p.moveTo(14,3);p.cubicTo(9,1,6,6,9,10);p.lineTo(3,17);p.lineTo(7,21);p.lineTo(14,14);p.cubicTo(19,17,23,11,20,7);p.lineTo(17,10);p.lineTo(13,6);p.close();c.drawPath(p,pen);}
            else if(id==13){Path p=new Path();p.moveTo(3,6);p.lineTo(9,3);p.lineTo(15,6);p.lineTo(21,3);p.lineTo(21,18);p.lineTo(15,21);p.lineTo(9,18);p.lineTo(3,21);p.close();p.moveTo(9,3);p.lineTo(9,18);p.moveTo(15,6);p.lineTo(15,21);c.drawPath(p,pen);}
            else if(id==14){c.drawRoundRect(new RectF(3,5,21,21),2,2,pen);c.drawLine(3,10,21,10,pen);c.drawLine(8,3,8,7,pen);c.drawLine(16,3,16,7,pen);for(int x=7;x<=17;x+=5)for(int y=14;y<=18;y+=4)c.drawPoint(x,y,pen);}
            else if(id==19){c.drawRoundRect(new RectF(4,4,20,22),2,2,pen);c.drawRoundRect(new RectF(8,2,16,6),1,1,pen);c.drawLine(8,10,16,10,pen);c.drawLine(8,14,12,14,pen);Path p=new Path();p.moveTo(12,18);p.lineTo(14,20);p.lineTo(18,16);c.drawPath(p,pen);}
            else if(id==11){c.drawRoundRect(new RectF(4,3,20,21),2,2,pen);c.drawLine(8,16,8,12,pen);c.drawLine(12,16,12,7,pen);c.drawLine(16,16,16,10,pen);}
            else if(id==3||id==12){c.drawRoundRect(new RectF(4,5,20,21),2,2,pen);c.drawLine(4,10,20,10,pen);c.drawLine(8,3,8,7,pen);c.drawLine(16,3,16,7,pen);Path p=new Path();p.moveTo(8,15);p.lineTo(11,18);p.lineTo(16,13);c.drawPath(p,pen);}
            else if(id==10){Path p=new Path();p.moveTo(10,3);p.lineTo(4,3);p.lineTo(4,21);p.lineTo(10,21);p.moveTo(9,12);p.lineTo(21,12);p.moveTo(17,8);p.lineTo(21,12);p.lineTo(17,16);c.drawPath(p,pen);}
            else if(id==9){c.drawCircle(10,10,7,pen);c.drawCircle(16,16,5,pen);c.drawLine(8,7,8,13,pen);c.drawLine(5,10,11,10,pen);}
            else if(id==8){c.drawRoundRect(new RectF(3,3,21,21),3,3,pen);c.drawLine(12,7,12,17,pen);c.drawLine(7,12,17,12,pen);}
            else if(id==7){c.drawRoundRect(new RectF(3,6,16,20),3,3,pen);c.drawRoundRect(new RectF(8,3,21,17),3,3,pen);c.drawLine(10,8,15,13,pen);c.drawLine(10,12,12,14,pen);}
            else if(id==5){c.drawRoundRect(new RectF(3,4,21,20),2,2,pen);c.drawCircle(8,9,1.4f,pen);Path p=new Path();p.moveTo(4,18);p.lineTo(10,12);p.lineTo(14,16);p.lineTo(17,13);p.lineTo(20,16);c.drawPath(p,pen);}
            else {c.drawCircle(12,8,4,pen);c.drawArc(new RectF(4,14,20,28),180,180,false,pen);}
            c.restore();
        }
    }
}
