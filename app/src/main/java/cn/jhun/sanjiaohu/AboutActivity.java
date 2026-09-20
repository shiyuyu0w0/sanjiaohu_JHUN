package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;

/** Offline about page; the sponsor image is bundled byte-for-byte as supplied. */
public final class AboutActivity extends Activity {
    /** What users can see and use in this release; plain wording, no version-by-version history. */
    static final String[][] UPDATES={
        {"v1.1.2","- 考试查询：首页第一个入口，显示考试时间、地点、座位和倒计时，考试当天标「今天」，考完显示「已考完」。\n- 电费：选宿舍楼、楼层、房间后查看空调与照明电量，可直接缴费用支付宝付款，也支持记住上次选的房间。\n- 电费：留学生公寓和研究生公寓的空调与照明合并成一个选项，两块电表都能查询和缴费。\n- 校园地图：可以定位当前位置，也能查看宿舍、教学楼和食堂。\n- 课表：修复了部分同学课表获取不完整的问题。\n- 大物实验报告：在校园网内打开实验网站，能选择文件提交报告。\n- 外观：跟随系统深色模式。\n- 本页：新增「更新内容」和「技术支持」。"},
    };
    /** Technical-support names shown under the「技术支持」heading. */
    static final String[] SUPPORT={"广","yy792e"};
    ThemePalette theme;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        AppTheme.applySystemBars(this,theme);
        LinearLayout root=column();root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView back=text("‹",28,theme.deepAccent,true);back.setGravity(Gravity.CENTER);back.setContentDescription("返回个人");back.setFocusable(true);back.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),shape(theme.entrySurface,15),shape(theme.rippleMask,15)));back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text("关于",30,theme.text,true);title.setPadding(dp(16),0,0,0);header.addView(title);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);FrameLayout center=new FrameLayout(this);scroll.addView(center);
        LinearLayout content=column();content.setPadding(0,dp(12),0,dp(28));int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(520));center.addView(content,new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sponsor=card();TextView sponsorTitle=text("赞助",22,theme.deepAccent,true);sponsorTitle.setGravity(Gravity.CENTER);sponsor.addView(sponsorTitle,new LinearLayout.LayoutParams(-1,-2));gap(sponsor,18);
        FrameLayout plate=new FrameLayout(this);plate.setPadding(dp(8),dp(8),dp(8),dp(8));plate.setBackground(shape(Color.WHITE,18));
        ImageView code=new ImageView(this);code.setAdjustViewBounds(true);code.setScaleType(ImageView.ScaleType.FIT_CENTER);code.setContentDescription("赞助码");
        try(InputStream in=getAssets().open("sponsor.png")){Bitmap bitmap=BitmapFactory.decodeStream(in);if(bitmap==null)throw new java.io.IOException();code.setImageBitmap(bitmap);}catch(java.io.IOException e){TextView error=text("赞助码暂时无法显示",14,theme.text,false);plate.addView(error);}
        plate.addView(code,new FrameLayout.LayoutParams(-1,-2));sponsor.addView(plate,new LinearLayout.LayoutParams(-1,-2));gap(sponsor,16);
        TextView caption=text("觉得好用就打赏一杯咖啡吧",13,ThemePalette.readable(theme.muted,theme.entrySurface,4.5),false);caption.setGravity(Gravity.CENTER);caption.setLineSpacing(dp(3),1);sponsor.addView(caption,new LinearLayout.LayoutParams(-1,-2));content.addView(sponsor);gap(content,16);
        String current="1.0.1";try{current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        LinearLayout version=card();version.setOrientation(LinearLayout.HORIZONTAL);version.setGravity(Gravity.CENTER_VERTICAL);version.addView(text("版本号",15,theme.text,false),new LinearLayout.LayoutParams(0,-2,1));
        version.addView(text(current.startsWith("v")?current:"v"+current,18,theme.deepAccent,true));content.addView(version);gap(content,16);
        LinearLayout updates=card();TextView updatesTitle=text("更新内容",22,theme.deepAccent,true);updatesTitle.setGravity(Gravity.CENTER);updates.addView(updatesTitle,new LinearLayout.LayoutParams(-1,-2));
        for(String[] entry:UPDATES){updates.addView(text(entry[0],15,theme.deepAccent,true),new LinearLayout.LayoutParams(-1,-2));TextView body=text(entry[1],13,ThemePalette.readable(theme.text,theme.entrySurface,7),false);body.setLineSpacing(dp(5),1);body.setPadding(0,dp(10),0,0);updates.addView(body,new LinearLayout.LayoutParams(-1,-2));}
        content.addView(updates);gap(content,16);
        LinearLayout support=card();support.setBackground(shape(theme.entrySurface,24));
        TextView supportTitle=text("技术支持",22,theme.deepAccent,true);supportTitle.setGravity(Gravity.CENTER);support.addView(supportTitle,new LinearLayout.LayoutParams(-1,-2));
        TextView supportNote=text("感谢以下同学在开发与测试中的帮助",12,ThemePalette.readable(theme.muted,theme.entrySurface,4.5),false);supportNote.setGravity(Gravity.CENTER);supportNote.setLineSpacing(dp(3),1);gap(support,10);support.addView(supportNote,new LinearLayout.LayoutParams(-1,-2));
        for(String name:SUPPORT){LinearLayout row=column();row.setGravity(Gravity.CENTER);gap(support,10);TextView person=text(name,14,theme.deepAccent,true);person.setGravity(Gravity.CENTER);person.setPadding(dp(18),dp(9),dp(18),dp(9));person.setBackground(shape(theme.controlSurface,14));row.addView(person,new LinearLayout.LayoutParams(-2,-2));support.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        content.addView(support);gap(content,16);
        setContentView(root);
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(shape(theme.entrySurface,24));return v;}
    void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
