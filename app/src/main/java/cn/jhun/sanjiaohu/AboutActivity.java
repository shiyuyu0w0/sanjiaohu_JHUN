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
        LinearLayout version=card();version.setOrientation(LinearLayout.HORIZONTAL);version.setGravity(Gravity.CENTER_VERTICAL);version.addView(text("版本号",15,theme.text,false),new LinearLayout.LayoutParams(0,-2,1));
        String current="1.0.1";try{current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        version.addView(text(current.startsWith("v")?current:"v"+current,18,theme.deepAccent,true));content.addView(version);
        setContentView(root);
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(shape(theme.entrySurface,24));return v;}
    void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
